-- Lilishop Mao Withdrawal Schema Hotfix
-- Target: li_mao_withdrawal_log
-- Purpose: Align the production withdrawal table with MaoWithdrawalLog.java.
--
-- [DEPLOYMENT SOP]
-- 1. Safe to run repeatedly.
-- 2. Uses stored procedures for MySQL 8 compatibility.
-- 3. Does not remove legacy columns such as amount/status/sn.

CREATE TABLE IF NOT EXISTS li_mao_withdrawal_log (
    id VARCHAR(32) NOT NULL COMMENT 'ID',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag BIT(1) DEFAULT b'0' COMMENT '删除标志',
    member_id VARCHAR(64) DEFAULT NULL COMMENT '会员ID',
    points BIGINT DEFAULT NULL COMMENT '提现扣除的喵币数量',
    mao_issued_amount DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '实际发放的 $MAO 数量',
    solana_wallet_address VARCHAR(128) DEFAULT NULL COMMENT '接收地址 (Solana)',
    mao_issue_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '状态',
    mao_tx_hash VARCHAR(128) DEFAULT NULL COMMENT 'Solana 交易哈希',
    error_log TEXT COMMENT '错误日志',
    PRIMARY KEY (id),
    INDEX idx_mao_withdrawal_member (member_id),
    INDEX idx_mao_withdrawal_status_time (mao_issue_status, create_time),
    INDEX idx_mao_withdrawal_tx_hash (mao_tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='$MAO 提现记录';

DROP PROCEDURE IF EXISTS AddMaoWithdrawalColumn;
DELIMITER //
CREATE PROCEDURE AddMaoWithdrawalColumn(IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = 'li_mao_withdrawal_log'
        AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE li_mao_withdrawal_log ADD COLUMN ', columnDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddMaoWithdrawalColumn('points', 'points BIGINT DEFAULT NULL COMMENT ''提现扣除的喵币数量''');
CALL AddMaoWithdrawalColumn('mao_issued_amount', 'mao_issued_amount DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT ''实际发放的 $MAO 数量''');
CALL AddMaoWithdrawalColumn('solana_wallet_address', 'solana_wallet_address VARCHAR(128) DEFAULT NULL COMMENT ''接收地址 (Solana)''');
CALL AddMaoWithdrawalColumn('mao_issue_status', 'mao_issue_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''状态''');
CALL AddMaoWithdrawalColumn('mao_tx_hash', 'mao_tx_hash VARCHAR(128) DEFAULT NULL COMMENT ''Solana 交易哈希''');
CALL AddMaoWithdrawalColumn('error_log', 'error_log TEXT COMMENT ''错误日志''');
DROP PROCEDURE IF EXISTS AddMaoWithdrawalColumn;

UPDATE li_mao_withdrawal_log SET mao_issued_amount = 0.000000 WHERE mao_issued_amount IS NULL;
UPDATE li_mao_withdrawal_log SET mao_issue_status = 'NONE' WHERE mao_issue_status IS NULL OR mao_issue_status = '';

ALTER TABLE li_mao_withdrawal_log MODIFY COLUMN mao_issued_amount DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '实际发放的 $MAO 数量';
ALTER TABLE li_mao_withdrawal_log MODIFY COLUMN mao_issue_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '状态';

DROP PROCEDURE IF EXISTS AddMaoWithdrawalIndex;
DELIMITER //
CREATE PROCEDURE AddMaoWithdrawalIndex(IN indexName VARCHAR(64), IN indexColumns VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
        AND table_name = 'li_mao_withdrawal_log'
        AND index_name = indexName
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX ', indexName, ' ON li_mao_withdrawal_log (', indexColumns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddMaoWithdrawalIndex('idx_mao_withdrawal_member', 'member_id');
CALL AddMaoWithdrawalIndex('idx_mao_withdrawal_status_time', 'mao_issue_status, create_time');
CALL AddMaoWithdrawalIndex('idx_mao_withdrawal_tx_hash', 'mao_tx_hash');
DROP PROCEDURE IF EXISTS AddMaoWithdrawalIndex;
