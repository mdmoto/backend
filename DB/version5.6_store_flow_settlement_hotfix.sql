-- Maollar production hotfix: restore settlement columns required by StoreFlowMapper.
-- Safe to run more than once on MySQL 8.0.
-- Production observation on 2026-07-13: li_store_flow is empty and all four columns are absent.

DROP PROCEDURE IF EXISTS AddStoreFlowColumnIfMissing;

DELIMITER //
CREATE PROCEDURE AddStoreFlowColumnIfMissing(
    IN columnName VARCHAR(64),
    IN columnDefinition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'li_store_flow'
          AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE li_store_flow ADD COLUMN ',
            columnDefinition
        );
        PREPARE statementToRun FROM @ddl;
        EXECUTE statementToRun;
        DEALLOCATE PREPARE statementToRun;
    END IF;
END //
DELIMITER ;

CALL AddStoreFlowColumnIfMissing(
    'bill_time',
    '`bill_time` datetime(6) DEFAULT NULL COMMENT ''结算时间'''
);
CALL AddStoreFlowColumnIfMissing(
    'full_refund',
    '`full_refund` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否全部退款'''
);
CALL AddStoreFlowColumnIfMissing(
    'profit_sharing_status',
    '`profit_sharing_status` varchar(64) DEFAULT NULL COMMENT ''分账状态'''
);
CALL AddStoreFlowColumnIfMissing(
    'profit_sharing',
    '`profit_sharing` text DEFAULT NULL COMMENT ''实际分账金额DTO'''
);

DROP PROCEDURE IF EXISTS AddStoreFlowColumnIfMissing;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'li_store_flow'
  AND column_name IN (
      'bill_time',
      'full_refund',
      'profit_sharing_status',
      'profit_sharing'
  )
ORDER BY ordinal_position;

-- Emergency rollback (only before new store-flow rows are created):
-- ALTER TABLE li_store_flow
--   DROP COLUMN profit_sharing,
--   DROP COLUMN profit_sharing_status,
--   DROP COLUMN full_refund,
--   DROP COLUMN bill_time;
