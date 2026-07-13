-- Lilishop Goods & Order USD Base Field
-- Purpose: Add a USD baseline price to facilitate frontend display-currency switching without real-time fiscal risk.

DROP PROCEDURE IF EXISTS AddColumnIfMissing;
DELIMITER //
CREATE PROCEDURE AddColumnIfMissing(IN tableName VARCHAR(64), IN columnName VARCHAR(64), IN columnDefinition VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = tableName
        AND column_name = columnName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tableName, ' ADD COLUMN ', columnDefinition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- Update Goods Table
CALL AddColumnIfMissing('li_goods', 'price_usd', 'price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT ''USD 基准价''');
CALL AddColumnIfMissing('li_goods_sku', 'price_usd', 'price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT ''USD 基准价''');

-- Update Order Table (for historical record)
CALL AddColumnIfMissing('li_order', 'total_price_usd', 'total_price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT ''订单总额 USD 基准价''');
CALL AddColumnIfMissing('li_order_item', 'price_usd', 'price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT ''商品单价 USD 基准价''');

DROP PROCEDURE IF EXISTS AddColumnIfMissing;
