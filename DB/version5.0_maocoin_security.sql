-- Lilishop Maocoin Security & Audit Upgrade DDL
-- Target: li_member_points_history
-- Purpose: Support for Merkle Tree audit reproducibility, strict fiscal idempotency, and high-precision reserve fund tracking.

-- 1. Precision Upgrade for Reserve Fund (USD)
ALTER TABLE li_member_points_history MODIFY COLUMN fund_reserve DECIMAL(18, 8) DEFAULT 0.00000000 COMMENT '基金会应拨备金 (USD)';

-- 2. Audit & Idempotency Columns
ALTER TABLE li_member_points_history ADD COLUMN IF NOT EXISTS biz_id VARCHAR(128) DEFAULT NULL COMMENT '业务关联ID (前缀_订单ID/售后单ID/申请ID)';
ALTER TABLE li_member_points_history ADD COLUMN IF NOT EXISTS merkle_timestamp BIGINT DEFAULT NULL COMMENT '默克尔树计算时间戳 (用于复算哈希)';
ALTER TABLE li_member_points_history ADD COLUMN IF NOT EXISTS is_settled TINYINT(1) DEFAULT 0 COMMENT '是否已结算拨付';
ALTER TABLE li_member_points_history ADD COLUMN IF NOT EXISTS is_confirmed TINYINT(1) DEFAULT 0 COMMENT '是否已确权 (DApp 兑换)';

-- 3. Fiscal Idempotency Index
-- This index prevents double-issuance at the database level.
-- Includes point_type because a single order might attract both a REWARD_MEOW and a REWARD_GIFT.
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_biz_type ON li_member_points_history (member_id, biz_id, point_type);

-- 4. Audit Search Index
CREATE INDEX IF NOT EXISTS idx_merkle_hash ON li_member_points_history (merkle_hash);
