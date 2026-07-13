-- =============================================================================
-- Version 6.0 - AI Category Upgrade (Phase A: Smooth Migration)
-- Adapted to actual DB: li_category.id is BIGINT, not VARCHAR
-- Run date: 2026-05-08
-- =============================================================================

-- Phase 1: Add slug / name_en / ai_description columns to li_category
-- Safe ALTER: adds nullable columns, no existing data is touched
ALTER TABLE `li_category`
    ADD COLUMN IF NOT EXISTS `slug`           VARCHAR(100) NULL COMMENT '英文标识符，供AI/API使用，如 electronics' AFTER `name`,
    ADD COLUMN IF NOT EXISTS `name_en`        VARCHAR(100) NULL COMMENT '英文分类名称' AFTER `slug`,
    ADD COLUMN IF NOT EXISTS `ai_description` VARCHAR(500) NULL COMMENT '给AI的简短描述，用于检索提示' AFTER `name_en`;

-- Phase 2: Populate slugs for Level-0 (top-level) categories based on actual names
UPDATE `li_category` SET slug='electronics', name_en='Electronics & Tech',        ai_description='数码科技，含手机/平板/笔记本/智能设备'    WHERE name='数码办公'   AND level=0;
UPDATE `li_category` SET slug='appliances',  name_en='Home Appliances',           ai_description='家用电器，含冰箱/洗衣机/空调/小家电'     WHERE name='家用电器'   AND level=0;
UPDATE `li_category` SET slug='fashion',     name_en='Fashion & Apparel',         ai_description='服装鞋帽，含男装/女装/童装/鞋/帽'        WHERE name='服装鞋帽'   AND level=0;
UPDATE `li_category` SET slug='food-beverage', name_en='Food & Beverage',         ai_description='食品饮料，含生鲜/零食/酒类/保健品'        WHERE name='食品饮料'   AND level=0;
UPDATE `li_category` SET slug='beauty',      name_en='Beauty & Personal Care',    ai_description='个护化妆，含护肤/彩妆/香水/洗护'         WHERE name='个护化妆'   AND level=0;
UPDATE `li_category` SET slug='home-living', name_en='Home & Living',             ai_description='家居家装，含家具/家纺/装饰/清洁日用'      WHERE name='家居家装'   AND level=0;
UPDATE `li_category` SET slug='bags-gifts',  name_en='Bags, Gifts & Luggage',     ai_description='礼品箱包，含女包/男包/礼品/奢侈品'        WHERE name='礼品箱包'   AND level=0;
UPDATE `li_category` SET slug='kitchen',     name_en='Kitchen & Dining',          ai_description='厨房餐饮，含锅具/餐具/茶具/收纳'         WHERE name='厨房餐饮'   AND level=0;
UPDATE `li_category` SET slug='toys-music',  name_en='Toys, Kids & Instruments',  ai_description='玩具乐器，含玩具/乐器/益智/模型'         WHERE name='玩具乐器'   AND level=0;
UPDATE `li_category` SET slug='automotive',  name_en='Automotive',                ai_description='汽车用品，含车内/车外/工具/改装'         WHERE name='汽车用品'   AND level=0;
UPDATE `li_category` SET slug='daily-life',  name_en='Daily Life & Wellness',     ai_description='惠民生活，含日用品/健康/生活服务'        WHERE name='惠民生活'   AND level=0;
UPDATE `li_category` SET slug='b2b',         name_en='B2B & Agency',              ai_description='招商代理，含代理/招商/批发'              WHERE name='招商代理'   AND level=0;

-- Phase 2b: Key Level-1 slugs (subset of most important for AI)
UPDATE `li_category` SET slug='smartphones',      name_en='Smartphones'         WHERE name='手机'       AND level=1;
UPDATE `li_category` SET slug='laptops',          name_en='Laptops & Computers' WHERE name='电脑整机'   AND level=1;
UPDATE `li_category` SET slug='computer-accessories', name_en='Computer Accessories' WHERE name='电脑配件' AND level=1;
UPDATE `li_category` SET slug='digital-accessories',  name_en='Digital Accessories'  WHERE name='数码配件' AND level=1;
UPDATE `li_category` SET slug='networking',       name_en='Networking'          WHERE name='网络产品'   AND level=1;
UPDATE `li_category` SET slug='peripherals',      name_en='Peripherals'         WHERE name='外设产品'   AND level=1;
UPDATE `li_category` SET slug='photography',      name_en='Photography'         WHERE name='摄影摄像'   AND level=1;
UPDATE `li_category` SET slug='womens-fashion',   name_en="Women's Fashion"     WHERE name='女装'       AND level=1;
UPDATE `li_category` SET slug='mens-fashion',     name_en="Men's Fashion"       WHERE name='男装'       AND level=1;
UPDATE `li_category` SET slug='kids-fashion',     name_en="Kids Fashion"        WHERE name='童装'       AND level=1;
UPDATE `li_category` SET slug='sports',           name_en='Sports & Fitness'    WHERE name='运动'       AND level=1;
UPDATE `li_category` SET slug='skincare',         name_en='Skincare'            WHERE name='面部护理'   AND level=1;
UPDATE `li_category` SET slug='makeup',           name_en='Makeup & Cosmetics'  WHERE name='魅力彩妆'   AND level=1;
UPDATE `li_category` SET slug='perfume',          name_en='Perfume & SPA'       WHERE name='香水SPA'    AND level=1;
UPDATE `li_category` SET slug='womens-bags',      name_en="Women's Bags"        WHERE name='潮流女包'   AND level=1;
UPDATE `li_category` SET slug='mens-bags',        name_en="Men's Bags"          WHERE name='时尚男包'   AND level=1;
UPDATE `li_category` SET slug='snacks',           name_en='Snacks & Sweets'     WHERE name='休闲食品'   AND level=1;
UPDATE `li_category` SET slug='fresh-food',       name_en='Fresh Food'          WHERE name='生鲜食品'   AND level=1;
UPDATE `li_category` SET slug='beverages',        name_en='Beverages'           WHERE name='饮料冲调'   AND level=1;

-- Phase 3: Create AI Template table (adapted: category_id is BIGINT)
CREATE TABLE IF NOT EXISTS `li_category_ai_template` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT   COMMENT '主键',
    `create_by`        VARCHAR(64)   DEFAULT NULL              COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT NOW()             COMMENT '创建时间',
    `update_by`        VARCHAR(64)   DEFAULT NULL              COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    `delete_flag`      TINYINT(1)    DEFAULT 0                 COMMENT '是否删除',
    `category_id`      BIGINT        NOT NULL                  COMMENT '绑定的分类ID',
    `category_slug`    VARCHAR(100)  NOT NULL                  COMMENT '分类slug（冗余，加速查询）',
    `template_version` INT           NOT NULL DEFAULT 1        COMMENT '模版版本号，升级时+1',
    `schema_json`      JSON          NOT NULL                  COMMENT 'JSON Schema定义：required/recommended/optional/properties',
    `ai_hint`          TEXT          DEFAULT NULL              COMMENT '给AI的购买决策提示词',
    `comparison_keys`  VARCHAR(500)  DEFAULT NULL              COMMENT 'AI对比时核心维度，逗号分隔',
    `unit_hints`       JSON          DEFAULT NULL              COMMENT '参数单位映射',
    `is_active`        TINYINT(1)    DEFAULT 1                 COMMENT '是否启用',
    PRIMARY KEY (`id`),
    KEY `idx_category_id`   (`category_id`),
    KEY `idx_category_slug` (`category_slug`),
    KEY `idx_active`        (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类AI参数模版，支持版本进化';

-- Phase 4: Create Goods AI Context snapshot table
CREATE TABLE IF NOT EXISTS `li_goods_ai_context` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT   COMMENT '主键',
    `create_time`      DATETIME      DEFAULT NOW()             COMMENT '生成时间',
    `update_time`      DATETIME      DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    `goods_id`         VARCHAR(64)   NOT NULL                  COMMENT '商品ID',
    `category_id`      BIGINT        NOT NULL                  COMMENT '分类ID',
    `template_version` INT           DEFAULT NULL              COMMENT '生成时使用的模版版本',
    `ai_context`       JSON          NOT NULL                  COMMENT '完整的AI上下文JSON',
    `needs_refresh`    TINYINT(1)    DEFAULT 0                 COMMENT '模版升级后标记需重新生成',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_goods_id`    (`goods_id`),
    KEY `idx_category_id`       (`category_id`),
    KEY `idx_needs_refresh`     (`needs_refresh`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品AI上下文快照，供AI Agent快速读取';

-- Phase 5: Insert AI Template for 电脑整机 (Laptops)
INSERT INTO `li_category_ai_template`
    (`category_id`, `category_slug`, `template_version`, `schema_json`, `ai_hint`, `comparison_keys`, `unit_hints`, `is_active`)
SELECT
    id,
    'laptops',
    1,
    JSON_OBJECT(
        'required',    JSON_ARRAY('brand','model','cpu','ram','storage','screen_size','os'),
        'recommended', JSON_ARRAY('gpu','battery_life','weight','resolution','refresh_rate'),
        'optional',    JSON_ARRAY('thunderbolt','touchscreen','fingerprint','backlit_keyboard'),
        'properties',  JSON_OBJECT(
            'brand',         JSON_OBJECT('type','string','ai_label','品牌'),
            'model',         JSON_OBJECT('type','string','ai_label','型号'),
            'cpu',           JSON_OBJECT('type','string','ai_label','处理器'),
            'ram',           JSON_OBJECT('type','number','unit','GB','values',JSON_ARRAY(8,16,32,64),'ai_label','内存'),
            'storage',       JSON_OBJECT('type','number','unit','GB','ai_label','存储容量'),
            'screen_size',   JSON_OBJECT('type','number','unit','inch','ai_label','屏幕尺寸'),
            'os',            JSON_OBJECT('type','enum','values',JSON_ARRAY('Windows','macOS','Linux','ChromeOS'),'ai_label','操作系统'),
            'gpu',           JSON_OBJECT('type','string','ai_label','独立显卡'),
            'battery_life',  JSON_OBJECT('type','number','unit','h','ai_label','续航时长'),
            'weight',        JSON_OBJECT('type','number','unit','kg','ai_label','重量'),
            'resolution',    JSON_OBJECT('type','string','ai_label','屏幕分辨率'),
            'refresh_rate',  JSON_OBJECT('type','number','unit','Hz','ai_label','刷新率')
        )
    ),
    '笔记本电脑核心决策因素：用途（办公/游戏/创作）、cpu性能、ram、storage、屏幕质量、重量、续航。游戏本重点看gpu。',
    'brand,cpu,ram,storage,screen_size,gpu,weight,battery_life,price_usd',
    JSON_OBJECT('ram','GB','storage','GB','screen_size','inch','battery_life','h','weight','kg','refresh_rate','Hz'),
    1
FROM `li_category` WHERE name='电脑整机' AND level=1 AND delete_flag=0 LIMIT 1;

-- Phase 5b: Insert AI Template for 面部护理 (Skincare)
INSERT INTO `li_category_ai_template`
    (`category_id`, `category_slug`, `template_version`, `schema_json`, `ai_hint`, `comparison_keys`, `unit_hints`, `is_active`)
SELECT
    id,
    'skincare',
    1,
    JSON_OBJECT(
        'required',    JSON_ARRAY('brand','product_type','skin_type','volume','main_ingredients'),
        'recommended', JSON_ARRAY('effect','gender','fragrance_free','cruelty_free','spf'),
        'optional',    JSON_ARRAY('dermatologist_tested','country_of_origin','age_group'),
        'properties',  JSON_OBJECT(
            'brand',          JSON_OBJECT('type','string','ai_label','品牌'),
            'product_type',   JSON_OBJECT('type','enum','values',JSON_ARRAY('cleanser','toner','serum','moisturizer','sunscreen','mask','eye_cream','exfoliator','other'),'ai_label','产品类型'),
            'skin_type',      JSON_OBJECT('type','enum','values',JSON_ARRAY('dry','oily','combination','sensitive','all'),'ai_label','适合肤质'),
            'volume',         JSON_OBJECT('type','number','unit','ml','ai_label','容量'),
            'main_ingredients', JSON_OBJECT('type','string','ai_label','主要成分'),
            'effect',         JSON_OBJECT('type','string','ai_label','主要功效'),
            'spf',            JSON_OBJECT('type','number','ai_label','防晒指数SPF'),
            'fragrance_free', JSON_OBJECT('type','boolean','ai_label','无香料'),
            'cruelty_free',   JSON_OBJECT('type','boolean','ai_label','无动物测试')
        )
    ),
    '护肤品核心决策因素：肤质适配（skin_type）、产品功效（effect）、主要成分、品牌、容量性价比。敏感肌用户额外关注fragrance_free。',
    'brand,product_type,skin_type,effect,main_ingredients,volume,price_usd',
    JSON_OBJECT('volume','ml'),
    1
FROM `li_category` WHERE name='面部护理' AND level=1 AND delete_flag=0 LIMIT 1;

-- Phase 5c: Insert AI Template for 魅力彩妆 (Makeup)
INSERT INTO `li_category_ai_template`
    (`category_id`, `category_slug`, `template_version`, `schema_json`, `ai_hint`, `comparison_keys`, `unit_hints`, `is_active`)
SELECT
    id,
    'makeup',
    1,
    JSON_OBJECT(
        'required',    JSON_ARRAY('brand','product_type','color_family','finish','coverage'),
        'recommended', JSON_ARRAY('skin_type','long_lasting','spf','vegan','cruelty_free'),
        'optional',    JSON_ARRAY('volume','fragrance_free','hypoallergenic'),
        'properties',  JSON_OBJECT(
            'brand',        JSON_OBJECT('type','string','ai_label','品牌'),
            'product_type', JSON_OBJECT('type','enum','values',JSON_ARRAY('foundation','lipstick','eyeshadow','mascara','blush','highlighter','concealer','primer','setting_spray','other'),'ai_label','产品类型'),
            'color_family', JSON_OBJECT('type','string','ai_label','色系'),
            'finish',       JSON_OBJECT('type','enum','values',JSON_ARRAY('matte','satin','dewy','glossy','natural'),'ai_label','妆感'),
            'coverage',     JSON_OBJECT('type','enum','values',JSON_ARRAY('sheer','light','medium','full'),'ai_label','遮瑕度'),
            'skin_type',    JSON_OBJECT('type','enum','values',JSON_ARRAY('dry','oily','combination','sensitive','all'),'ai_label','适合肤质'),
            'long_lasting', JSON_OBJECT('type','boolean','ai_label','持久不脱妆'),
            'vegan',        JSON_OBJECT('type','boolean','ai_label','纯素成分'),
            'cruelty_free', JSON_OBJECT('type','boolean','ai_label','无动物测试')
        )
    ),
    '彩妆核心决策因素：产品类型、品牌、色系、妆感（finish）、遮瑕度、持久性。用户常对比同类产品的色号范围和成分安全性。',
    'brand,product_type,color_family,finish,coverage,long_lasting,price_usd',
    JSON_OBJECT('volume','ml'),
    1
FROM `li_category` WHERE name='魅力彩妆' AND level=1 AND delete_flag=0 LIMIT 1;

-- Phase 5d: Insert AI Template for 运动 (Sports)
INSERT INTO `li_category_ai_template`
    (`category_id`, `category_slug`, `template_version`, `schema_json`, `ai_hint`, `comparison_keys`, `unit_hints`, `is_active`)
SELECT
    id,
    'sports',
    1,
    JSON_OBJECT(
        'required',    JSON_ARRAY('brand','sport_type','gender','material'),
        'recommended', JSON_ARRAY('size_system','weight','waterproof','color','season'),
        'optional',    JSON_ARRAY('uv_protection','breathable','quick_dry'),
        'properties',  JSON_OBJECT(
            'brand',       JSON_OBJECT('type','string','ai_label','品牌'),
            'sport_type',  JSON_OBJECT('type','enum','values',JSON_ARRAY('running','basketball','football','swimming','hiking','gym','cycling','yoga','tennis','other'),'ai_label','运动类型'),
            'gender',      JSON_OBJECT('type','enum','values',JSON_ARRAY('male','female','unisex','kids'),'ai_label','适用性别'),
            'material',    JSON_OBJECT('type','string','ai_label','材质'),
            'weight',      JSON_OBJECT('type','number','unit','g','ai_label','重量'),
            'waterproof',  JSON_OBJECT('type','boolean','ai_label','防水'),
            'breathable',  JSON_OBJECT('type','boolean','ai_label','透气'),
            'quick_dry',   JSON_OBJECT('type','boolean','ai_label','速干'),
            'uv_protection', JSON_OBJECT('type','boolean','ai_label','防紫外线'),
            'season',      JSON_OBJECT('type','enum','values',JSON_ARRAY('spring','summer','autumn','winter','all'),'ai_label','适用季节')
        )
    ),
    '运动用品核心决策因素：运动类型（sport_type）、品牌、适用性别、材质、功能特性（防水/透气/速干）。户外类需关注防水和防UV。',
    'brand,sport_type,gender,waterproof,breathable,weight,price_usd',
    JSON_OBJECT('weight','g'),
    1
FROM `li_category` WHERE name='运动' AND level=1 AND delete_flag=0 LIMIT 1;

-- =============================================================================
-- Verification
-- =============================================================================
SELECT '=== li_category slugs ===' AS info;
SELECT id, name, slug, name_en, level FROM li_category WHERE slug IS NOT NULL ORDER BY level, sort_order;

SELECT '=== li_category_ai_template ===' AS info;
SELECT category_slug, template_version, is_active, comparison_keys FROM li_category_ai_template;

SELECT '=== li_goods_ai_context table ===' AS info;
DESCRIBE li_goods_ai_context;
