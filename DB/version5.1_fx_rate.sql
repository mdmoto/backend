-- Lilishop FX Rate Snapshot Table
-- Purpose: Store display-only exchange rates for frontend currency switching.
-- This table is decoupled from fiscal/accounting logic.

CREATE TABLE IF NOT EXISTS li_fx_rate_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    base_currency VARCHAR(10) NOT NULL DEFAULT 'USD' COMMENT '基准币种',
    quote_currency VARCHAR(10) NOT NULL COMMENT '目标币种',
    exchange_rate DECIMAL(18, 8) NOT NULL COMMENT '汇率 (1 base = X quote)',
    as_of_ts BIGINT NOT NULL COMMENT '汇率快照时间戳',
    source VARCHAR(32) DEFAULT 'OER' COMMENT '数据来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX uk_base_quote (base_currency, quote_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展示用汇率快照表';
