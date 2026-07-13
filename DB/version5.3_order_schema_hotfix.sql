-- Lilishop Order Schema Hotfix
-- Purpose: Align production li_order table with current Order entity fields used by scheduled tasks.

DROP PROCEDURE IF EXISTS AddOrderColumnIfMissing;
DELIMITER //
CREATE PROCEDURE AddOrderColumnIfMissing(IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = 'li_order'
        AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE li_order ADD COLUMN ', columnDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL AddOrderColumnIfMissing('seller_remark', 'seller_remark VARCHAR(255) NULL COMMENT ''商家订单备注''');

DROP PROCEDURE IF EXISTS AddOrderColumnIfMissing;
