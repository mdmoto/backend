-- Lilishop Maocoin Security & Audit Upgrade DDL
-- Target: li_member_points_history
-- Purpose: Support for Merkle Tree audit reproducibility, strict fiscal idempotency, and high-precision reserve fund tracking.
--
-- [DEPLOYMENT SOP - READ CAREFULLY]
-- 1. Columns and indexes are created through stored procedures for MySQL 8 compatibility.
-- 2. Safe to run more than once on the same database.

-- 1. Audit & Idempotency Columns
DROP PROCEDURE IF EXISTS AddMaocoinColumn;
DELIMITER //
CREATE PROCEDURE AddMaocoinColumn(IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = 'li_member_points_history'
        AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE li_member_points_history ADD COLUMN ', columnDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddMaocoinColumn('fund_reserve', 'fund_reserve DECIMAL(18, 8) DEFAULT 0.00000000 COMMENT ''基金会应拨备金 (USD)''');
CALL AddMaocoinColumn('biz_id', 'biz_id VARCHAR(128) DEFAULT NULL COMMENT ''业务关联ID (前缀_订单ID/售后单ID/申请ID)''');
CALL AddMaocoinColumn('merkle_timestamp', 'merkle_timestamp BIGINT DEFAULT NULL COMMENT ''默克尔树计算时间戳 (用于复算哈希)''');
CALL AddMaocoinColumn('merkle_hash', 'merkle_hash VARCHAR(64) DEFAULT NULL COMMENT ''默克尔树叶子哈希''');
CALL AddMaocoinColumn('is_settled', 'is_settled TINYINT(1) DEFAULT 0 COMMENT ''是否已结算拨付''');
CALL AddMaocoinColumn('is_confirmed', 'is_confirmed TINYINT(1) DEFAULT 0 COMMENT ''是否已确权 (DApp 兑换)''');
DROP PROCEDURE IF EXISTS AddMaocoinColumn;

-- 2. Precision Upgrade for Reserve Fund (USD)
ALTER TABLE li_member_points_history MODIFY COLUMN fund_reserve DECIMAL(18, 8) DEFAULT 0.00000000 COMMENT '基金会应拨备金 (USD)';

-- 3. Fiscal Idempotency Index
-- This index prevents double-issuance at the database level.
DROP PROCEDURE IF EXISTS AddMaocoinIndex;
DELIMITER //
CREATE PROCEDURE AddMaocoinIndex(IN indexName VARCHAR(64), IN indexColumns VARCHAR(255), IN uniquePrefix VARCHAR(16))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
        AND table_name = 'li_member_points_history'
        AND index_name = indexName
    ) THEN
        SET @ddl = CONCAT('CREATE ', uniquePrefix, ' INDEX ', indexName, ' ON li_member_points_history (', indexColumns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddMaocoinIndex('uk_member_biz_type', 'member_id, biz_id, point_type', 'UNIQUE');

-- 4. Audit Search Index
CALL AddMaocoinIndex('idx_merkle_hash', 'merkle_hash', '');
DROP PROCEDURE IF EXISTS AddMaocoinIndex;
