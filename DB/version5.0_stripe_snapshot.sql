-- Lilishop Stripe Payment Snapshot Table
-- Purpose: Store locally cached Stripe payment data for accurate Maocoin issuance.

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
