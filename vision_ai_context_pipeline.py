import argparse
import base64
import csv
import dataclasses
import datetime
import json
import os
import re
import time
from typing import Any, Iterable, Optional

import mysql.connector
import requests
from PIL import Image


@dataclasses.dataclass(frozen=True)
class Config:
    db_host: str
    db_port: int
    db_user: str
    db_password: str
    db_name: str
    openai_api_key: str
    openai_model: str
    openai_base_url: str
    template_version: int
    batch_size: int
    max_candidate_images: int
    request_timeout_s: int
    report_dir: str
    context_column: str


def now_ts() -> str:
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def extract_image_urls(intro_html: str) -> list[str]:
    urls = re.findall(r'(?:src|data-src)\s*=\s*"([^"]+)"', intro_html or "")
    cleaned: list[str] = []
    for u in urls:
        u = (u or "").strip().strip("`").strip()
        if u.startswith(("http://", "https://")):
            cleaned.append(u)
    seen = set()
    out: list[str] = []
    for u in cleaned:
        if u not in seen:
            out.append(u)
            seen.add(u)
    return out


def download_image(url: str, timeout_s: int) -> bytes:
    resp = requests.get(url, timeout=timeout_s)
    resp.raise_for_status()
    return resp.content


def to_jpeg_bytes(image_bytes: bytes) -> bytes:
    with Image.open(io_bytes(image_bytes)) as img:
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        out = io_bytes()
        img.save(out, format="JPEG", quality=92, optimize=True)
        return out.getvalue()


def resize_max_width_jpeg(image_bytes: bytes, max_width: int) -> bytes:
    with Image.open(io_bytes(image_bytes)) as img:
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        w, h = img.size
        if w > max_width:
            new_h = int(h * (max_width / w))
            img = img.resize((max_width, max(1, new_h)))
        out = io_bytes()
        img.save(out, format="JPEG", quality=82, optimize=True)
        return out.getvalue()


def get_image_size(image_bytes: bytes) -> tuple[int, int]:
    with Image.open(io_bytes(image_bytes)) as img:
        return img.size


def io_bytes(initial: Optional[bytes] = None):
    from io import BytesIO

    return BytesIO(initial) if initial is not None else BytesIO()


def image_bytes_to_data_url(image_bytes: bytes, mime: str = "image/jpeg") -> str:
    b64 = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{mime};base64,{b64}"


def openai_chat_json(
    *,
    api_key: str,
    base_url: str,
    model: str,
    messages: list[dict[str, Any]],
    timeout_s: int,
) -> dict[str, Any]:
    url = f"{base_url.rstrip('/')}/v1/chat/completions"
    payload = {
        "model": model,
        "messages": messages,
        "response_format": {"type": "json_object"},
        "temperature": 0,
    }
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    resp = requests.post(url, headers=headers, data=json.dumps(payload), timeout=timeout_s)
    resp.raise_for_status()
    data = resp.json()
    content = data["choices"][0]["message"]["content"]
    return json.loads(content)


def classify_param_candidate(
    *,
    cfg: Config,
    image_jpeg: bytes,
    template_keywords: list[str],
) -> dict[str, Any]:
    keyword_str = "、".join(template_keywords[:60])
    prompt = (
        "你是电商商品详情图审核器。判断这张图片是否主要包含“规格参数/参数表/配置表”。"
        "如果只是装饰图、营销海报、外观渲染图、场景图，请判定为否。"
        "结合图片中的文字密度、是否有表格结构、是否出现参数关键词。"
        f"模板可能出现的关键词（用于参考）：{keyword_str}\n"
        '只返回 JSON：{"is_param": true|false, "score": 0~1, "reason": "..." }'
    )
    data_url = image_bytes_to_data_url(image_jpeg)
    messages = [
        {"role": "system", "content": "严格输出 JSON，不要输出多余文本。"},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": data_url, "detail": "low"}},
            ],
        },
    ]
    return openai_chat_json(
        api_key=cfg.openai_api_key,
        base_url=cfg.openai_base_url,
        model=cfg.openai_model,
        messages=messages,
        timeout_s=cfg.request_timeout_s,
    )


def build_template_keywords(template_schema: dict[str, Any]) -> list[str]:
    props = template_schema.get("properties") or {}
    keywords: list[str] = []
    for k, v in props.items():
        keywords.append(str(k))
        if isinstance(v, dict):
            ai_label = v.get("ai_label")
            if isinstance(ai_label, str):
                keywords.extend([s.strip() for s in re.split(r"[\/\s]+", ai_label) if s.strip()])
            aliases = v.get("aliases")
            if isinstance(aliases, list):
                for a in aliases:
                    if isinstance(a, str) and a.strip():
                        keywords.append(a.strip())
    dedup: list[str] = []
    seen = set()
    for x in keywords:
        if x and x not in seen:
            dedup.append(x)
            seen.add(x)
    return dedup


def extract_fields_from_images(
    *,
    cfg: Config,
    template_schema: dict[str, Any],
    unit_hints: dict[str, Any],
    candidate_image_urls: list[str],
    candidate_image_jpegs: list[bytes],
) -> dict[str, Any]:
    props = template_schema.get("properties") or {}
    fields: list[dict[str, Any]] = []
    for key, info in props.items():
        if not isinstance(info, dict):
            continue
        field = {
            "key": key,
            "type": info.get("type") or "string",
            "ai_label": info.get("ai_label") or "",
            "aliases": info.get("aliases") or [],
            "unit": info.get("unit") or unit_hints.get(key) or "",
        }
        fields.append(field)

    field_lines = []
    for f in fields:
        aliases = f.get("aliases") or []
        alias_str = "、".join([a for a in aliases if isinstance(a, str)]) if isinstance(aliases, list) else ""
        unit = f.get("unit") or ""
        field_lines.append(
            f"- {f['key']} | type={f['type']} | unit={unit} | label={f['ai_label']} | aliases={alias_str}"
        )
    idx_lines = [f"- img_index={i} url={u}" for i, u in enumerate(candidate_image_urls)]

    prompt = (
        "请分析以下商品详情长图，忽略装饰图内容，只关注“规格参数/参数表/配置表”区域。\n"
        "你必须对每个字段返回 value，并给出证据 raw_text、来源图片索引 img_index、置信度 confidence。\n"
        "如果图片未提及该字段，请返回 value='N/A'，raw_text=''，confidence=0。\n"
        "字段列表：\n"
        + "\n".join(field_lines)
        + "\n图片索引：\n"
        + "\n".join(idx_lines)
        + "\n"
        '只返回 JSON，格式如下：{"fields": { "<field_key>": {"value": ..., "raw_text": "...", "img_index": 0, "confidence": 0.0 } }, "meta": {"model_version": "..."} }'
    )

    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for img in candidate_image_jpegs:
        data_url = image_bytes_to_data_url(img)
        content.append({"type": "image_url", "image_url": {"url": data_url, "detail": "high"}})

    messages = [
        {"role": "system", "content": "严格输出 JSON，不要输出多余文本。"},
        {"role": "user", "content": content},
    ]
    return openai_chat_json(
        api_key=cfg.openai_api_key,
        base_url=cfg.openai_base_url,
        model=cfg.openai_model,
        messages=messages,
        timeout_s=cfg.request_timeout_s,
    )


def parse_number_with_unit(text: str) -> tuple[Optional[float], str]:
    if not isinstance(text, str):
        return None, ""
    s = text.strip()
    m = re.search(r"([-+]?\d+(?:\.\d+)?)\s*([a-zA-Z]+|英寸|″)?", s)
    if not m:
        return None, ""
    num = float(m.group(1))
    unit = (m.group(2) or "").strip()
    unit = unit.replace("″", "inch")
    if unit == "英寸":
        unit = "inch"
    return num, unit


def normalize_number(value: Any, raw_text: str, target_unit: str) -> Optional[float]:
    target = (target_unit or "").strip()
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        v = value.strip()
        if not v or v.upper() == "N/A":
            return None
        num, unit = parse_number_with_unit(v)
        if num is None:
            num, unit = parse_number_with_unit(raw_text or "")
        if num is None:
            return None
        unit_norm = unit.lower()
        target_norm = target.lower()
        if not target_norm:
            return num
        if target_norm == "gb":
            if unit_norm in ("gb", "g"):
                return num
            if unit_norm == "mb":
                return num / 1024
            if unit_norm == "tb":
                return num * 1024
            return num
        if target_norm == "mah":
            if unit_norm == "mah":
                return num
            if unit_norm == "ah":
                return num * 1000
            return num
        if target_norm == "g":
            if unit_norm == "g":
                return num
            if unit_norm == "kg":
                return num * 1000
            if unit_norm == "mg":
                return num / 1000
            return num
        if target_norm == "kg":
            if unit_norm == "kg":
                return num
            if unit_norm == "g":
                return num / 1000
            if unit_norm == "mg":
                return num / 1_000_000
            return num
        if target_norm == "inch":
            return num
        return num
    return None


def build_ai_context(
    *,
    template_schema: dict[str, Any],
    unit_hints: dict[str, Any],
    extracted: dict[str, Any],
    candidate_image_urls: list[str],
    scan_time: str,
    model_version: str,
    total_images_scanned: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    props = template_schema.get("properties") or {}
    required = template_schema.get("required") or []
    fields_out: dict[str, Any] = {}
    evidence: dict[str, Any] = {}

    extracted_fields = (extracted or {}).get("fields") or {}
    for key, info in props.items():
        prop = info if isinstance(info, dict) else {}
        typ = prop.get("type") or "string"
        unit = prop.get("unit") or unit_hints.get(key) or ""

        item = extracted_fields.get(key) or {}
        if not isinstance(item, dict):
            item = {}
        value = item.get("value")
        raw_text = item.get("raw_text") if isinstance(item.get("raw_text"), str) else ""
        img_index = item.get("img_index")
        conf = item.get("confidence")
        confidence = float(conf) if isinstance(conf, (int, float)) else 0.0

        source_img = ""
        if isinstance(img_index, int) and 0 <= img_index < len(candidate_image_urls):
            source_img = candidate_image_urls[img_index]

        if typ == "number":
            n = normalize_number(value, raw_text, str(unit))
            fields_out[key] = n if n is not None else "N/A"
        else:
            if value is None or (isinstance(value, str) and (not value.strip() or value.strip().upper() == "N/A")):
                fields_out[key] = "N/A"
            else:
                fields_out[key] = str(value)

        evidence[key] = {"raw_text": raw_text, "source_img": source_img, "confidence": confidence}

    ai_report = {
        "scan_time": scan_time,
        "model_version": model_version,
        "total_images_scanned": total_images_scanned,
    }

    ai_context = {**fields_out, "_evidence": evidence, "_ai_report": ai_report}

    stats = compute_alignment_stats(fields_out=fields_out, required=required)
    return ai_context, stats


def compute_alignment_stats(*, fields_out: dict[str, Any], required: list[Any]) -> dict[str, Any]:
    keys = list(fields_out.keys())
    total_all = len(keys)
    filled_all = sum(1 for k in keys if fields_out.get(k) != "N/A")
    coverage_all = (filled_all / total_all) if total_all else 0.0

    required_keys = [r for r in required if isinstance(r, str) and r in fields_out]
    total_req = len(required_keys)
    filled_req = sum(1 for k in required_keys if fields_out.get(k) != "N/A")
    coverage_req = (filled_req / total_req) if total_req else None

    missing_all = [k for k in keys if fields_out.get(k) == "N/A"]
    missing_req = [k for k in required_keys if fields_out.get(k) == "N/A"]

    return {
        "coverage_rate_all": coverage_all,
        "coverage_rate_required": coverage_req,
        "missing_fields_all": missing_all,
        "missing_required_fields": missing_req,
        "total_fields": total_all,
        "filled_fields": filled_all,
        "total_required_fields": total_req,
        "filled_required_fields": filled_req,
    }


def write_reports(
    *,
    report_dir: str,
    run_id: str,
    rows: list[dict[str, Any]],
) -> tuple[str, str]:
    os.makedirs(report_dir, exist_ok=True)
    csv_path = os.path.join(report_dir, f"ai_alignment_report_{run_id}.csv")
    json_path = os.path.join(report_dir, f"ai_alignment_report_{run_id}.json")

    summary = build_report_summary(rows)
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "items": rows}, f, ensure_ascii=False, indent=2)

    if rows:
        headers = list(rows[0].keys())
    else:
        headers = []

    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=headers)
        w.writeheader()
        for r in rows:
            w.writerow(r)

    return csv_path, json_path


def build_report_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    total = len(rows)
    if total == 0:
        return {"total_items": 0}

    parse_ok_cnt = sum(1 for r in rows if bool(r.get("parse_ok")))
    cover_all = [r.get("coverage_rate_all") for r in rows if isinstance(r.get("coverage_rate_all"), (int, float))]
    cover_req = [
        r.get("coverage_rate_required")
        for r in rows
        if isinstance(r.get("coverage_rate_required"), (int, float))
    ]
    avg_cover_all = (sum(cover_all) / len(cover_all)) if cover_all else 0.0
    avg_cover_req = (sum(cover_req) / len(cover_req)) if cover_req else None

    missing_counter: dict[str, int] = {}
    for r in rows:
        s = r.get("missing_fields_all")
        if not isinstance(s, str) or not s:
            continue
        for k in [x for x in s.split("|") if x]:
            missing_counter[k] = missing_counter.get(k, 0) + 1

    top_missing = sorted(missing_counter.items(), key=lambda x: (-x[1], x[0]))[:30]

    return {
        "total_items": total,
        "parse_ok_rate": parse_ok_cnt / total,
        "avg_coverage_rate_all": avg_cover_all,
        "avg_coverage_rate_required": avg_cover_req,
        "top_missing_fields": [{"field": k, "missing_count": v} for k, v in top_missing],
    }


def upsert_ai_context(
    *,
    cursor,
    cfg: Config,
    goods_id: str,
    category_id: Any,
    ai_context: dict[str, Any],
):
    sql = f"""
INSERT INTO li_goods_ai_context
(goods_id, category_id, template_version, {cfg.context_column}, needs_refresh)
VALUES (%s, %s, %s, %s, 0)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  template_version = VALUES(template_version),
  {cfg.context_column} = VALUES({cfg.context_column}),
  needs_refresh = 0
"""
    cursor.execute(
        sql,
        (
            str(goods_id),
            int(category_id) if category_id is not None else None,
            int(cfg.template_version),
            json.dumps(ai_context, ensure_ascii=False),
        ),
    )


def fetch_pending_goods(cursor, limit: int, offset: int) -> list[dict[str, Any]]:
    sql = """
WITH matched AS (
  SELECT
    g.id,
    g.goods_name,
    g.category_path,
    g.intro,
    g.update_time AS goods_update_time,
    t.category_id,
    t.schema_json AS template_schema,
    t.unit_hints AS unit_hints,
    t.priority,
    ROW_NUMBER() OVER (
      PARTITION BY g.id
      ORDER BY t.priority ASC, CHAR_LENGTH(t.category_id) DESC
    ) AS rn
  FROM li_goods g
  JOIN li_category_ai_template t
    ON g.category_path LIKE CONCAT('%', t.category_id, '%')
  WHERE g.delete_flag = 0 AND g.intro IS NOT NULL
),
picked AS (
  SELECT * FROM matched WHERE rn = 1
)
SELECT
  p.id,
  p.goods_name,
  p.category_id,
  p.intro,
  p.template_schema,
  p.unit_hints,
  p.goods_update_time,
  c.update_time AS ctx_update_time,
  c.needs_refresh
FROM picked p
LEFT JOIN li_goods_ai_context c
  ON CAST(p.id AS CHAR) = c.goods_id
WHERE
  c.goods_id IS NULL
  OR p.goods_update_time > c.update_time
  OR c.needs_refresh = 1
ORDER BY p.id
LIMIT %s OFFSET %s
"""
    cursor.execute(sql, (limit, offset))
    return cursor.fetchall()


def safe_json_loads(s: Any) -> dict[str, Any]:
    if not s:
        return {}
    if isinstance(s, dict):
        return s
    if isinstance(s, str):
        try:
            return json.loads(s)
        except Exception:
            return {}
    return {}


def run_pipeline(cfg: Config) -> tuple[str, str]:
    db = mysql.connector.connect(
        host=cfg.db_host,
        port=cfg.db_port,
        user=cfg.db_user,
        password=cfg.db_password,
        database=cfg.db_name,
        autocommit=False,
    )
    cursor = db.cursor(dictionary=True)
    run_id = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    report_rows: list[dict[str, Any]] = []

    offset = 0
    total_processed = 0
    while True:
        rows = fetch_pending_goods(cursor, cfg.batch_size, offset)
        if not rows:
            break
        offset += len(rows)

        for r in rows:
            goods_id = str(r["id"])
            category_id = r.get("category_id")
            intro = r.get("intro") or ""
            template_schema = safe_json_loads(r.get("template_schema"))
            unit_hints = safe_json_loads(r.get("unit_hints"))

            props = template_schema.get("properties") or {}
            if not isinstance(props, dict) or not props:
                continue

            scan_time = now_ts()
            img_urls = extract_image_urls(intro)

            reason_codes: list[str] = []
            total_images = len(img_urls)
            candidate_urls: list[str] = []
            candidate_jpegs: list[bytes] = []

            if not img_urls:
                reason_codes.append("NO_IMG")
                extracted = {"fields": {}}
                ai_context, stats = build_ai_context(
                    template_schema=template_schema,
                    unit_hints=unit_hints,
                    extracted=extracted,
                    candidate_image_urls=[],
                    scan_time=scan_time,
                    model_version=cfg.openai_model,
                    total_images_scanned=0,
                )
                upsert_ai_context(
                    cursor=cursor,
                    cfg=cfg,
                    goods_id=goods_id,
                    category_id=category_id,
                    ai_context=ai_context,
                )
                db.commit()
                report_rows.append(
                    {
                        "goods_id": goods_id,
                        "category_id": category_id,
                        "template_version": cfg.template_version,
                        "scan_time": scan_time,
                        "model_version": cfg.openai_model,
                        "total_images": total_images,
                        "param_candidate_count": 0,
                        "high_res_scanned_count": 0,
                        "parse_ok": True,
                        "coverage_rate_all": stats["coverage_rate_all"],
                        "coverage_rate_required": stats["coverage_rate_required"],
                        "missing_fields_all": "|".join(stats["missing_fields_all"]),
                        "missing_required_fields": "|".join(stats["missing_required_fields"]),
                        "reason_codes": "|".join(reason_codes),
                    }
                )
                total_processed += 1
                continue

            template_keywords = build_template_keywords(template_schema)

            for u in img_urls:
                try:
                    img_bytes = download_image(u, cfg.request_timeout_s)
                    w, h = get_image_size(img_bytes)
                    if min(w, h) < 300 or (w * h) < 200_000:
                        continue
                    if (h / max(1, w)) < 0.6 and w < 1200:
                        continue
                    img_small = resize_max_width_jpeg(img_bytes, 900)
                    cls = classify_param_candidate(
                        cfg=cfg,
                        image_jpeg=img_small,
                        template_keywords=template_keywords,
                    )
                    is_param = bool(cls.get("is_param"))
                    score = cls.get("score")
                    score_f = float(score) if isinstance(score, (int, float)) else 0.0
                    if is_param and score_f >= 0.6:
                        candidate_urls.append(u)
                        candidate_jpegs.append(to_jpeg_bytes(img_bytes))
                        if len(candidate_urls) >= cfg.max_candidate_images:
                            break
                except Exception:
                    continue

            if not candidate_urls:
                reason_codes.append("NO_PARAM_CANDIDATE")
                extracted = {"fields": {}}
                ai_context, stats = build_ai_context(
                    template_schema=template_schema,
                    unit_hints=unit_hints,
                    extracted=extracted,
                    candidate_image_urls=[],
                    scan_time=scan_time,
                    model_version=cfg.openai_model,
                    total_images_scanned=0,
                )
                upsert_ai_context(
                    cursor=cursor,
                    cfg=cfg,
                    goods_id=goods_id,
                    category_id=category_id,
                    ai_context=ai_context,
                )
                db.commit()
                report_rows.append(
                    {
                        "goods_id": goods_id,
                        "category_id": category_id,
                        "template_version": cfg.template_version,
                        "scan_time": scan_time,
                        "model_version": cfg.openai_model,
                        "total_images": total_images,
                        "param_candidate_count": 0,
                        "high_res_scanned_count": 0,
                        "parse_ok": True,
                        "coverage_rate_all": stats["coverage_rate_all"],
                        "coverage_rate_required": stats["coverage_rate_required"],
                        "missing_fields_all": "|".join(stats["missing_fields_all"]),
                        "missing_required_fields": "|".join(stats["missing_required_fields"]),
                        "reason_codes": "|".join(reason_codes),
                    }
                )
                total_processed += 1
                continue

            parse_ok = True
            extracted: dict[str, Any] = {}
            try:
                extracted = extract_fields_from_images(
                    cfg=cfg,
                    template_schema=template_schema,
                    unit_hints=unit_hints,
                    candidate_image_urls=candidate_urls,
                    candidate_image_jpegs=candidate_jpegs,
                )
            except Exception:
                parse_ok = False
                reason_codes.append("EXTRACT_FAIL")
                extracted = {"fields": {}}

            ai_context, stats = build_ai_context(
                template_schema=template_schema,
                unit_hints=unit_hints,
                extracted=extracted,
                candidate_image_urls=candidate_urls,
                scan_time=scan_time,
                model_version=cfg.openai_model,
                total_images_scanned=len(candidate_urls),
            )

            upsert_ai_context(
                cursor=cursor,
                cfg=cfg,
                goods_id=goods_id,
                category_id=category_id,
                ai_context=ai_context,
            )
            db.commit()

            report_rows.append(
                {
                    "goods_id": goods_id,
                    "category_id": category_id,
                    "template_version": cfg.template_version,
                    "scan_time": scan_time,
                    "model_version": cfg.openai_model,
                    "total_images": total_images,
                    "param_candidate_count": len(candidate_urls),
                    "high_res_scanned_count": len(candidate_urls),
                    "parse_ok": parse_ok,
                    "coverage_rate_all": stats["coverage_rate_all"],
                    "coverage_rate_required": stats["coverage_rate_required"],
                    "missing_fields_all": "|".join(stats["missing_fields_all"]),
                    "missing_required_fields": "|".join(stats["missing_required_fields"]),
                    "reason_codes": "|".join(reason_codes),
                }
            )
            total_processed += 1
            time.sleep(0.2)

    csv_path, json_path = write_reports(report_dir=cfg.report_dir, run_id=run_id, rows=report_rows)
    cursor.close()
    db.close()
    return csv_path, json_path


def self_test() -> None:
    html = """
<p><img src="`https://a.com/1.jpg`"/></p>
<p><img data-src="https://a.com/2.jpg"/></p>
"""
    urls = extract_image_urls(html)
    assert urls == ["https://a.com/1.jpg", "https://a.com/2.jpg"]
    n1 = normalize_number("8GB", "8GB LPDDR5 运存", "GB")
    assert n1 is not None and abs(n1 - 8) < 1e-6
    n2 = normalize_number("8192MB", "8192MB", "GB")
    assert n2 is not None and abs(n2 - 8) < 1e-6
    n3 = normalize_number("4.5Ah", "4.5Ah", "mAh")
    assert n3 is not None and abs(n3 - 4500) < 1e-6


def load_cfg(args: argparse.Namespace) -> Config:
    db_host = os.getenv("LILISHOP_DB_HOST", args.db_host)
    db_port = int(os.getenv("LILISHOP_DB_PORT", str(args.db_port)))
    db_user = os.getenv("LILISHOP_DB_USER", args.db_user)
    db_password = os.getenv("LILISHOP_DB_PASSWORD", args.db_password or "")
    db_name = os.getenv("LILISHOP_DB_NAME", args.db_name)

    openai_api_key = os.getenv("OPENAI_API_KEY", args.openai_api_key or "")
    openai_model = os.getenv("OPENAI_MODEL", args.openai_model)
    openai_base_url = os.getenv("OPENAI_BASE_URL", args.openai_base_url)

    if not openai_api_key:
        raise SystemExit("missing OPENAI_API_KEY")
    if not db_password:
        raise SystemExit("missing LILISHOP_DB_PASSWORD")

    return Config(
        db_host=db_host,
        db_port=db_port,
        db_user=db_user,
        db_password=db_password,
        db_name=db_name,
        openai_api_key=openai_api_key,
        openai_model=openai_model,
        openai_base_url=openai_base_url,
        template_version=args.template_version,
        batch_size=args.batch_size,
        max_candidate_images=args.max_candidate_images,
        request_timeout_s=args.request_timeout_s,
        report_dir=args.report_dir,
        context_column=args.context_column,
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db-host", default="127.0.0.1")
    ap.add_argument("--db-port", type=int, default=3306)
    ap.add_argument("--db-user", default="root")
    ap.add_argument("--db-password", default="")
    ap.add_argument("--db-name", default="lilishop")
    ap.add_argument("--openai-api-key", default="")
    ap.add_argument("--openai-model", default="gpt-4o-2024-05-13")
    ap.add_argument("--openai-base-url", default="https://api.openai.com")
    ap.add_argument("--template-version", type=int, default=1)
    ap.add_argument("--batch-size", type=int, default=200)
    ap.add_argument("--max-candidate-images", type=int, default=6)
    ap.add_argument("--request-timeout-s", type=int, default=30)
    ap.add_argument("--report-dir", default=os.path.join(os.getcwd(), "ai_context_reports"))
    ap.add_argument("--context-column", default="ai_context")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return

    cfg = load_cfg(args)
    csv_path, json_path = run_pipeline(cfg)
    print(csv_path)
    print(json_path)


if __name__ == "__main__":
    main()
