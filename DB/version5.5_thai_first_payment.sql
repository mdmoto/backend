-- Thai-first payment migration
-- Purpose: run collection through Thai company/account first, while keeping records ready for SG migration.

DROP PROCEDURE IF EXISTS AddStripeThaiFirstColumn;
DELIMITER //
CREATE PROCEDURE AddStripeThaiFirstColumn(IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'li_stripe_payment_snapshot'
          AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE li_stripe_payment_snapshot ADD COLUMN ', columnDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddStripeThaiFirstColumn('merchant_entity', 'merchant_entity VARCHAR(32) DEFAULT ''TH_COMPANY'' COMMENT ''收单主体: TH_COMPANY, SG_COMPANY''');
CALL AddStripeThaiFirstColumn('acquirer_account', 'acquirer_account VARCHAR(64) DEFAULT ''stripe_th'' COMMENT ''收单账户标识''');
CALL AddStripeThaiFirstColumn('settlement_entity', 'settlement_entity VARCHAR(64) DEFAULT ''TH_BANK'' COMMENT ''结算主体或账户''');
CALL AddStripeThaiFirstColumn('presentment_currency', 'presentment_currency VARCHAR(10) DEFAULT ''THB'' COMMENT ''展示给客户的币种''');
CALL AddStripeThaiFirstColumn('amount_presentment', 'amount_presentment DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT ''展示给客户的金额''');
CALL AddStripeThaiFirstColumn('settlement_currency', 'settlement_currency VARCHAR(10) DEFAULT ''THB'' COMMENT ''结算币种''');
CALL AddStripeThaiFirstColumn('payer_wallet', 'payer_wallet VARCHAR(32) DEFAULT ''UNKNOWN'' COMMENT ''付款钱包/方式''');
CALL AddStripeThaiFirstColumn('supplier_payout_status', 'supplier_payout_status VARCHAR(32) DEFAULT ''PENDING'' COMMENT ''中国供应商付款状态''');

DROP PROCEDURE IF EXISTS AddStripeThaiFirstColumn;

DROP PROCEDURE IF EXISTS AddStripeThaiFirstIndex;
DELIMITER //
CREATE PROCEDURE AddStripeThaiFirstIndex(IN indexName VARCHAR(64), IN indexDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'li_stripe_payment_snapshot'
          AND index_name = indexName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE li_stripe_payment_snapshot ADD INDEX ', indexName, ' ', indexDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddStripeThaiFirstIndex('idx_stripe_merchant_entity', '(merchant_entity)');
CALL AddStripeThaiFirstIndex('idx_stripe_supplier_payout_status', '(supplier_payout_status)');

DROP PROCEDURE IF EXISTS AddStripeThaiFirstIndex;

CREATE TABLE IF NOT EXISTS li_supplier_payable (
    id VARCHAR(32) PRIMARY KEY,
    order_sn VARCHAR(32) NOT NULL COMMENT '订单编号',
    supplier_id VARCHAR(32) COMMENT '供应商ID',
    supplier_name VARCHAR(128) COMMENT '供应商名称',
    merchant_entity VARCHAR(32) NOT NULL DEFAULT 'TH_COMPANY' COMMENT '采购付款主体',
    source_payment_provider VARCHAR(32) COMMENT '来源收单方式: STRIPE, THAI_QR',
    payable_amount DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT '应付供应商金额',
    payable_currency VARCHAR(10) NOT NULL DEFAULT 'CNY' COMMENT '应付币种',
    payout_provider VARCHAR(32) COMMENT '付款服务商: AIRWALLEX, WISE, BANK',
    payout_batch_sn VARCHAR(64) COMMENT '付款批次号',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, READY, PROCESSING, PAID, FAILED',
    remark VARCHAR(512) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_supplier_payable_order_sn (order_sn),
    INDEX idx_supplier_payable_status (status),
    INDEX idx_supplier_payable_batch_sn (payout_batch_sn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中国供应商应付账款';

CREATE TABLE IF NOT EXISTS li_supplier_payout_batch (
    id VARCHAR(32) PRIMARY KEY,
    batch_sn VARCHAR(64) NOT NULL COMMENT '付款批次号',
    merchant_entity VARCHAR(32) NOT NULL DEFAULT 'TH_COMPANY' COMMENT '付款主体',
    payout_provider VARCHAR(32) NOT NULL COMMENT '付款服务商: AIRWALLEX, WISE, BANK',
    payout_currency VARCHAR(10) NOT NULL DEFAULT 'CNY' COMMENT '付款币种',
    payout_amount DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT '付款总额',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, PAID, FAILED',
    provider_reference_no VARCHAR(128) COMMENT '服务商流水号',
    raw_data TEXT COMMENT '服务商原始数据',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_supplier_payout_batch_sn (batch_sn),
    INDEX idx_supplier_payout_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中国供应商付款批次';

INSERT INTO li_setting (id, create_by, create_time, delete_flag, update_by, update_time, setting_value)
VALUES (
    'STRIPE_PAYMENT',
    'admin',
    NOW(),
    b'0',
    'admin',
    NOW(),
    '{"secretKey":"","webhookSecret":"","successUrl":"","cancelUrl":"","currency":"THB","settlementCurrency":"THB","merchantEntity":"TH_COMPANY","acquirerAccount":"stripe_th","settlementEntity":"TH_BANK","paymentMethodTypes":["card","promptpay"]}'
)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO li_setting (id, create_by, create_time, delete_flag, update_by, update_time, setting_value)
VALUES (
    'THAI_QR_PAYMENT',
    'admin',
    NOW(),
    b'0',
    'admin',
    NOW(),
    '{"enabled":false,"qrPayload":"","qrCodeUrl":"","merchantName":"","providerName":"Thai QR","currency":"THB","merchantEntity":"TH_COMPANY","acquirerAccount":"thai_bank_qr","settlementEntity":"TH_BANK","instructions":"Please scan the Thai QR / PromptPay code and include the order number as reference."}'
)
ON DUPLICATE KEY UPDATE id = id;
