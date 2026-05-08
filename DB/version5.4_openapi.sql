CREATE TABLE `li_open_api_key` (
  `id` varchar(255) NOT NULL COMMENT 'ID',
  `create_by` varchar(255) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(255) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `delete_flag` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `member_id` varchar(255) NOT NULL COMMENT '关联会员ID',
  `api_key` varchar(255) NOT NULL COMMENT 'API Key',
  `api_secret` varchar(255) NOT NULL COMMENT 'API Secret',
  `permissions` varchar(1024) NOT NULL COMMENT '权限范围',
  `status` varchar(20) DEFAULT 'OPEN' COMMENT '状态 OPEN,CLOSE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Open API 密钥表';
