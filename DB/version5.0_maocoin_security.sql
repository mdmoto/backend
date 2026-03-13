-- Lilishop Maocoin Security & Audit Upgrade DDL
-- Target: li_member_points_history
-- Purpose: Support for Merkle Tree audit reproducibility, strict fiscal idempotency, and high-precision reserve fund tracking.
--
-- [DEPLOYMENT SOP - READ CAREFULLY]
-- 1. This script is NOT idempotent (IF NOT EXISTS is not supported for ALTER ADD/CREATE INDEX in standard MySQL 8.0).
-- 2. DO NOT run this script more than once on the same database.
-- 3. BEFORE RUNNING: Verify if columns (biz_id, merkle_timestamp, etc.) already exist via:
--    SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_NAME = 'li_member_points_history';
-- 4. BEFORE RUNNING: Verify if index (uk_member_biz_type) already exists via:
--    SHOW INDEX FROM li_member_points_history;

-- 1. Precision Upgrade for Reserve Fund (USD)
ALTER TABLE li_member_points_history MODIFY COLUMN fund_reserve DECIMAL(18, 8) DEFAULT 0.00000000 COMMENT '基金会应拨备金 (USD)';

-- 2. Audit & Idempotency Columns
-- Standard MySQL (8.0+) does not support IF NOT EXISTS in ALTER TABLE ADD COLUMN.
-- This script assumes a one-time execution against the target database.
ALTER TABLE li_member_points_history 
  ADD COLUMN biz_id VARCHAR(128) DEFAULT NULL COMMENT '业务关联ID (前缀_订单ID/售后单ID/申请ID)',
  ADD COLUMN merkle_timestamp BIGINT DEFAULT NULL COMMENT '默克尔树计算时间戳 (用于复算哈希)',
  ADD COLUMN is_settled TINYINT(1) DEFAULT 0 COMMENT '是否已结算拨付',
  ADD COLUMN is_confirmed TINYINT(1) DEFAULT 0 COMMENT '是否已确权 (DApp 兑换)';

-- 3. Fiscal Idempotency Index
-- This index prevents double-issuance at the database level.
CREATE UNIQUE INDEX uk_member_biz_type ON li_member_points_history (member_id, biz_id, point_type);

-- 4. Audit Search Index
CREATE INDEX idx_merkle_hash ON li_member_points_history (merkle_hash);
