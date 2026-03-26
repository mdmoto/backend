-- SQL to add common international countries to li_region table
-- Root parent_id is '0' in Lilishop

-- First, ensure we don't duplicate if they already exist
-- Japan
INSERT INTO li_region (id, name, parent_id, path, level, order_num) 
SELECT '2000000001', '日本', '0', '0,2000000001', 'country', 100
WHERE NOT EXISTS (SELECT 1 FROM li_region WHERE name = '日本' AND parent_id = '0');

-- USA
INSERT INTO li_region (id, name, parent_id, path, level, order_num) 
SELECT '2000000002', '美国', '0', '0,2000000002', 'country', 101
WHERE NOT EXISTS (SELECT 1 FROM li_region WHERE name = '美国' AND parent_id = '0');

-- Hong Kong (SAR)
INSERT INTO li_region (id, name, parent_id, path, level, order_num) 
SELECT '2000000003', '中国香港', '0', '0,2000000003', 'country', 102
WHERE NOT EXISTS (SELECT 1 FROM li_region WHERE name = '中国香港' AND parent_id = '0');

-- Taiwan
INSERT INTO li_region (id, name, parent_id, path, level, order_num) 
SELECT '2000000004', '中国台湾', '0', '0,2000000004', 'country', 103
WHERE NOT EXISTS (SELECT 1 FROM li_region WHERE name = '中国台湾' AND parent_id = '0');

-- Singapore
INSERT INTO li_region (id, name, parent_id, path, level, order_num) 
SELECT '2000000005', '新加坡', '0', '0,2000000005', 'country', 104
WHERE NOT EXISTS (SELECT 1 FROM li_region WHERE name = '新加坡' AND parent_id = '0');
