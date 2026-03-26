-- Add address fields for international routing
ALTER TABLE `li_member_address` 
    ADD COLUMN `province` varchar(100) DEFAULT NULL COMMENT '州/省 (国际地址使用)' AFTER `postal_code`,
    ADD COLUMN `city` varchar(100) DEFAULT NULL COMMENT '城市 (国际地址使用)' AFTER `province`;

-- Add product dimensions for volume weight calculation
ALTER TABLE `li_goods` 
    ADD COLUMN `goods_length` double(10,2) DEFAULT NULL COMMENT '长度 (cm)',
    ADD COLUMN `goods_width` double(10,2) DEFAULT NULL COMMENT '宽度 (cm)',
    ADD COLUMN `goods_height` double(10,2) DEFAULT NULL COMMENT '高度 (cm)';

ALTER TABLE `li_goods_sku` 
    ADD COLUMN `goods_length` double(10,2) DEFAULT NULL COMMENT '长度 (cm)',
    ADD COLUMN `goods_width` double(10,2) DEFAULT NULL COMMENT '宽度 (cm)',
    ADD COLUMN `goods_height` double(10,2) DEFAULT NULL COMMENT '高度 (cm)';
