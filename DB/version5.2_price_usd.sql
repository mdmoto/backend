-- Lilishop Goods & Order USD Base Field
-- Purpose: Add a USD baseline price to facilitate frontend display-currency switching without real-time fiscal risk.

-- Update Goods Table
ALTER TABLE li_goods ADD COLUMN price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT 'USD 基准价';
ALTER TABLE li_goods_sku ADD COLUMN price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT 'USD 基准价';

-- Update Order Table (for historical record)
ALTER TABLE li_order ADD COLUMN total_price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT '订单总额 USD 基准价';
ALTER TABLE li_order_item ADD COLUMN price_usd DECIMAL(18, 2) DEFAULT 0.00 COMMENT '商品单价 USD 基准价';
