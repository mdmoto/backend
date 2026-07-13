-- Lilishop Maocoin Public Audit Anchor
-- Purpose: Persist immutable daily Merkle root snapshots for public verification.

CREATE TABLE IF NOT EXISTS `li_mao_audit_anchor` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `delete_flag` tinyint(1) DEFAULT 0 COMMENT '删除标志',
  `anchor_key` varchar(64) NOT NULL COMMENT '锚定批次，如 MAO-LEDGER-2026-05-29',
  `anchor_date` varchar(10) NOT NULL COMMENT '锚定日期 UTC yyyy-MM-dd',
  `anchor_timezone` varchar(32) NOT NULL DEFAULT 'UTC' COMMENT '锚定时区',
  `anchored_until` datetime NOT NULL COMMENT '账本统计截止时间',
  `root_scope` varchar(64) NOT NULL COMMENT 'Root 覆盖范围',
  `merkle_root` varchar(64) NOT NULL COMMENT '账本 Merkle Root',
  `ledger_hash` varchar(64) NOT NULL COMMENT '语义账本 Hash',
  `anchor_payload_hash` varchar(64) DEFAULT NULL COMMENT '公开锚定 Payload Hash',
  `record_count` bigint NOT NULL DEFAULT 0 COMMENT '纳入记录数',
  `total_increase_points` bigint NOT NULL DEFAULT 0 COMMENT '累计增加喵币',
  `total_reduce_points` bigint NOT NULL DEFAULT 0 COMMENT '累计扣减喵币',
  `total_fund_reserve` decimal(24,8) NOT NULL DEFAULT 0.00000000 COMMENT '累计基金拨备 USD',
  `first_history_id` varchar(64) DEFAULT NULL COMMENT '首条积分流水 ID',
  `last_history_id` varchar(64) DEFAULT NULL COMMENT '末条积分流水 ID',
  `anchor_status` varchar(32) NOT NULL DEFAULT 'LOCAL_CREATED' COMMENT '锚定状态',
  `anchor_target` varchar(32) DEFAULT NULL COMMENT '外部锚定目标',
  `anchor_tx_hash` varchar(128) DEFAULT NULL COMMENT '外部锚定交易/对象 Hash',
  `error_log` varchar(1024) DEFAULT NULL COMMENT '外部锚定失败原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_anchor_key` (`anchor_key`),
  KEY `idx_anchor_date` (`anchor_date`),
  KEY `idx_anchor_status` (`anchor_status`),
  KEY `idx_anchor_tx_hash` (`anchor_tx_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MAO 猫币账本公开审计锚';
