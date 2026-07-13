import mysql.connector
import json

def deep_datafication():
    try:
        db = mysql.connector.connect(
            host="127.0.0.1", user="root", password="", database="lilishop"
        )
        cursor = db.cursor(dictionary=True)
        cursor.execute("SELECT category_id, schema_json FROM li_category_ai_template WHERE is_active = 1")
        templates = {t['category_id']: json.loads(t['schema_json']) for t in cursor.fetchall()}

        cursor.execute("SELECT id, goods_name, category_path, params FROM li_goods WHERE delete_flag = 0 AND category_path IS NOT NULL")
        goods_list = cursor.fetchall()

        count = 0
        for goods in goods_list:
            goods_id = str(goods['id'])
            try:
                category_id = int(goods['category_path'].split(',')[-1])
            except: continue
            
            template = templates.get(category_id)
            if not template: continue

            raw_data = {}
            if goods['params']:
                try:
                    p_list = json.loads(goods['params'])
                    raw_data = {p['name']: p['value'] for p in p_list if 'name' in p and 'value' in p}
                except: pass

            ai_context = {"product_name": goods['goods_name']}
            for key, info in template.get('properties', {}).items():
                label = info.get('ai_label', key)
                ai_context[key] = raw_data.get(label) or raw_data.get(key) or "N/A"

            cursor.execute(
                "UPDATE li_goods_ai_context SET ai_context = %s, needs_refresh = 0 WHERE goods_id = %s",
                (json.dumps(ai_context, ensure_ascii=False), goods_id)
            )
            count += 1

        db.commit()
        print(f"Deep Datafication completed for {count} goods.")
        cursor.close()
        db.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    deep_datafication()
