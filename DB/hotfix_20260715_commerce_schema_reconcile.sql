-- Maollar commerce schema reconciliation
-- Purpose: make the production schema match the fields used by registration,
-- checkout, fulfilment, logistics, after-sale and distribution code.
-- MySQL compatibility: 8.0.30 (does not use ADD COLUMN IF NOT EXISTS).
-- Safe to run repeatedly. This file changes schema only; it does not seed store,
-- address, carrier credential or other business data.

DROP PROCEDURE IF EXISTS AddColumnIfMissing;

DELIMITER //
CREATE PROCEDURE AddColumnIfMissing(
    IN tableName VARCHAR(64),
    IN columnName VARCHAR(64),
    IN columnDefinition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = tableName
          AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', REPLACE(tableName, '`', '``'),
            '` ADD COLUMN ', columnDefinition
        );
        PREPARE statementToRun FROM @ddl;
        EXECUTE statementToRun;
        DEALLOCATE PREPARE statementToRun;
    END IF;
END //
DELIMITER ;

-- Category fields required by current goods/detail code.
CALL AddColumnIfMissing('li_category', 'slug',
    '`slug` varchar(100) DEFAULT NULL COMMENT ''英文标识符'' AFTER `name`');
CALL AddColumnIfMissing('li_category', 'name_en',
    '`name_en` varchar(100) DEFAULT NULL COMMENT ''英文分类名称'' AFTER `slug`');
CALL AddColumnIfMissing('li_category', 'ai_description',
    '`ai_description` varchar(500) DEFAULT NULL COMMENT ''AI检索描述'' AFTER `name_en`');

-- Order fields required by list/detail, fulfilment and refund statistics.
CALL AddColumnIfMissing('li_order_item', 'deliver_number',
    '`deliver_number` int DEFAULT NULL COMMENT ''发货数量''');
CALL AddColumnIfMissing('li_order_item', 'is_refund',
    '`is_refund` varchar(255) DEFAULT NULL COMMENT ''是否退款''');
CALL AddColumnIfMissing('li_order_item', 'refund_price',
    '`refund_price` decimal(10,2) DEFAULT NULL COMMENT ''退款金额''');

-- Freight and electronic-waybill fields used by checkout and seller delivery.
CALL AddColumnIfMissing('li_freight_template', 'four_px_switch',
    '`four_px_switch` bit(1) DEFAULT b''0'' COMMENT ''是否启用4PX API国际计费''');
CALL AddColumnIfMissing('li_store_logistics', 'partner_name',
    '`partner_name` varchar(255) DEFAULT NULL COMMENT ''电子面单客户账户名称''');

-- Distribution cash fields from the upstream 4.3 -> 4.4 migration. They are
-- nullable so adding them does not alter existing payout records.
CALL AddColumnIfMissing('li_distribution_cash', 'name',
    '`name` varchar(255) DEFAULT NULL COMMENT ''会员姓名''');
CALL AddColumnIfMissing('li_distribution_cash', 'id_number',
    '`id_number` varchar(255) DEFAULT NULL COMMENT ''身份证号''');
CALL AddColumnIfMissing('li_distribution_cash', 'settlement_bank_account_name',
    '`settlement_bank_account_name` varchar(255) DEFAULT NULL COMMENT ''结算银行开户行名称''');
CALL AddColumnIfMissing('li_distribution_cash', 'settlement_bank_account_num',
    '`settlement_bank_account_num` varchar(255) DEFAULT NULL COMMENT ''结算银行开户账号''');
CALL AddColumnIfMissing('li_distribution_cash', 'settlement_bank_branch_name',
    '`settlement_bank_branch_name` varchar(255) DEFAULT NULL COMMENT ''结算银行开户支行名称''');

DROP PROCEDURE IF EXISTS AddColumnIfMissing;

-- Package tables used by getPackage/getTracesList and partial delivery.
CREATE TABLE IF NOT EXISTS `li_order_package` (
    `id` bigint NOT NULL COMMENT 'ID',
    `create_by` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '创建者',
    `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
    `delete_flag` bit(1) DEFAULT NULL COMMENT '是否删除',
    `update_by` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '修改者',
    `update_time` datetime(6) DEFAULT NULL COMMENT '修改时间',
    `package_no` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '包裹单号',
    `order_sn` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单编号',
    `logistics_no` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '发货单号',
    `logistics_code` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '物流公司CODE',
    `logistics_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '物流公司名称',
    `consignee_mobile` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '收件人手机',
    `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '状态',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `li_order_package_item` (
    `id` bigint NOT NULL COMMENT 'ID',
    `create_by` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '创建者',
    `create_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
    `delete_flag` bit(1) DEFAULT NULL COMMENT '是否删除',
    `update_by` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '修改者',
    `update_time` datetime(6) DEFAULT NULL COMMENT '修改时间',
    `package_no` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '包裹单号',
    `order_sn` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单编号',
    `order_item_sn` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '子订单编号',
    `deliver_number` int DEFAULT NULL COMMENT '发货数量',
    `logistics_time` datetime(6) DEFAULT NULL COMMENT '发货时间',
    `goods_name` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
    `thumbnail` varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin ROW_FORMAT=DYNAMIC;

-- Verification: the first query must return zero rows. The second query exposes
-- product/store referential gaps for an operator to repair with verified data.
WITH expected(table_name, column_name) AS (
    SELECT 'li_category', 'slug'
    UNION ALL SELECT 'li_category', 'name_en'
    UNION ALL SELECT 'li_category', 'ai_description'
    UNION ALL SELECT 'li_order_item', 'deliver_number'
    UNION ALL SELECT 'li_order_item', 'is_refund'
    UNION ALL SELECT 'li_order_item', 'refund_price'
    UNION ALL SELECT 'li_freight_template', 'four_px_switch'
    UNION ALL SELECT 'li_store_logistics', 'partner_name'
    UNION ALL SELECT 'li_distribution_cash', 'name'
    UNION ALL SELECT 'li_distribution_cash', 'id_number'
    UNION ALL SELECT 'li_distribution_cash', 'settlement_bank_account_name'
    UNION ALL SELECT 'li_distribution_cash', 'settlement_bank_account_num'
    UNION ALL SELECT 'li_distribution_cash', 'settlement_bank_branch_name'
)
SELECT expected.table_name, expected.column_name
FROM expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

SELECT goods.store_id, COUNT(*) AS goods_count
FROM li_goods goods
LEFT JOIN li_store store ON store.id = goods.store_id
WHERE goods.delete_flag = b'0'
  AND store.id IS NULL
GROUP BY goods.store_id;
