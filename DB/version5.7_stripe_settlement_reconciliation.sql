-- Stripe settlement reconciliation audit fields.
-- Safe to run repeatedly after version5.0_stripe_snapshot.sql.

DROP PROCEDURE IF EXISTS AddStripeReconciliationColumn;
DELIMITER //
CREATE PROCEDURE AddStripeReconciliationColumn(IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
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

CALL AddStripeReconciliationColumn('amount_gross_settlement',
    'amount_gross_settlement DECIMAL(18,8) NULL COMMENT ''Stripe结算币种总额''');
CALL AddStripeReconciliationColumn('fee_settlement',
    'fee_settlement DECIMAL(18,8) NULL COMMENT ''Stripe结算币种手续费''');
CALL AddStripeReconciliationColumn('amount_net_settlement',
    'amount_net_settlement DECIMAL(18,8) NULL COMMENT ''Stripe结算币种净额''');
CALL AddStripeReconciliationColumn('usd_conversion_rate',
    'usd_conversion_rate DECIMAL(20,12) NULL COMMENT ''每单位结算币种对应USD''');
CALL AddStripeReconciliationColumn('balance_transaction_status',
    'balance_transaction_status VARCHAR(32) NULL COMMENT ''Stripe余额交易状态''');
CALL AddStripeReconciliationColumn('funds_available_at',
    'funds_available_at DATETIME NULL COMMENT ''Stripe预计资金可用时间''');
CALL AddStripeReconciliationColumn('reconciled_at',
    'reconciled_at DATETIME NULL COMMENT ''Stripe财务对账完成时间''');

DROP PROCEDURE IF EXISTS AddStripeReconciliationColumn;
