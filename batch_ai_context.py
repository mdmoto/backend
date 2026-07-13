import mysql.connector
import json

def generate_ai_context():
    # 数据库连接
    db = mysql.connector.connect(
        host="127.0.0.1",
        user="root",
        password="",
        database="lilishop"
    )
    cursor = db.cursor(dictionary=True)

    # 1. 获取所有模版
    cursor.execute("SELECT category_id, schema_json FROM li_category_ai_template WHERE is_active = 1")
    templates = {t['category_id']: json.loads(t['schema_json']) for t in cursor.fetchall()}

    # 2. 获取所有商品
    cursor.execute("SELECT id, goods_name, category_path, params FROM li_goods WHERE delete_flag = 0")
    goods_list = cursor.fetchall()

    processed_count = 0
    for goods in goods_list:
        goods_id = goods['id']
        # 获取商品最后级分类ID
        category_id = int(goods['category_path'].split(',')[-1])
        
        template = templates.get(category_id)
        if not template:
            # 如果没找到精准模版，查找父级模版 (这里简化处理，直接找父级)
            continue

        raw_params = []
        try:
            raw_params = json.loads(goods['params']) if goods['params'] else []
        except:
            pass

        # 将原始参数转为字典方便查询
        param_dict = {p.get('name'): p.get('value') for p in raw_params}
        
        # 按照模版进行对齐
        ai_data = {}
        for prop_key, prop_val in template.get('properties', {}).items():
            label = prop_val.get('ai_label')
            # 尝试在原始参数中匹配标签名或KEY
            value = param_dict.get(label) or param_dict.get(prop_key) or "N/A"
            ai_data[prop_key] = value

        # 3. 写入 AI 上下文表
        ai_context_json = json.dumps(ai_data, ensure_ascii=False)
        insert_sql = """
        INSERT INTO li_goods_ai_context (goods_id, category_id, ai_context_json, last_update)
        VALUES (%s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE ai_context_json = VALUES(ai_context_json), last_update = NOW()
        """
        cursor.execute(insert_sql, (goods_id, category_id, ai_context_json))
        processed_count += 1

    db.commit()
    print(f"Successfully processed {processed_count} goods.")
    cursor.close()
    db.close()

if __name__ == "__main__":
    generate_ai_context()
