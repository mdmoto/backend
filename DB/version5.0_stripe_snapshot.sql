-- Lilishop Stripe Payment Snapshot Table
-- Purpose: Store locally cached Stripe payment data for accurate Maocoin issuance.

-- 1. Create table if not exists (including unique index for new tables)
CREATE TABLE IF NOT EXISTS li_stripe_payment_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    order_sn VARCHAR(32) NOT NULL COMMENT '订单编号',
    charge_id VARCHAR(64) COMMENT 'Stripe Charge ID',
    payment_intent_id VARCHAR(64) COMMENT 'Stripe Payment Intent ID',
    balance_transaction_id VARCHAR(64) COMMENT 'Stripe Balance Transaction ID',
    amount_net_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT '真实净收款 (USD)',
    amount_gross_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT '真实总收款 (USD)',
    fee_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000 COMMENT 'Stripe 手续费 (USD)',
    currency VARCHAR(10) DEFAULT 'USD' COMMENT '币种',
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '支付状态: UNCONFIRMED, CONFIRMED, REFUNDED',
    raw_data TEXT COMMENT 'Stripe 原始数据快照 (JSON)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_order_sn (order_sn),
    INDEX idx_payment_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stripe 支付快照表';

-- 2. Maintenance for existing tables: Cleanup duplicates before adding index
-- P0 Fix: Handle cases where update_time is identical by using ID as tie-break
DELETE s1
FROM li_stripe_payment_snapshot s1
JOIN li_stripe_payment_snapshot s2
  ON s1.order_sn = s2.order_sn
 AND (
      s1.update_time < s2.update_time
   OR (s1.update_time = s2.update_time AND s1.id < s2.id)
 );

-- 3. Maintenance: Fix existing NULL values before applying NOT NULL constraint
-- P0 Fix: Prevent "Data truncated" error when modifying columns to NOT NULL
UPDATE li_stripe_payment_snapshot SET amount_net_usd = 0.00000000 WHERE amount_net_usd IS NULL;
UPDATE li_stripe_payment_snapshot SET amount_gross_usd = 0.00000000 WHERE amount_gross_usd IS NULL;
UPDATE li_stripe_payment_snapshot SET fee_usd = 0.00000000 WHERE fee_usd IS NULL;

-- 4. Maintenance: Ensure the UNIQUE INDEX exists on order_sn (Idempotent)
-- We use a stored procedure to safely apply the index to an existing table.
DROP PROCEDURE IF EXISTS AddUniqueIndexToStripeSnapshot;
DELIMITER //
CREATE PROCEDURE AddUniqueIndexToStripeSnapshot()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
        AND table_name = 'li_stripe_payment_snapshot' 
        AND index_name = 'uk_order_sn'
    ) THEN
        ALTER TABLE li_stripe_payment_snapshot ADD UNIQUE INDEX uk_order_sn (order_sn);
    END IF;
END //
DELIMITER ;
CALL AddUniqueIndexToStripeSnapshot();
DROP PROCEDURE AddUniqueIndexToStripeSnapshot;

-- 5. Maintenance: Ensure financial columns are NOT NULL and have default values
ALTER TABLE li_stripe_payment_snapshot MODIFY amount_net_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000;
ALTER TABLE li_stripe_payment_snapshot MODIFY amount_gross_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000;
ALTER TABLE li_stripe_payment_snapshot MODIFY fee_usd DECIMAL(18, 8) NOT NULL DEFAULT 0.00000000;
