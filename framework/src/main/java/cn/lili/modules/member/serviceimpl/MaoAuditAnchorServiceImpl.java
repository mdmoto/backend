package cn.lili.modules.member.serviceimpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.member.audit.MaocoinMerkleTree;
import cn.lili.modules.member.entity.dos.MaoAuditAnchor;
import cn.lili.modules.member.entity.dos.MemberPointsHistory;
import cn.lili.modules.member.entity.enums.PointTypeEnum;
import cn.lili.modules.member.mapper.MaoAuditAnchorMapper;
import cn.lili.modules.member.service.MaoAuditAnchorService;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MAO 猫币账本公开审计锚业务实现
 */
@Slf4j
@Service
public class MaoAuditAnchorServiceImpl extends ServiceImpl<MaoAuditAnchorMapper, MaoAuditAnchor>
        implements MaoAuditAnchorService {

    private static final String ROOT_SCOPE = "ALL_HISTORY_UNTIL_UTC_DAY";
    private static final String STATUS_LOCAL_CREATED = "LOCAL_CREATED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_PUBLISH_FAILED = "PUBLISH_FAILED";
    private static final String ANCHOR_TARGET = "SOLANA_MEMO";

    @Autowired
    private MemberPointsHistoryService memberPointsHistoryService;

    @Value("${lili.maollar.solana.gateway-url:}")
    private String gatewayUrl;

    @Value("${lili.maollar.solana.gateway-secret:}")
    private String gatewaySecret;

    @Override
    public MaoAuditAnchor createDailyAnchor(LocalDate anchorDate) {
        LocalDate targetDate = anchorDate == null ? LocalDate.now(ZoneOffset.UTC).minusDays(1) : anchorDate;
        String anchorKey = buildAnchorKey(targetDate);

        MaoAuditAnchor existing = getByAnchorKey(anchorKey);
        if (existing != null) {
            if (!STATUS_PUBLISHED.equals(existing.getAnchorStatus())) {
                publishAnchor(existing);
                this.updateById(existing);
            }
            return existing;
        }

        Date anchoredUntil = Date.from(targetDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1));
        List<MemberPointsHistory> histories = listHistoriesUntil(anchoredUntil);

        MaoAuditAnchor anchor = new MaoAuditAnchor();
        anchor.setAnchorKey(anchorKey);
        anchor.setAnchorDate(targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        anchor.setAnchorTimezone("UTC");
        anchor.setAnchoredUntil(anchoredUntil);
        anchor.setRootScope(ROOT_SCOPE);
        anchor.setRecordCount((long) histories.size());
        anchor.setTotalIncreasePoints(sumPoints(histories, PointTypeEnum.INCREASE.name()));
        anchor.setTotalReducePoints(sumPoints(histories, PointTypeEnum.REDUCE.name()));
        anchor.setTotalFundReserve(sumFundReserve(histories));
        anchor.setFirstHistoryId(histories.isEmpty() ? null : histories.get(0).getId());
        anchor.setLastHistoryId(histories.isEmpty() ? null : histories.get(histories.size() - 1).getId());
        anchor.setMerkleRoot(buildMerkleRoot(histories));
        anchor.setLedgerHash(buildLedgerHash(histories));
        anchor.setAnchorPayloadHash(buildAnchorPayloadHash(anchor));
        anchor.setAnchorStatus(STATUS_LOCAL_CREATED);
        anchor.setAnchorTarget(ANCHOR_TARGET);

        try {
            this.save(anchor);
        } catch (DuplicateKeyException e) {
            MaoAuditAnchor concurrentAnchor = getByAnchorKey(anchorKey);
            if (concurrentAnchor != null) {
                return concurrentAnchor;
            }
            throw e;
        }

        publishAnchor(anchor);
        this.updateById(anchor);
        return anchor;
    }

    @Override
    public MaoAuditAnchor getLatestAnchor() {
        QueryWrapper<MaoAuditAnchor> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("anchor_date").orderByDesc("create_time").last("LIMIT 1");
        return this.getOne(queryWrapper, false);
    }

    @Override
    public List<MaoAuditAnchor> getRecentAnchors(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        QueryWrapper<MaoAuditAnchor> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("anchor_date").orderByDesc("create_time").last("LIMIT " + safeLimit);
        return this.list(queryWrapper);
    }

    private MaoAuditAnchor getByAnchorKey(String anchorKey) {
        QueryWrapper<MaoAuditAnchor> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("anchor_key", anchorKey).last("LIMIT 1");
        return this.getOne(queryWrapper, false);
    }

    private List<MemberPointsHistory> listHistoriesUntil(Date anchoredUntil) {
        QueryWrapper<MemberPointsHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "member_id", "point_type", "variable_point", "before_point", "point",
                        "biz_id", "fund_reserve", "merkle_timestamp", "merkle_hash", "create_time")
                .le("create_time", anchoredUntil)
                .isNotNull("merkle_hash")
                .orderByAsc("create_time")
                .orderByAsc("id");
        return memberPointsHistoryService.list(queryWrapper);
    }

    private String buildMerkleRoot(List<MemberPointsHistory> histories) {
        List<String> hashes = histories.stream()
                .map(MemberPointsHistory::getMerkleHash)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        return MaocoinMerkleTree.buildRoot(hashes);
    }

    private Long sumPoints(List<MemberPointsHistory> histories, String pointType) {
        return histories.stream()
                .filter(history -> pointType.equals(history.getPointType()))
                .map(MemberPointsHistory::getVariablePoint)
                .filter(point -> point != null)
                .reduce(0L, Long::sum);
    }

    private BigDecimal sumFundReserve(List<MemberPointsHistory> histories) {
        return histories.stream()
                .map(MemberPointsHistory::getFundReserve)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildLedgerHash(List<MemberPointsHistory> histories) {
        if (histories.isEmpty()) {
            return DigestUtil.sha256Hex("MAOLLAR_EMPTY_LEDGER_V1");
        }
        StringBuilder builder = new StringBuilder();
        for (MemberPointsHistory history : histories) {
            builder.append(value(history.getId())).append('|')
                    .append(value(history.getMemberId())).append('|')
                    .append(value(history.getPointType())).append('|')
                    .append(value(history.getVariablePoint())).append('|')
                    .append(value(history.getBeforePoint())).append('|')
                    .append(value(history.getPoint())).append('|')
                    .append(normalize(history.getFundReserve())).append('|')
                    .append(value(history.getBizId())).append('|')
                    .append(value(history.getMerkleTimestamp())).append('|')
                    .append(value(history.getMerkleHash())).append('|')
                    .append(history.getCreateTime() == null ? 0L : history.getCreateTime().getTime())
                    .append('\n');
        }
        return DigestUtil.sha256Hex(builder.toString());
    }

    private String buildAnchorPayloadHash(MaoAuditAnchor anchor) {
        String payload = anchor.getAnchorKey() + "|" +
                anchor.getAnchorDate() + "|" +
                anchor.getAnchoredUntil().getTime() + "|" +
                anchor.getRootScope() + "|" +
                anchor.getMerkleRoot() + "|" +
                anchor.getLedgerHash() + "|" +
                anchor.getRecordCount() + "|" +
                anchor.getTotalIncreasePoints() + "|" +
                anchor.getTotalReducePoints() + "|" +
                normalize(anchor.getTotalFundReserve());
        return DigestUtil.sha256Hex(payload);
    }

    private void publishAnchor(MaoAuditAnchor anchor) {
        if (StrUtil.isBlank(gatewayUrl) || StrUtil.isBlank(gatewaySecret)) {
            anchor.setErrorLog("Solana gateway is not configured; local audit anchor has been created.");
            return;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", "ANCHOR_AUDIT_ROOT");
            body.put("request_id", "AUDIT_ANCHOR_" + anchor.getAnchorKey());
            body.put("anchor_key", anchor.getAnchorKey());
            body.put("anchor_date", anchor.getAnchorDate());
            body.put("merkle_root", anchor.getMerkleRoot());
            body.put("ledger_hash", anchor.getLedgerHash());
            body.put("payload_hash", anchor.getAnchorPayloadHash());
            body.put("record_count", anchor.getRecordCount());
            body.put("total_increase_points", anchor.getTotalIncreasePoints());
            body.put("total_reduce_points", anchor.getTotalReducePoints());
            body.put("total_fund_reserve", normalize(anchor.getTotalFundReserve()));

            String timestamp = String.valueOf(System.currentTimeMillis());
            String bodyText = JSONUtil.toJsonStr(body);
            String signature = SecureUtil.hmacSha256(gatewaySecret).digestHex(timestamp + bodyText);

            String result = HttpUtil.createPost(gatewayUrl)
                    .header("X-MAO-Timestamp", timestamp)
                    .header("X-MAO-Signature", signature)
                    .body(bodyText)
                    .timeout(30000)
                    .execute()
                    .body();

            JSONObject jsonObject = JSONUtil.parseObj(result);
            if (Boolean.TRUE.equals(jsonObject.getBool("success"))) {
                anchor.setAnchorStatus(STATUS_PUBLISHED);
                anchor.setAnchorTarget(ANCHOR_TARGET);
                anchor.setAnchorTxHash(jsonObject.getStr("txHash"));
                anchor.setErrorLog(null);
                return;
            }
            throw new IllegalStateException(StrUtil.blankToDefault(jsonObject.getStr("message"), result));
        } catch (Exception e) {
            log.error("【Maollar 审计锚】外部锚定失败 anchorKey={}", anchor.getAnchorKey(), e);
            anchor.setAnchorStatus(STATUS_PUBLISH_FAILED);
            anchor.setErrorLog(truncate(e.getMessage(), 1024));
        }
    }

    private String buildAnchorKey(LocalDate anchorDate) {
        return "MAO-LEDGER-" + anchorDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String normalize(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
