/**
 * 地址国际化：会员收货地址增加国家码与邮编
 *
 * 注意：不同 MySQL 版本对 "ADD COLUMN IF NOT EXISTS" 支持不一致，这里使用通用语法。
 */

ALTER TABLE `li_member_address`
    ADD COLUMN `country_code` varchar(2) DEFAULT 'CN' COMMENT 'ISO 3166-1 alpha-2 国家码 (CN/US/JP...)' AFTER `mobile`,
    ADD COLUMN `postal_code` varchar(32) DEFAULT NULL COMMENT '邮编/邮政编码' AFTER `country_code`;

UPDATE `li_member_address`
SET `country_code` = 'CN'
WHERE `country_code` IS NULL OR `country_code` = '';

