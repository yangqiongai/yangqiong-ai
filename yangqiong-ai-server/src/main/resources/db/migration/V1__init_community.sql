-- =============================================================
-- 社区版 V1 初始化脚本（yangqiong-ai-community）
-- 来源：从社区版数据库实际结构采集导出（DDL + 种子数据 + 演示数据）
-- 说明：结构仅 CREATE TABLE IF NOT EXISTS，不含 DROP；数据仅保留平台种子、
--       必要配置与初始化演示数据（客服专员Agent发布链/评测数据集/MCP配置）
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- 一、表结构
-- =============================================================

CREATE TABLE IF NOT EXISTS `ai_a2a_push_config` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent任务ID',
  `url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '推送回调URL',
  `token_header` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鉴权头名称（默认Authorization）',
  `token` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鉴权头取值',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A推送配置';
CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码(唯一标识，路由键)',
  `agent_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent名称(中文显示名)',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `icon` text COLLATE utf8mb4_unicode_ci COMMENT '图标（antd图标名或base64图片）',
  `category` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类(CHAT/QA/EXTRACTION/REVIEW/REPORT/ORCHESTRATION)',
  `directory_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属目录编码（空为未分类）',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态(0-禁用 1-启用)',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `session_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话类型(CHAT/KB_QA/DOC_QA/EXTRACTION/REVIEW/REPORT/ORCHESTRATION)',
  `agent_config` json DEFAULT NULL COMMENT 'Agent配置(JSON: model, systemPrompt, maxIterations, temperature)',
  `shared` tinyint(1) NOT NULL DEFAULT '0' COMMENT '平台下发模板标记（仅平台域有意义）',
  `origin_agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复制来源Agent编码（副本专用）',
  `origin_version_id` bigint(20) DEFAULT NULL COMMENT '复制来源版本ID（副本专用）',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_agent_code` (`scope_id`,`agent_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置';
CREATE TABLE IF NOT EXISTS `ai_agent_action_anchor` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `chain_date` date NOT NULL COMMENT '链日期',
  `chain_head_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '当日链头哈希',
  `log_count` int(11) NOT NULL DEFAULT '0' COMMENT '当日日志条数',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '锚定时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_date` (`scope_id`,`chain_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent动作审计链锚点';
CREATE TABLE IF NOT EXISTS `ai_agent_action_log` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `identity_uid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份唯一标识（未绑定身份时为空）',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent编码',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运行ID',
  `action_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作类型：TOOL_CALL工具调用/GOVERNANCE治理动作等',
  `resource` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作对象（工具名/资源标识）',
  `decision` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALLOW' COMMENT '判定结果：ALLOW放行/DENY拒绝',
  `summary` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作摘要（已脱敏截断）',
  `evidence_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证据哈希（SHA-256，企业版哈希链）',
  `prev_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前链哈希（企业版哈希链）',
  `seq_no` bigint(20) DEFAULT NULL COMMENT '链内序号（企业版哈希链）',
  `duration_millis` bigint(20) DEFAULT NULL COMMENT '执行耗时毫秒（工具调用）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人（操作者）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scope_time` (`scope_id`,`create_time`),
  KEY `idx_agent_action` (`agent_code`,`action_type`),
  KEY `idx_decision_time` (`decision`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent动作审计日志';
CREATE TABLE IF NOT EXISTS `ai_agent_budget_override` (
  `id` bigint(20) NOT NULL COMMENT '主键(雪花)',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '租户隔离',
  `agent_code` varchar(100) NOT NULL COMMENT 'Agent编码',
  `override_month` varchar(7) NOT NULL COMMENT '覆盖月份(YYYY-MM)',
  `override_amount` decimal(18,4) NOT NULL COMMENT '覆盖后月度预算',
  `original_amount` decimal(18,4) DEFAULT NULL COMMENT '覆盖前预算',
  `reason` varchar(500) DEFAULT NULL COMMENT '覆盖原因',
  `operator` varchar(100) NOT NULL COMMENT '操作人',
  `create_user` varchar(100) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_user` varchar(100) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget_override` (`scope_id`,`agent_code`,`override_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent月度预算处置覆盖';
CREATE TABLE IF NOT EXISTS `ai_agent_config_drift` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `profile_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '环境档编码',
  `expected_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '期望哈希(PUBLISHED快照按env合成后)',
  `actual_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实际哈希(ai_agent.agent_config同白名单投影)',
  `diff_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '字段级差异JSON',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DETECTED' COMMENT '状态(DETECTED/REPAIRED/IGNORED)',
  `repaired_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修复人',
  `repaired_time` datetime DEFAULT NULL COMMENT '修复时间',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_status` (`scope_id`,`agent_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置漂移记录';
CREATE TABLE IF NOT EXISTS `ai_agent_config_profile` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `profile_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '环境档编码(dev/test/prod/default)',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显示名称',
  `override_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '差异覆盖JSON(仅存差异项:model/temperature/maxIterations/tools子集/知识库开关)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_profile` (`scope_id`,`agent_code`,`profile_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent环境配置档';
CREATE TABLE IF NOT EXISTS `ai_agent_context_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '追踪ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `agent_code` varchar(128) NOT NULL COMMENT 'Agent编码',
  `scope_id` varchar(64) DEFAULT NULL COMMENT '作用域ID',
  `model_code` varchar(128) DEFAULT NULL COMMENT '模型编码',
  `call_seq` int(11) NOT NULL COMMENT '当次任务内的模型调用序号(从1开始)',
  `snapshot_json` longtext NOT NULL COMMENT '消息快照JSON数组[{role,content,source,truncated}]',
  `msg_count` int(11) NOT NULL DEFAULT '0' COMMENT '消息条数',
  `total_chars` bigint(20) NOT NULL DEFAULT '0' COMMENT '快照总字符数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_seq` (`task_id`,`call_seq`),
  KEY `idx_session` (`session_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent模型调用上下文快照';
CREATE TABLE IF NOT EXISTS `ai_agent_definition` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码(唯一标识，≡ai_agent.agent_code)',
  `agent_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent名称(中文显示名)',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent描述',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent分类',
  `current_version_id` bigint(20) DEFAULT NULL COMMENT '当前生效版本ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态(DRAFT/ENABLED/DISABLED)',
  `require_approval` tinyint(4) NOT NULL DEFAULT '0' COMMENT '发布是否需要审批(0-否 1-是)',
  `eval_enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用评测门禁(0-否 1-是)',
  `eval_pass_threshold` decimal(5,2) DEFAULT '80.00' COMMENT '评测通过阈值(均分)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `card_enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'A2A卡片对外启用(0-禁用 1-启用)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code` (`agent_code`,`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent定义';
CREATE TABLE IF NOT EXISTS `ai_agent_directory` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目录编码（创建时自定义，创建后不可修改）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目录名称',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID（NULL为根节点）',
  `sort_num` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体目录';
CREATE TABLE IF NOT EXISTS `ai_agent_gray_rule` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `rule_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则类型(USER_HASH/USER_WHITELIST/SCOPE_LIST)',
  `rule_value` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则值(JSON数组，USER_HASH类型可空)',
  `gray_percent` int(11) DEFAULT '0' COMMENT '灰度百分比(0-100)',
  `target_version_id` bigint(20) NOT NULL COMMENT '目标版本ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/PAUSED)',
  `start_time` datetime DEFAULT NULL COMMENT '生效开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '生效结束时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_status` (`agent_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent灰度规则';
CREATE TABLE IF NOT EXISTS `ai_agent_identity` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `identity_uid` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份唯一标识（aid-前缀）',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份显示名称',
  `owner_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '属主用户',
  `credential_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '凭证指纹（SHA-256前32位，不含明文）',
  `did` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DID标识（企业版导出用，国标后补映射）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/REVOKED已吊销',
  `rotate_days` int(11) DEFAULT NULL COMMENT '轮换周期（天，空为不轮换）',
  `last_rotated_time` datetime DEFAULT NULL COMMENT '最近轮换时间',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_uid` (`identity_uid`),
  UNIQUE KEY `uk_agent_code` (`agent_code`),
  KEY `idx_scope_status` (`scope_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent身份档案';
CREATE TABLE IF NOT EXISTS `ai_agent_memory_entry` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `user_anchor` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户锚点（记忆归属用户）',
  `memory_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆类型：EPISODIC情景/SEMANTIC语义/PROCEDURAL程序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆内容',
  `embedding_ref` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '向量索引引用',
  `source_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源任务ID（任务级溯源）',
  `source_user` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源用户',
  `confidence` double NOT NULL DEFAULT '0.8' COMMENT '置信度（0-1）',
  `ttl_expire_time` datetime DEFAULT NULL COMMENT 'TTL过期时间（null表示永久有效）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE生效/STALE归档/QUARANTINED隔离/ERASED已擦除',
  `input_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容摘要哈希（去重键）',
  `access_count` int(11) NOT NULL DEFAULT '0' COMMENT '访问次数',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最后访问时间',
  `version_no` int(11) NOT NULL DEFAULT '1' COMMENT '版本号（PROCEDURAL版本化）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_user_status` (`agent_code`,`user_anchor`,`status`),
  KEY `idx_status_ttl` (`status`,`ttl_expire_time`),
  KEY `idx_input_hash` (`input_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent运行记忆条目';
CREATE TABLE IF NOT EXISTS `ai_agent_node` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `node_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID',
  `host_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主机名',
  `pid` int(11) DEFAULT NULL COMMENT '进程号',
  `running_count` int(11) NOT NULL DEFAULT '0' COMMENT '心跳时上报的在跑数',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最近心跳时间',
  `startup_time` datetime DEFAULT NULL COMMENT '启动时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node` (`node_id`),
  KEY `idx_heartbeat` (`last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行节点心跳';
CREATE TABLE IF NOT EXISTS `ai_agent_pending_resume` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` varchar(191) NOT NULL COMMENT '恢复请求标识（confirm:会话ID 或 clarification:会话ID:工具调用ID）',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `tool_call_id` varchar(128) DEFAULT NULL COMMENT '关联工具调用ID（澄清场景）',
  `scope_id` varchar(64) DEFAULT NULL COMMENT '租户范围ID',
  `run_id` varchar(64) DEFAULT NULL COMMENT '引擎运行ID',
  `agent_code` varchar(64) DEFAULT NULL COMMENT 'Agent编码',
  `resume_type` varchar(32) NOT NULL COMMENT '恢复类型（CONFIRM/CLARIFICATION）',
  `user_id` varchar(64) DEFAULT NULL COMMENT '用户ID',
  `node_id` varchar(128) DEFAULT NULL COMMENT '注册节点标识',
  `pending_data` text COMMENT '待恢复数据JSON（待确认工具清单或澄清请求）',
  `request_data` text COMMENT '暂停时请求JSON（跨节点重建上下文用）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING待恢复/RESOLVED已恢复/EXPIRED已过期）',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pending_resume_request` (`request_id`),
  KEY `idx_pending_resume_session` (`session_id`),
  KEY `idx_pending_resume_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent暂停恢复登记';
CREATE TABLE IF NOT EXISTS `ai_agent_permission_profile` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `tool_whitelist` text COLLATE utf8mb4_unicode_ci COMMENT '工具白名单（JSON数组，空/缺失=不限制）',
  `knowledge_scope` text COLLATE utf8mb4_unicode_ci COMMENT '知识库范围（JSON数组，空/缺失=不限制）',
  `data_mask_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '数据脱敏级别：NONE不脱敏/BASIC基础/STRICT严格',
  `egress_whitelist` text COLLATE utf8mb4_unicode_ci COMMENT '网络出口白名单（JSON数组，支持*.example.com通配，空/缺失=不限制）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED禁用（禁用=全放行）',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code` (`agent_code`),
  KEY `idx_scope_status` (`scope_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent权限画像';
CREATE TABLE IF NOT EXISTS `ai_agent_release` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `release_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布流水号',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `release_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布类型(PUBLISH/ROLLBACK/GRAY)',
  `from_version_id` bigint(20) DEFAULT NULL COMMENT '原版本ID',
  `to_version_id` bigint(20) DEFAULT NULL COMMENT '目标版本ID',
  `gray_rule_id` bigint(20) DEFAULT NULL COMMENT '灰度规则ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布结果(SUCCESS/FAILED)',
  `gate_result` longtext COLLATE utf8mb4_unicode_ci COMMENT '门禁结论(JSON: 各门禁通过情况+证据ID)',
  `reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原因(回滚/失败说明)',
  `operator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_no` (`release_no`),
  KEY `idx_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent发布审计';
CREATE TABLE IF NOT EXISTS `ai_agent_run_checkpoint` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运行ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话ID',
  `checkpoint_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检查点键',
  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '检查点版本号',
  `payload` longtext COLLATE utf8mb4_unicode_ci COMMENT '序列化检查点',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_key` (`run_id`,`checkpoint_key`),
  KEY `idx_scope_id` (`scope_id`),
  KEY `idx_session_ver` (`scope_id`,`session_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行检查点';
CREATE TABLE IF NOT EXISTS `ai_agent_run_lock` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `lock_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '锁键',
  `owner_node` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '持有节点',
  `expire_at` datetime DEFAULT NULL COMMENT '过期时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lock` (`lock_key`),
  KEY `idx_expire` (`expire_at`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行锁';
CREATE TABLE IF NOT EXISTS `ai_agent_run_state` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运行ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户ID',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent编码',
  `state` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED' COMMENT '状态(CREATED/RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED)',
  `owner_node` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前持锁节点',
  `lock_expire_at` datetime DEFAULT NULL COMMENT '锁到期时间',
  `attempt` int(11) NOT NULL DEFAULT '0' COMMENT '恢复次数',
  `checkpoint_ref` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检查点引用',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `transitions` longtext COLLATE utf8mb4_unicode_ci COMMENT '状态迁移轨迹JSON',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run` (`run_id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_state` (`state`),
  KEY `idx_scope_id` (`scope_id`),
  KEY `idx_session` (`scope_id`,`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行实例状态';
CREATE TABLE IF NOT EXISTS `ai_agent_schedule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `schedule_id` varchar(64) NOT NULL COMMENT '调度ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `task_name` varchar(128) DEFAULT NULL COMMENT '任务名称',
  `agent_code` varchar(64) NOT NULL COMMENT 'Agent类型编码',
  `cron_expression` varchar(128) NOT NULL COMMENT 'Cron表达式',
  `input_text` text COMMENT '输入文本',
  `description` varchar(512) DEFAULT NULL COMMENT '任务描述',
  `body` json DEFAULT NULL COMMENT '请求体参数',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `next_fire_time` datetime DEFAULT NULL COMMENT '下次触发时间',
  `last_fire_time` datetime DEFAULT NULL COMMENT '上次触发时间',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_id` (`schedule_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_next_fire` (`enabled`,`next_fire_time`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent定时调度';
CREATE TABLE IF NOT EXISTS `ai_agent_schedule_log` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `schedule_id` varchar(64) NOT NULL COMMENT '调度ID',
  `agent_code` varchar(64) NOT NULL COMMENT 'Agent编码',
  `user_id` varchar(64) DEFAULT NULL COMMENT '触发用户',
  `input_text` text COMMENT '执行输入',
  `output_text` mediumtext COMMENT '执行输出',
  `success` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否成功（1是0否）',
  `error_message` text COMMENT '失败原因',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时毫秒',
  `fire_time` datetime NOT NULL COMMENT '触发时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `input_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输入token数',
  `output_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输出token数',
  `total_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '总token数',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_log_sid` (`schedule_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent调度执行历史';
CREATE TABLE IF NOT EXISTS `ai_agent_signal_disposal` (
  `id` bigint(20) NOT NULL COMMENT '主键(雪花)',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '租户隔离',
  `signal_type` varchar(50) NOT NULL COMMENT '信号键(driftDetected/slaDegraded/costOverrun等)',
  `agent_code` varchar(100) DEFAULT NULL COMMENT 'Agent编码(全局信号为空)',
  `action` varchar(30) NOT NULL COMMENT '动作类型(AUTO_REPAIR/RUN_REGRESSION/BUDGET_OVERRIDE/ACK)',
  `action_params` varchar(1000) DEFAULT NULL COMMENT '动作参数JSON',
  `result_status` varchar(20) NOT NULL COMMENT '结果状态(SUCCESS/PARTIAL/FAILED)',
  `result_detail` varchar(2000) DEFAULT NULL COMMENT '结果明细(成功N条/失败原因)',
  `operator` varchar(100) NOT NULL COMMENT '操作人',
  `create_user` varchar(100) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_user` varchar(100) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_disposal_signal_agent` (`signal_type`,`agent_code`),
  KEY `idx_disposal_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治理信号处置记录';
CREATE TABLE IF NOT EXISTS `ai_agent_skill_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `skill_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能ID',
  `skill_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能名称',
  `skill_description` text COLLATE utf8mb4_unicode_ci COMMENT '技能描述',
  `skill_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能类型(BUILTIN/UPLOADED/GENERATED)',
  `skill_content` longtext COLLATE utf8mb4_unicode_ci COMMENT '技能内容(Markdown)',
  `bound_tools` json DEFAULT NULL COMMENT '绑定工具列表',
  `resources` json DEFAULT NULL COMMENT '资源文件(路径→内容)',
  `execution` json DEFAULT NULL COMMENT '执行配置',
  `dependencies` json DEFAULT NULL COMMENT '依赖配置',
  `preset_parameters` json DEFAULT NULL COMMENT '预设参数',
  `conditions` json DEFAULT NULL COMMENT '条件配置(任务白名单、请求数据体匹配规则)',
  `skill_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '技能状态(0-禁用 1-启用)',
  `skill_version` int(11) NOT NULL DEFAULT '1' COMMENT '技能版本',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `trust_level` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'COMMUNITY' COMMENT '信任等级(BUILTIN/TRUSTED/COMMUNITY/AGENT_CREATED)',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属分类编码（空为未分类）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_id` (`skill_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent技能配置';
CREATE TABLE IF NOT EXISTS `ai_agent_skill_gen_draft` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `draft_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草稿ID',
  `skill_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能名称',
  `skill_description` text COLLATE utf8mb4_unicode_ci COMMENT '技能描述',
  `skill_content` longtext COLLATE utf8mb4_unicode_ci COMMENT '技能内容',
  `bound_tools` json DEFAULT NULL COMMENT '绑定工具',
  `generate_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生成状态(CREATED/RUNNING/DRAFT_READY/NEED_CLARIFICATION/SAVE_CONFIRMATION_REQUIRED/SAVED)',
  `resume_token` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '恢复令牌',
  `resume_token_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '恢复令牌哈希',
  `resume_token_expire_time` datetime DEFAULT NULL COMMENT '恢复令牌过期时间',
  `clarification_question` text COLLATE utf8mb4_unicode_ci COMMENT '澄清问题',
  `clarification_answer` text COLLATE utf8mb4_unicode_ci COMMENT '澄清回答',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_id` (`draft_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能生成草稿';
CREATE TABLE IF NOT EXISTS `ai_agent_skill_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `skill_id` varchar(128) NOT NULL,
  `view_count` bigint(20) DEFAULT '0' COMMENT '查看次数',
  `use_count` bigint(20) DEFAULT '0' COMMENT '使用次数',
  `patch_count` bigint(20) DEFAULT '0' COMMENT '修改次数',
  `state` varchar(32) DEFAULT 'ACTIVE' COMMENT '生命周期状态(DRAFT/ACTIVE/STALE/ARCHIVED)',
  `pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶',
  `last_viewed_at` datetime DEFAULT NULL COMMENT '最后查看时间',
  `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
  `last_patched_at` datetime DEFAULT NULL COMMENT '最后修改时间',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_id` (`skill_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能使用量统计';
CREATE TABLE IF NOT EXISTS `ai_agent_skill_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `skill_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能ID',
  `version` int(11) NOT NULL COMMENT '版本号',
  `skill_content` longtext COLLATE utf8mb4_unicode_ci COMMENT '技能内容',
  `change_log` text COLLATE utf8mb4_unicode_ci COMMENT '变更日志',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能内容SHA-256指纹',
  `quality_score` decimal(5,2) DEFAULT NULL COMMENT '内容质量总分(0-100)',
  `eval_dimensions` json DEFAULT NULL COMMENT '分维度得分与改进建议',
  `eval_model` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评测模型',
  `evaluated_time` datetime DEFAULT NULL COMMENT '评测时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`skill_id`,`version`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent技能版本';
CREATE TABLE IF NOT EXISTS `ai_agent_sla_daily` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_runs` int(11) NOT NULL DEFAULT '0' COMMENT '总运行次数',
  `success_runs` int(11) NOT NULL DEFAULT '0' COMMENT '成功次数',
  `failed_runs` int(11) NOT NULL DEFAULT '0' COMMENT '失败次数',
  `timeout_runs` int(11) NOT NULL DEFAULT '0' COMMENT '超时次数',
  `failure_rate` decimal(6,4) DEFAULT NULL COMMENT '失败率',
  `p50_duration_ms` bigint(20) DEFAULT NULL COMMENT 'P50耗时(毫秒)',
  `p95_duration_ms` bigint(20) DEFAULT NULL COMMENT 'P95耗时(毫秒)',
  `avg_approval_wait_ms` bigint(20) DEFAULT NULL COMMENT '审批平均等待(毫秒)',
  `total_cost` decimal(14,6) DEFAULT NULL COMMENT '总成本',
  `total_tokens` bigint(20) DEFAULT NULL COMMENT '总Token数',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_date` (`scope_id`,`agent_code`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent运行SLA日汇总';
CREATE TABLE IF NOT EXISTS `ai_agent_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `parent_task_id` varchar(64) DEFAULT NULL COMMENT '父任务ID（子代理场景）',
  `fork_call_seq` int(11) DEFAULT NULL COMMENT '分叉点：来源任务的模型调用序号',
  `agent_path` varchar(512) DEFAULT NULL COMMENT '代理调用路径（如 main>research>sql）',
  `agent_code` varchar(64) NOT NULL COMMENT '代理类型编码',
  `agent_name` varchar(128) DEFAULT NULL COMMENT '代理显示名称',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `user_id` varchar(64) DEFAULT NULL COMMENT '用户ID',
  `user_input` text COMMENT '用户输入（纯文本，多模态的文本部分）',
  `user_input_json` json DEFAULT NULL COMMENT '用户输入（多模态JSON，List<InputBlock>）',
  `output_text` text COMMENT '输出文本（多模态的文本部分）',
  `output_json` json DEFAULT NULL COMMENT '输出（多模态JSON，List<OutputBlock>）',
  `task_status` varchar(32) NOT NULL COMMENT '状态(PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)',
  `priority` int(11) NOT NULL DEFAULT '5' COMMENT '优先级0-9(大者先执行)',
  `queued_time` datetime DEFAULT NULL COMMENT '入队时间',
  `runner_id` varchar(128) DEFAULT NULL COMMENT '执行实例标识(hostname:port:uuid)',
  `runner_heartbeat` datetime DEFAULT NULL COMMENT '执行实例心跳',
  `redeliver_count` int(11) NOT NULL DEFAULT '0' COMMENT '重派次数',
  `task_source` varchar(16) DEFAULT 'ASYNC' COMMENT '任务来源(SYNC/STREAM/ASYNC)',
  `error_message` text COMMENT '错误信息',
  `input_tokens` int(11) DEFAULT '0' COMMENT '真实输入Token数',
  `output_tokens` int(11) DEFAULT '0' COMMENT '真实输出Token数',
  `total_tokens` int(11) DEFAULT '0' COMMENT '真实总Token数',
  `execution_time` double DEFAULT '0' COMMENT '模型执行耗时（秒）',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '任务总执行时长（毫秒）',
  `body` json DEFAULT NULL COMMENT '请求数据体',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_parent_task_id` (`parent_task_id`),
  KEY `idx_agent_code` (`agent_code`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_task_source` (`task_source`),
  KEY `idx_scope_id` (`scope_id`),
  KEY `idx_queue_scan` (`task_status`,`priority`,`queued_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent任务';
CREATE TABLE IF NOT EXISTS `ai_agent_task_step` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `step_order` int(11) NOT NULL COMMENT '步骤序号（从1递增）',
  `step_type` varchar(32) NOT NULL COMMENT '步骤类型(LLM_CALL/TOOL_CALL/TOOL_RESULT/SUBAGENT_CALL)',
  `agent_name` varchar(128) DEFAULT NULL COMMENT '执行代理名称',
  `step_content` text COMMENT '步骤内容（LLM推理文本/工具输入输出摘要）',
  `tool_name` varchar(128) DEFAULT NULL COMMENT '工具名称（TOOL_CALL类型）',
  `tool_input` text COMMENT '工具输入（JSON）',
  `tool_output` text COMMENT '工具输出（文本摘要）',
  `input_tokens` int(11) DEFAULT '0' COMMENT '步骤输入Token数（LLM_CALL）',
  `output_tokens` int(11) DEFAULT '0' COMMENT '步骤输出Token数（LLM_CALL）',
  `total_tokens` int(11) DEFAULT '0' COMMENT '步骤总Token数（LLM_CALL）',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '步骤执行时长（毫秒）',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `permission_decision` varchar(16) DEFAULT NULL COMMENT '权限决策(ALLOW/ASK/DENY)',
  `exit_code` int(11) DEFAULT NULL COMMENT '工具退出码',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_task_id_step_order` (`task_id`,`step_order`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent任务步骤';
CREATE TABLE IF NOT EXISTS `ai_agent_tool_execution` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `idem_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运行ID',
  `tool_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具名称',
  `result_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '执行结果JSON',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idem` (`idem_key`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具执行幂等记录';
CREATE TABLE IF NOT EXISTS `ai_agent_trace_span` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '追踪ID(一次运行根span与子span共享)',
  `span_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Span ID',
  `parent_span_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父Span ID(根span为空)',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联任务ID(ai_agent_task.task_id)',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Agent编码',
  `operation` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作名(agent_run/reasoning/acting/model_call/tool_call/middleware)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OK' COMMENT '状态(OK/ERROR)',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息(截断)',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时(毫秒)',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `attributes` json DEFAULT NULL COMMENT '属性标签(截断后)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_span` (`trace_id`,`span_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_agent_time` (`agent_code`,`create_time`),
  KEY `idx_trace_root` (`trace_id`,`parent_span_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent运行Span';
CREATE TABLE IF NOT EXISTS `ai_agent_trigger` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `trigger_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发器编码（唯一）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发器名称',
  `trigger_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发类型：CRON定时/WEBHOOK回调/EVENT内置事件/FILE文件监听',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标Agent编码',
  `user_anchor` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '归属用户锚点',
  `cron_expr` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CRON表达式（CRON类型必填，复用引擎CronExpression语法）',
  `webhook_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'WEBHOOK回调令牌（令牌即凭证）',
  `event_source` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内置事件源：CONFIG_DRIFT/EVAL_REGRESSION/ACTION_ANOMALY等',
  `payload_template` text COLLATE utf8mb4_unicode_ci COMMENT '输入指令模板（支持{payload}占位符）',
  `watch_dir` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件监听根目录（FILE类型必填）',
  `file_suffixes` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件后缀过滤（逗号分隔，空为全部）',
  `notify_webhook` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结果回投WEBHOOK地址（空则不回投）',
  `daily_quota` int(11) DEFAULT NULL COMMENT '每日触发配额（空或0为不限）',
  `dedup_window_seconds` int(11) NOT NULL DEFAULT '0' COMMENT '同源去重窗口秒数（0为不去重）',
  `enabled` int(11) NOT NULL DEFAULT '1' COMMENT '是否启用（1启用/0停用）',
  `last_fire_time` datetime DEFAULT NULL COMMENT 'CRON最近一次计划触发时刻（幂等推进基准）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trigger_code` (`trigger_code`),
  UNIQUE KEY `uk_webhook_token` (`webhook_token`),
  KEY `idx_type_enabled` (`trigger_type`,`enabled`),
  KEY `idx_event_source` (`event_source`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent触发器';
CREATE TABLE IF NOT EXISTS `ai_agent_trigger_log` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `trigger_id` bigint(20) NOT NULL COMMENT '触发规则ID',
  `trigger_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发器编码（冗余，便于查询）',
  `dedup_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等键（CRON为计划时刻/事件为载荷摘要）',
  `fire_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发来源：CRON_SCHEDULER/BUILTIN_EVENT/MANUAL/WEBHOOK/FILE_WATCH',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入队任务ID',
  `payload_digest` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发载荷摘要（截断）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_trigger_time` (`trigger_id`,`create_time`),
  KEY `idx_trigger_dedup` (`trigger_id`,`dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent触发记录';
CREATE TABLE IF NOT EXISTS `ai_agent_usage_call` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `reply_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型调用回复ID(ModelCallEndEvent.replyId)',
  `call_seq` int(11) NOT NULL COMMENT '调用序号(同一run内从1递增)',
  `model_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型编码(该次调用实际使用)',
  `input_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输入Token',
  `output_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输出Token',
  `cached_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '缓存Token',
  `total_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '总Token',
  `cost_usd` decimal(12,8) DEFAULT NULL COMMENT '成本USD(无定价时为空)',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '该次调用耗时(毫秒)',
  `start_time` datetime(3) DEFAULT NULL COMMENT '调用开始时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_call` (`task_id`,`call_seq`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_model_time` (`model_code`,`create_time`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent调用级用量明细';
CREATE TABLE IF NOT EXISTS `ai_agent_usage_daily` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `run_count` int(11) NOT NULL DEFAULT '0' COMMENT '运行次数',
  `input_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输入Token',
  `output_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输出Token',
  `total_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '总Token',
  `cost_usd` decimal(14,6) NOT NULL DEFAULT '0.000000' COMMENT '成本USD',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_agent_date` (`scope_id`,`agent_code`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent用量日汇总';
CREATE TABLE IF NOT EXISTS `ai_agent_usage_record` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID(幂等键)',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `model_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主模型编码',
  `input_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输入Token',
  `output_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '输出Token',
  `total_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT '总Token',
  `cost_usd` decimal(12,6) DEFAULT NULL COMMENT '成本USD(无定价时为空)',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '执行时长(毫秒)',
  `task_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务结果状态(SUCCEEDED/FAILED)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_id` (`record_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_agent_time` (`agent_code`,`create_time`),
  KEY `idx_scope_time` (`scope_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent用量明细';
CREATE TABLE IF NOT EXISTS `ai_agent_version` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `version_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `config_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '装配清单JSON(model/systemPrompt/maxIterations/temperature/tools/skills/knowledgeBase/bindingMode)',
  `config_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置哈希(SHA-256，防重复发布)',
  `changelog` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更说明',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态(DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/DEPRECATED)',
  `eval_dataset_locations` longtext COLLATE utf8mb4_unicode_ci COMMENT '评测数据集位置(JSON数组)',
  `eval_report_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评测报告ID',
  `eval_passed` tinyint(4) DEFAULT NULL COMMENT '评测是否通过(0-否 1-是)',
  `submit_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `eval_run_id` bigint(20) DEFAULT NULL COMMENT '引用的评测运行ID(发布门禁离线报告)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version` (`agent_code`,`version_no`,`scope_id`),
  KEY `idx_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent版本';
CREATE TABLE IF NOT EXISTS `ai_alert_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` bigint(20) DEFAULT NULL COMMENT '规则ID',
  `title` varchar(256) NOT NULL COMMENT '告警标题',
  `content` text COMMENT '告警内容',
  `level` varchar(16) NOT NULL COMMENT '告警级别',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/RESOLVED/IGNORED)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `resolve_time` datetime DEFAULT NULL COMMENT '解决时间',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT '租户隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警实例';
CREATE TABLE IF NOT EXISTS `ai_alert_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `level` varchar(16) NOT NULL DEFAULT 'WARNING' COMMENT '告警级别(INFO/WARNING/CRITICAL)',
  `throttle_seconds` int(11) NOT NULL DEFAULT '0' COMMENT '节流秒数',
  `actionable` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否带交互按钮(0否/1是)',
  `actions` text COMMENT '操作按钮列表(JSON)',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(0否/1是)',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT '租户隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';
CREATE TABLE IF NOT EXISTS `ai_chat_memory` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户ID',
  `message_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色(USER/ASSISTANT/SYSTEM/TOOL)',
  `message_content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `token_count` int(11) DEFAULT NULL COMMENT 'Token数量',
  `importance_score` int(11) DEFAULT '0' COMMENT '重要性评分',
  `input_tokens` int(11) DEFAULT '0' COMMENT '真实输入Token数（模型返回）',
  `output_tokens` int(11) DEFAULT '0' COMMENT '真实输出Token数（模型返回）',
  `total_tokens` int(11) DEFAULT '0' COMMENT '真实总Token数（模型返回）',
  `execution_time` double DEFAULT '0' COMMENT '模型执行耗时（秒）',
  `body` json DEFAULT NULL COMMENT '请求数据体',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_session_create` (`session_id`,`create_time`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记忆';
CREATE TABLE IF NOT EXISTS `ai_connector_credential` (
  `id` bigint(20) NOT NULL COMMENT '主键(雪花)',
  `provider_code` varchar(32) NOT NULL COMMENT '提供商编码(dingtalk/wecom/feishu/database/docparser)',
  `name` varchar(128) NOT NULL COMMENT '凭证名称',
  `credential_json` text COMMENT 'AES加密后的凭证JSON',
  `masked_json` text COMMENT '敏感字段尾号掩码(JSON: 字段名->掩码值)',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态(ENABLED/DISABLED)',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT 'scope隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_provider_name` (`scope_id`,`provider_code`,`name`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连接器凭证';
CREATE TABLE IF NOT EXISTS `ai_connector_instance` (
  `id` bigint(20) NOT NULL COMMENT '主键(雪花)',
  `instance_code` varchar(64) NOT NULL COMMENT '实例编码(入站回调路由键)',
  `provider_code` varchar(32) NOT NULL COMMENT '提供商编码',
  `name` varchar(128) NOT NULL COMMENT '实例名称',
  `config_json` text COMMENT '非敏感配置JSON(robotCode/agentCode绑定/数据库参数/解析参数等)',
  `credential_id` bigint(20) DEFAULT NULL COMMENT '关联凭证ID(可空=免凭证提供商)',
  `agent_code` varchar(64) DEFAULT NULL COMMENT '入站消息目标Agent编码(可空=仅出站工具)',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态(ENABLED/DISABLED)',
  `version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT 'scope隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_instance` (`scope_id`,`instance_code`),
  KEY `idx_provider` (`provider_code`),
  KEY `idx_credential` (`credential_id`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连接器实例';
CREATE TABLE IF NOT EXISTS `ai_conversation_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务编码',
  `session_title` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `summary_text` text COLLATE utf8mb4_unicode_ci COMMENT 'LLM生成的会话摘要',
  `session_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话类型(CHAT/KB_QA/WIKI_QA)',
  `body` json DEFAULT NULL COMMENT '请求数据体',
  `summary_round` int(11) DEFAULT '0' COMMENT '摘要轮次',
  `latest_summary_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最新摘要ID',
  `last_summarized_at` datetime DEFAULT NULL COMMENT '最后摘要时间',
  `session_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '会话状态(0-已结束 1-活跃)',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';
CREATE TABLE IF NOT EXISTS `ai_datasource_ingest_log` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `ingest_type` varchar(32) NOT NULL COMMENT '接入方式(TEXT/WEBPAGE/API/DATABASE)',
  `title` varchar(512) DEFAULT NULL COMMENT '文档标题',
  `kb_id` varchar(64) DEFAULT NULL COMMENT '知识库ID',
  `source_url` varchar(1024) DEFAULT NULL COMMENT '来源URL(网页/API接入)',
  `doc_id` varchar(64) DEFAULT NULL COMMENT '生成的文档ID(失败时为空)',
  `content_size` bigint(20) DEFAULT NULL COMMENT '内容大小(字节)',
  `user_id` varchar(64) DEFAULT NULL COMMENT '归属用户(知识库归属人)',
  `success` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否成功(1是0否)',
  `error_message` text COMMENT '失败原因',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时毫秒',
  `ingest_time` datetime NOT NULL COMMENT '接入时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ingest_log_kb` (`kb_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源接入记录';
CREATE TABLE IF NOT EXISTS `ai_eval_case_candidate` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `input_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '输入文本SHA-256(防重)',
  `input_text` longtext COLLATE utf8mb4_unicode_ci COMMENT '真实输入',
  `expected_output` longtext COLLATE utf8mb4_unicode_ci COMMENT '实际输出(人工确认后转期望输出)',
  `actual_output` longtext COLLATE utf8mb4_unicode_ci COMMENT '实际输出原文',
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNTIME' COMMENT '来源(RUNTIME/MANUAL)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CANDIDATE' COMMENT '状态(CANDIDATE/CONFIRMED/REJECTED)',
  `confirmed_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '确认人',
  `confirmed_time` datetime DEFAULT NULL COMMENT '确认时间',
  `dataset_id` bigint(20) DEFAULT NULL COMMENT '确认转入的数据集ID',
  `dataset_case_id` bigint(20) DEFAULT NULL COMMENT '转入的数据集用例ID',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_input` (`scope_id`,`agent_code`,`input_hash`),
  KEY `idx_agent_status` (`scope_id`,`agent_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测候选案例池';
CREATE TABLE IF NOT EXISTS `ai_eval_dataset` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `dataset_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编码',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据集描述',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态(DRAFT/ENABLED/DISABLED)',
  `case_count` int(11) NOT NULL DEFAULT '0' COMMENT '冗余用例数',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_dataset` (`scope_id`,`dataset_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测数据集';
CREATE TABLE IF NOT EXISTS `ai_eval_dataset_case` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集ID',
  `case_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用例编号(写入GoldenCase.caseId)',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用例标题',
  `query_text` text COLLATE utf8mb4_unicode_ci COMMENT '用户输入(→GoldenCase.query)',
  `expected_output` text COLLATE utf8mb4_unicode_ci COMMENT '期望输出',
  `body_json` text COLLATE utf8mb4_unicode_ci COMMENT '额外请求体JSON(→GoldenCase.body，agentCode覆盖等)',
  `scoring_criteria` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评分策略(llm_judge/contains_all/exact_match/fuzzy_match，空=llm_judge)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dataset_case` (`dataset_id`,`case_no`),
  KEY `idx_dataset` (`dataset_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测数据集用例';
CREATE TABLE IF NOT EXISTS `ai_eval_regression` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `run_date` date NOT NULL COMMENT '回归日期',
  `eval_run_id` bigint(20) DEFAULT NULL COMMENT '关联的评测运行ID(ai_eval_run)',
  `pass_rate` decimal(6,4) DEFAULT NULL COMMENT '通过率0-1',
  `avg_score` decimal(6,4) DEFAULT NULL COMMENT '平均分0-1(引擎分值域)',
  `prev_pass_rate` decimal(6,4) DEFAULT NULL COMMENT '前一日通过率',
  `prev_avg_score` decimal(6,4) DEFAULT NULL COMMENT '前一日平均分',
  `degraded` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否环比下降超阈值(0-否 1-是)',
  `detail_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '逐案例结果',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_date` (`scope_id`,`agent_code`,`run_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测回归日报告';
CREATE TABLE IF NOT EXISTS `ai_eval_run` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集ID',
  `dataset_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据集编码快照',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被测Agent编码(空=用例body内指定)',
  `judge_model_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评判模型编码快照',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态(RUNNING/PASSED/FAILED/ERROR/CANCELLED)',
  `total_cases` int(11) NOT NULL DEFAULT '0' COMMENT '总用例数',
  `passed_cases` int(11) NOT NULL DEFAULT '0' COMMENT '通过用例数',
  `failed_cases` int(11) NOT NULL DEFAULT '0' COMMENT '失败用例数',
  `avg_score` decimal(8,4) DEFAULT NULL COMMENT '平均分(0-100)',
  `threshold` decimal(8,4) DEFAULT NULL COMMENT '本次判定阈值快照(0-100)',
  `report_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '评测报告完整快照(EvaluationReport)',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dataset` (`dataset_id`),
  KEY `idx_agent_time` (`agent_code`,`create_time`),
  KEY `idx_scope_time` (`scope_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测运行';
CREATE TABLE IF NOT EXISTS `ai_eval_run_case` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `run_id` bigint(20) NOT NULL COMMENT '评测运行ID',
  `case_id` bigint(20) DEFAULT NULL COMMENT '数据集用例ID',
  `case_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用例编号(=CaseResult.caseId)',
  `actual_output` longtext COLLATE utf8mb4_unicode_ci COMMENT '实际输出',
  `expected_output` text COLLATE utf8mb4_unicode_ci COMMENT '期望输出',
  `score` decimal(8,4) DEFAULT NULL COMMENT '得分(0-100)',
  `passed` tinyint(4) DEFAULT NULL COMMENT '是否通过(0-否 1-是)',
  `strategy_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评分策略名',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测运行用例明细';
CREATE TABLE IF NOT EXISTS `ai_eval_sampling_config` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `agent_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Agent编码',
  `sample_rate` decimal(5,4) NOT NULL DEFAULT '0.0000' COMMENT '采样率0-1',
  `daily_cap` int(11) NOT NULL DEFAULT '100' COMMENT '每日入库上限',
  `regression_enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用每日回归(0-否 1-是)',
  `regression_dataset_id` bigint(20) DEFAULT NULL COMMENT '回归绑定的数据集ID',
  `enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '采样是否启用(0-否 1-是)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent` (`scope_id`,`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测流量采样配置';
CREATE TABLE IF NOT EXISTS `ai_integration_channel_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `channel_type` varchar(16) NOT NULL COMMENT '渠道类型(DINGTALK/FEISHU/WECOM/EMAIL/WEBHOOK)',
  `webhook_url` varchar(512) DEFAULT NULL COMMENT 'Webhook地址',
  `secret` varchar(256) DEFAULT NULL COMMENT '密钥',
  `enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用(0否/1是)',
  `extra` text COMMENT '扩展配置(JSON)',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT '租户隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_channel_type` (`channel_type`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集成渠道配置';
CREATE TABLE IF NOT EXISTS `ai_integration_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID',
  `channel` varchar(16) NOT NULL COMMENT '渠道',
  `status` varchar(16) NOT NULL COMMENT '发送状态(SUCCESS/FAILED)',
  `response` text COMMENT '响应内容',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT '租户隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `payload` text COMMENT '外发载荷(JSON,用于手工重推)',
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集成发送记录';
CREATE TABLE IF NOT EXISTS `ai_kb_document` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doc_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属用户ID',
  `kb_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库ID',
  `doc_name` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `file_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小(字节)',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件内容MD5哈希',
  `file_bucket` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MinIO存储bucket',
  `file_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MinIO文件路径',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'FILE' COMMENT '数据来源类型: FILE/DATABASE/API/WEBPAGE/TEXT/CRAWLER',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '直接文本内容（TEXT/API/WEBPAGE等来源，FILE来源为空）',
  `version_tag` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本标签',
  `doc_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档状态(PENDING/PARSING/PARSED/CHUNKING/CHUNKED/EMBEDDING/COMPLETED/FAILED)',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `chunk_count` int(11) DEFAULT NULL COMMENT '切片数量',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '文档摘要',
  `regions` json DEFAULT NULL COMMENT '地区名列表',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_id` (`doc_id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_scope_id` (`scope_id`),
  KEY `idx_kb_content_hash` (`kb_id`,`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';
CREATE TABLE IF NOT EXISTS `ai_kb_slice_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `slice_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '切片ID',
  `doc_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `kb_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库ID',
  `version_tag` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本标签',
  `sort_num` int(11) DEFAULT NULL COMMENT '排序号',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否有效',
  `chunk_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CHILD' COMMENT '切片类型(PARENT/CHILD)',
  `parent_chunk_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父切片ID',
  `chunk_index` int(11) NOT NULL DEFAULT '0' COMMENT '切片序号',
  `chunk_text` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '切片文本',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希,用于切片去重',
  `context_inject` text COLLATE utf8mb4_unicode_ci COMMENT '上下文注入',
  `start_offset` int(11) DEFAULT NULL COMMENT '起始偏移',
  `end_offset` int(11) DEFAULT NULL COMMENT '结束偏移',
  `qdrant_point_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Qdrant点ID',
  `collection_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Qdrant集合名',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slice_id` (`slice_id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_kb_version` (`kb_id`,`version_tag`),
  KEY `idx_scope_id` (`scope_id`),
  FULLTEXT KEY `ft_chunk_text` (`chunk_text`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库切片记录';
CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `kb_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属用户ID',
  `kb_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `kb_description` text COLLATE utf8mb4_unicode_ci COMMENT '知识库描述',
  `embedding_model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '嵌入模型',
  `chunk_strategy` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD' COMMENT '切片策略(STANDARD/PARENT_CHILD)',
  `chunk_config` json DEFAULT NULL COMMENT '切片配置',
  `active_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前活跃版本',
  `kb_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '知识库状态(0-禁用 1-启用)',
  `kb_icon` text COLLATE utf8mb4_unicode_ci COMMENT '图标名称或base64图片',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_id` (`kb_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';
CREATE TABLE IF NOT EXISTS `ai_mcp_server_category` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码（创建时自定义，创建后不可修改）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID（NULL为根节点）',
  `sort_num` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务分类';
CREATE TABLE IF NOT EXISTS `ai_mcp_server_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `server_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务编码',
  `server_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务名称',
  `transport_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '传输类型(STDIO/SSE/STREAMABLE_HTTP)',
  `connection_config` json NOT NULL COMMENT '连接配置',
  `enabled_tools` json DEFAULT NULL COMMENT '启用的工具列表',
  `disabled_tools` json DEFAULT NULL COMMENT '禁用的工具列表',
  `server_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '服务状态(0-禁用 1-启用)',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属分类编码（空为未分类）',
  `offline_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '下线原因（自动下线时记录）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_code` (`server_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务配置';
CREATE TABLE IF NOT EXISTS `ai_mcp_server_expose` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `expose_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '暴露类型：TOOL平台工具/AGENT代理长任务/PROMPT能力提示词/RESOURCE知识资源',
  `expose_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '暴露编码（工具编码/代理编码/能力编码/资源来源键）',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '对外显示名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '对外描述',
  `render_allowed` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否允许MCP Apps渲染（0否/1是，SEP-1865预留）',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用（0否/1是）',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`expose_type`,`expose_code`),
  KEY `idx_scope_enabled` (`scope_id`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务暴露白名单';
CREATE TABLE IF NOT EXISTS `ai_model_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型编码',
  `model_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `provider` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型提供商(OPENAI/OLLAMA/DEEPSEEK/ZHIPU)',
  `model_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型类型(CHAT/EMBEDDING)',
  `api_endpoint` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API地址',
  `api_key` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥(加密存储)',
  `model_config` json DEFAULT NULL COMMENT '模型配置(JSON: temperature, topP, maxTokens)',
  `is_default` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否默认(0-否 1-是)',
  `model_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '模型状态(0-禁用 1-启用)',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `support_reasoning` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否支持推理（1是0否）',
  `support_image` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否支持图片（1是0否）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型信息';
CREATE TABLE IF NOT EXISTS `ai_open_capability` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码（唯一标识）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '能力名称（用于界面展示）',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明（用于界面展示和维护说明）',
  `definition_json` text COLLATE utf8mb4_unicode_ci COMMENT '能力定义JSON（完整CapabilitySpec JSON）',
  `input_schema` text COLLATE utf8mb4_unicode_ci COMMENT '输入Schema内容（JSON格式）',
  `output_schema` text COLLATE utf8mb4_unicode_ci COMMENT '输出Schema内容（JSON格式）',
  `prompt_template` text COLLATE utf8mb4_unicode_ci COMMENT 'Prompt模板内容',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用（0=禁用，1=启用）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-能力定义';
CREATE TABLE IF NOT EXISTS `ai_open_capability_call` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `call_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调用ID',
  `capability` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '能力编码',
  `caller` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调用方',
  `dedup_key` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '去重键',
  `request_fingerprint` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求指纹',
  `request_body` text COLLATE utf8mb4_unicode_ci COMMENT '请求体',
  `response_body` text COLLATE utf8mb4_unicode_ci COMMENT '响应体',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态(success/failure/timeout)',
  `duration_millis` bigint(20) DEFAULT NULL COMMENT '耗时毫秒',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `started_at` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开始时间',
  `finished_at` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束时间',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_call_id` (`call_id`),
  KEY `idx_capability` (`capability`),
  KEY `idx_dedup_key` (`dedup_key`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-能力调用记录';
CREATE TABLE IF NOT EXISTS `ai_open_capability_category` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码（创建时自定义，创建后不可修改）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID（NULL为根节点）',
  `sort_num` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-能力分类';
CREATE TABLE IF NOT EXISTS `ai_open_capability_history` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `capability_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码',
  `version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照版本号',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照能力名称',
  `operation` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型(CREATE/UPDATE/ROLLBACK)',
  `definition_json` text COLLATE utf8mb4_unicode_ci COMMENT '快照定义JSON（完整CapabilitySpec JSON）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_capability_code` (`capability_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-能力定义版本历史';
CREATE TABLE IF NOT EXISTS `ai_open_credential` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `credential_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '凭证编码，全局唯一',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '凭证名称',
  `secret_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密钥哈希（SHA-256(credential_code:secret)），明文不落库',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用/DISABLED禁用/REVOKED吊销',
  `ip_whitelist` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IP白名单（逗号分隔，企业版生效）',
  `rate_limit_qps` int(11) DEFAULT NULL COMMENT '每秒限流阈值（空为不限流）',
  `daily_quota` int(11) DEFAULT NULL COMMENT '每日配额（企业版生效）',
  `expires_time` datetime DEFAULT NULL COMMENT '过期时间（空为永不过期）',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_code` (`credential_code`),
  KEY `idx_scope_status` (`scope_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放凭证';
CREATE TABLE IF NOT EXISTS `ai_open_data_context` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `ref` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '引用编码',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上下文类型（project-info/contract-text/attachment/custom）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上下文名称',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '上下文内容（文本或结构化JSON）',
  `expires_at` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '过期时间（yyyy-MM-dd HH:mm:ss格式）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref` (`ref`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-数据上下文';
CREATE TABLE IF NOT EXISTS `ai_open_request_dedup` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dedup_key` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '去重键',
  `request_fingerprint` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求指纹',
  `response_cache` text COLLATE utf8mb4_unicode_ci COMMENT '缓存响应',
  `expires_at` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '过期时间（yyyy-MM-dd HH:mm:ss格式）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放能力-请求去重记录';
CREATE TABLE IF NOT EXISTS `ai_org_context` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `context_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：TERM术语/ALIAS别名/LINEAGE血缘',
  `term` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '术语（TERM=术语名，ALIAS=别名，LINEAGE=子术语）',
  `target_term` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标术语（TERM=标准名，ALIAS=标准术语，LINEAGE=上级术语）',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `conflict_flag` tinyint(4) NOT NULL DEFAULT '0' COMMENT '冲突标记（1=冲突，如别名映射到不同标准术语）',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '启用状态（1=启用，0=停用）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_term` (`context_type`,`term`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织上下文记忆';
CREATE TABLE IF NOT EXISTS `ai_pending_request` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求唯一标识',
  `approval_token` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批安全令牌',
  `session_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `approver` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人(user:xxx/user:xxx,yyy/role:xxx，空表示按发起人处理)',
  `resource_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TOOL' COMMENT '资源类型: TOOL/WORKFLOW_NODE/CUSTOM',
  `target_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标名称',
  `reason` text COLLATE utf8mb4_unicode_ci COMMENT '审批原因说明',
  `target_params` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数JSON',
  `input_schema` text COLLATE utf8mb4_unicode_ci COMMENT '输入模式定义JSON（options/fields）',
  `response_payload` text COLLATE utf8mb4_unicode_ci COMMENT '审批响应数据JSON（选择结果和表单内容）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED/TIMEOUT',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `resolved_time` datetime DEFAULT NULL COMMENT '审批/拒绝时间',
  `resolved_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人',
  `reject_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因',
  `del_flag` tinyint(4) NOT NULL DEFAULT '0' COMMENT '删除标记: 0-正常, 1-删除',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  UNIQUE KEY `uk_approval_token` (`approval_token`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_status_expire` (`status`,`expire_time`),
  KEY `idx_scope_id` (`scope_id`),
  KEY `idx_pending_approver` (`approver`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批请求表';
CREATE TABLE IF NOT EXISTS `ai_session_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `tag` varchar(64) NOT NULL COMMENT '标签名称',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_tag` (`session_id`,`tag`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话标签';
CREATE TABLE IF NOT EXISTS `ai_skill_category` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码（创建时自定义，创建后不可修改）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID（NULL为根节点）',
  `sort_num` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能分类';
CREATE TABLE IF NOT EXISTS `ai_system_menu` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（种子固定编号，管理页新增为雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '归属作用域，平台级菜单固定default',
  `app_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '端标识：community-admin/enterprise-admin/enterprise-client',
  `menu_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语义键，app_code内唯一',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父菜单ID，0=根',
  `menu_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PAGE' COMMENT '类型：GROUP=分组/PAGE=页面/LINK=外链',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端路由路径（PAGE/LINK必填）',
  `icon` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标名字符串，各端图标注册表映射',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '同级显示顺序',
  `visible` tinyint(4) NOT NULL DEFAULT '1' COMMENT '全局显隐：1显示/0隐藏',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1启用/0停用（停用=不存在，区别于隐藏）',
  `permission_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限点编码，关联ai_system_permission.perm_code，可空',
  `feature_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置开关键（如ai.agent.evolution.enabled），缺失视为开启',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0存在/1删除',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_app` (`app_code`,`status`),
  KEY `idx_menu_key` (`app_code`,`menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单目录';
CREATE TABLE IF NOT EXISTS `ai_system_menu_scope` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  `scope_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '覆盖维度：SCOPE=租户作用域/USER=用户',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID或用户ID',
  `visible` tinyint(4) NOT NULL COMMENT '覆盖值：0=对该作用域隐藏/1=强制显示（即使全局隐藏）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_scope` (`menu_id`,`scope_type`,`scope_id`),
  KEY `idx_scope` (`scope_type`,`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单作用域覆盖';
CREATE TABLE IF NOT EXISTS `ai_system_user` (
  `id` bigint(20) NOT NULL COMMENT '主键ID（雪花算法）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名，scope内唯一',
  `password` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt加密）',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显示名称',
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `org_id` bigint(20) DEFAULT NULL COMMENT '所属组织ID（企业版组织归属列，社区版预留不使用）',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1启用/0停用',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0存在/1删除',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_username` (`scope_id`,`username`,`deleted`),
  KEY `idx_scope_org` (`scope_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户';
CREATE TABLE IF NOT EXISTS `ai_tool_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tool_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具编码',
  `tool_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具名称',
  `tool_desc` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具描述(对应@AgentTool.value)',
  `tool_class` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具实现类全名(自动同步填充)',
  `tool_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具类型(PROJECT/MCP/WIKI/RAG)',
  `tool_category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具分类(如 SEARCH/EXEC/REVIEW/RAG/WIKI 等)',
  `tool_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `tool_config` json DEFAULT NULL COMMENT '工具配置',
  `tool_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '工具状态(0-禁用 1-启用)',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属分类编码（空为未分类）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tool_code` (`tool_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具配置';
CREATE TABLE IF NOT EXISTS `ai_tool_config_category` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码（创建时自定义，创建后不可修改）',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点ID（NULL为根节点）',
  `sort_num` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_scope` (`code`,`scope_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具配置分类';
CREATE TABLE IF NOT EXISTS `ai_tool_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tool_code` varchar(128) NOT NULL COMMENT '工具编码',
  `tool_name` varchar(128) DEFAULT NULL COMMENT '工具名称',
  `tool_desc` varchar(1024) DEFAULT NULL COMMENT '工具描述',
  `call_count` bigint(20) DEFAULT '0' COMMENT '调用次数',
  `success_count` bigint(20) DEFAULT '0' COMMENT '成功次数',
  `fail_count` bigint(20) DEFAULT '0' COMMENT '失败次数',
  `last_called_at` datetime DEFAULT NULL COMMENT '最后调用时间',
  `scope_id` varchar(64) NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tool_code` (`tool_code`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具使用量统计';
CREATE TABLE IF NOT EXISTS `ai_user_long_term_memory` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `memory_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆类型(SUMMARY/FACT/PREFERENCE)',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `related_session_ids` text COLLATE utf8mb4_unicode_ci COMMENT '关联会话ID列表(逗号分隔)',
  `importance_score` int(11) DEFAULT '0' COMMENT '重要性评分',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最后访问时间',
  `base_score` int(11) DEFAULT NULL COMMENT '基础分（衰减计算用，创建时初始分）',
  `access_count` int(11) DEFAULT '0' COMMENT '访问次数',
  `tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签（逗号分隔）',
  `embedding` blob COMMENT '序列化向量兜底',
  `valid_from` datetime DEFAULT NULL COMMENT '生效时间',
  `valid_until` datetime DEFAULT NULL COMMENT '失效时间（null表示永久有效）',
  `source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LLM_EXTRACTED' COMMENT '记忆来源（LLM_EXTRACTED/USER_DECLARED/SYSTEM_DEFAULT）',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_memory_type` (`memory_type`),
  KEY `idx_valid_until` (`valid_until`),
  KEY `idx_tags` (`tags`),
  KEY `idx_source` (`source`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `ai_user_workspace` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '归属作用域',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作区名称',
  `root_path` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '根目录绝对路径（canonical归一化，全局唯一）',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `approval_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '审批层级：MANUAL/AUTO/FULL_ACCESS/CUSTOM',
  `type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SERVER' COMMENT '工作区类型：SERVER服务器工作区',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态（1启用0停用）',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ws_root` (`root_path`),
  KEY `idx_ws_user` (`user_id`,`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户工作区';
CREATE TABLE IF NOT EXISTS `ai_webhook_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `url` varchar(512) NOT NULL COMMENT '回调地址',
  `secret` varchar(256) DEFAULT NULL COMMENT '签名密钥',
  `event_types` varchar(512) NOT NULL COMMENT '监听事件类型(逗号分隔,含运行时事件)',
  `retry_count` int(11) NOT NULL DEFAULT '3' COMMENT '重试次数',
  `retry_interval` bigint(20) NOT NULL DEFAULT '5000' COMMENT '重试间隔(毫秒)',
  `timeout` int(11) NOT NULL DEFAULT '10000' COMMENT '超时时间(毫秒)',
  `enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用(0否/1是)',
  `scope_id` varchar(64) DEFAULT 'default' COMMENT '租户隔离',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `agent_filter` varchar(512) DEFAULT NULL COMMENT 'agentCode过滤(逗号分隔,空=全部)',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_scope` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook外发配置';
CREATE TABLE IF NOT EXISTS `ai_workflow_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `definition_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流定义名称(唯一标识)',
  `display_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显示名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '工作流描述',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类(ORCHESTRATION/REPORT/DATA_PIPELINE/CUSTOM)',
  `version` int(11) NOT NULL DEFAULT '1' COMMENT '版本号',
  `definition_json` json NOT NULL COMMENT '工作流定义JSON(包含nodes/edges/errorStrategy等)',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态(0-禁用 1-启用)',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_version` (`definition_name`,`version`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';
CREATE TABLE IF NOT EXISTS `ai_workflow_execution_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流实例ID',
  `definition_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流定义名称',
  `definition_version` int(11) DEFAULT NULL COMMENT '定义版本号',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行状态',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户ID',
  `input_summary` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入摘要',
  `output_summary` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输出摘要',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_definition_name` (`definition_name`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行历史';
CREATE TABLE IF NOT EXISTS `ai_workflow_node_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流实例ID',
  `node_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点ID',
  `node_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型',
  `execution_order` int(11) DEFAULT NULL COMMENT '执行顺序(从1开始)',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点执行状态',
  `input_data` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '节点输入(JSON,超4KB截断)',
  `output_data` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '节点输出(JSON,超4KB截断)',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `retry_count` int(11) DEFAULT '0' COMMENT '重试次数',
  `iteration_count` int(11) DEFAULT '0' COMMENT '循环迭代次数',
  `branch_taken` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条件分支命中值(CONDITION节点)',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '节点耗时(毫秒)',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '作用域ID',
  `create_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_instance_node` (`instance_id`,`node_id`),
  KEY `idx_scope_start` (`scope_id`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点执行轨迹';
CREATE TABLE IF NOT EXISTS `ai_workflow_pause_history` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域ID',
  `instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实例ID',
  `definition_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流名称',
  `paused_node_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '暂停节点ID',
  `action` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作(PAUSE/RESUME)',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原因(暂停为用户输入，恢复为恢复方式说明)',
  `operator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  `operator_time` bigint(20) DEFAULT NULL COMMENT '操作时间(毫秒)',
  `pending_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联审批请求ID',
  `wait_duration_ms` bigint(20) DEFAULT NULL COMMENT '本次暂停等待时长(毫秒，恢复时计算)',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间(毫秒)',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_operator_time` (`operator_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流暂停恢复流水';
CREATE TABLE IF NOT EXISTS `ai_workflow_state` (
  `instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实例ID',
  `definition_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流名称',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行状态(RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED)',
  `paused_node_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '暂停节点ID',
  `pending_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '暂停关联的审批请求ID',
  `paused_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '暂停原因(用户输入)',
  `paused_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '暂停操作人',
  `paused_time` bigint(20) DEFAULT NULL COMMENT '暂停操作时间(毫秒)',
  `state_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '状态快照JSON',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间(毫秒)',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间(毫秒)',
  PRIMARY KEY (`instance_id`),
  KEY `idx_status` (`status`),
  KEY `idx_definition_name` (`definition_name`),
  KEY `idx_pending_request_id` (`pending_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流实例状态';
CREATE TABLE IF NOT EXISTS `harness_approval` (
  `approval_id` varchar(64) NOT NULL COMMENT '审批ID',
  `run_id` varchar(64) NOT NULL COMMENT '所属运行ID',
  `tool_call_id` varchar(64) NOT NULL COMMENT '工具调用ID（精确匹配键）',
  `tool_name` varchar(128) DEFAULT NULL COMMENT '工具名称',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `approver_id` varchar(64) DEFAULT NULL COMMENT '审批人用户ID',
  `state` varchar(32) NOT NULL COMMENT '审批状态',
  `reason` varchar(1024) DEFAULT NULL COMMENT '审批理由',
  `created_at` bigint(20) NOT NULL COMMENT '创建时间戳（毫秒）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`approval_id`),
  UNIQUE KEY `uk_harness_approval_tool` (`tool_call_id`),
  KEY `idx_harness_approval_scope` (`scope_id`,`state`),
  KEY `idx_harness_approval_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工审批记录';
CREATE TABLE IF NOT EXISTS `harness_checkpoint` (
  `run_id` varchar(64) NOT NULL COMMENT '所属运行ID',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `version` bigint(20) NOT NULL COMMENT '乐观锁版本号（按runId单调递增）',
  `iteration` int(11) NOT NULL COMMENT '迭代轮次',
  `messages` text COMMENT '对话历史快照（JSON）',
  `pending_tool_calls` text COMMENT '待执行工具调用（JSON）',
  `completed_tool_use_ids` text COMMENT '已完成工具调用ID（JSON）',
  `checkpoint_time` bigint(20) NOT NULL COMMENT '快照时间戳（毫秒）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`,`version`),
  KEY `idx_harness_checkpoint_session` (`scope_id`,`session_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行检查点';
CREATE TABLE IF NOT EXISTS `harness_long_term_memory` (
  `id` varchar(64) NOT NULL COMMENT '记忆ID（全局唯一）',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `content` text COMMENT '记忆内容',
  `metadata` text COMMENT '元数据（JSON）',
  `created_at` bigint(20) NOT NULL COMMENT '创建时间戳（毫秒）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_harness_memory_user` (`scope_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长期记忆';
CREATE TABLE IF NOT EXISTS `harness_run` (
  `run_id` varchar(64) NOT NULL COMMENT '运行ID（全局唯一）',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` varchar(64) DEFAULT NULL COMMENT '用户ID',
  `agent_name` varchar(128) DEFAULT NULL COMMENT 'Agent名称',
  `created_at` bigint(20) NOT NULL COMMENT '创建时间戳（毫秒）',
  `updated_at` bigint(20) NOT NULL COMMENT '最近更新时间戳（毫秒）',
  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `state` varchar(32) NOT NULL COMMENT '运行状态',
  `error` varchar(1024) DEFAULT NULL COMMENT '失败原因',
  `transitions` text COMMENT '状态迁移历史（JSON）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`),
  KEY `idx_harness_run_session` (`scope_id`,`session_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行记录';
CREATE TABLE IF NOT EXISTS `harness_run_lock` (
  `run_id` varchar(64) NOT NULL COMMENT '运行ID',
  `owner_node_id` varchar(128) NOT NULL COMMENT '持有节点ID',
  `expires_at` bigint(20) NOT NULL COMMENT '过期时间戳（毫秒）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行锁';
CREATE TABLE IF NOT EXISTS `harness_session_episode` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `message` text COMMENT '情节消息（JSON）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_harness_episode_session` (`scope_id`,`session_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话关键情节消息';
CREATE TABLE IF NOT EXISTS `harness_session_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `scope_id` varchar(64) NOT NULL COMMENT '租户标识',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `summary` text COMMENT '会话运行摘要',
  `summarized_message_count` int(11) NOT NULL DEFAULT '0' COMMENT '已摘要到的消息序号（不含）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_harness_session_summary` (`scope_id`,`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话运行摘要';
CREATE TABLE IF NOT EXISTS `harness_tool_execution` (
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `result` text COMMENT '工具结果（JSON）',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具执行记录';
CREATE TABLE IF NOT EXISTS `qrtz_blob_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `BLOB_DATA` blob COMMENT '二进制数据',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz二进制触发器';
CREATE TABLE IF NOT EXISTS `qrtz_calendars` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `CALENDAR_NAME` varchar(190) NOT NULL COMMENT '日历名',
  `CALENDAR` blob NOT NULL COMMENT '日历数据',
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz日历';
CREATE TABLE IF NOT EXISTS `qrtz_cron_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `CRON_EXPRESSION` varchar(120) NOT NULL COMMENT 'Cron表达式',
  `TIME_ZONE_ID` varchar(80) DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QuartzCron触发器';
CREATE TABLE IF NOT EXISTS `qrtz_fired_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `ENTRY_ID` varchar(95) NOT NULL COMMENT '触发记录ID',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `INSTANCE_NAME` varchar(190) NOT NULL COMMENT '执行节点实例名',
  `FIRED_TIME` bigint(20) NOT NULL COMMENT '实际触发时间',
  `SCHED_TIME` bigint(20) NOT NULL COMMENT '计划触发时间',
  `PRIORITY` int(11) NOT NULL COMMENT '优先级',
  `STATE` varchar(16) NOT NULL COMMENT '状态',
  `JOB_NAME` varchar(190) DEFAULT NULL COMMENT '任务名',
  `JOB_GROUP` varchar(190) DEFAULT NULL COMMENT '任务组',
  `IS_NONCONCURRENT` varchar(1) DEFAULT NULL COMMENT '是否禁止并发',
  `REQUESTS_RECOVERY` varchar(1) DEFAULT NULL COMMENT '是否请求恢复',
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_G_T` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_NAME`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_G_J` (`SCHED_NAME`,`TRIGGER_GROUP`,`JOB_NAME`),
  KEY `IDX_QRTZ_FT_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz已触发记录';
CREATE TABLE IF NOT EXISTS `qrtz_job_details` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `JOB_NAME` varchar(190) NOT NULL COMMENT '任务名',
  `JOB_GROUP` varchar(190) NOT NULL COMMENT '任务组',
  `DESCRIPTION` varchar(250) DEFAULT NULL COMMENT '描述',
  `JOB_CLASS_NAME` varchar(250) NOT NULL COMMENT '任务实现类全名',
  `IS_DURABLE` varchar(1) NOT NULL COMMENT '是否持久任务',
  `IS_NONCONCURRENT` varchar(1) NOT NULL COMMENT '是否禁止并发执行',
  `IS_UPDATE_DATA` varchar(1) NOT NULL COMMENT '是否更新任务数据',
  `REQUESTS_RECOVERY` varchar(1) NOT NULL COMMENT '失败后是否请求恢复',
  `JOB_DATA` blob COMMENT '任务数据（JSON序列化）',
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz任务详情';
CREATE TABLE IF NOT EXISTS `qrtz_locks` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `LOCK_NAME` varchar(40) NOT NULL COMMENT '锁名',
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz集群行锁';
CREATE TABLE IF NOT EXISTS `qrtz_paused_trigger_grps` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '暂停的触发器组',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz暂停触发器组';
CREATE TABLE IF NOT EXISTS `qrtz_scheduler_state` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `INSTANCE_NAME` varchar(190) NOT NULL COMMENT '节点实例名',
  `LAST_CHECKIN_TIME` bigint(20) NOT NULL COMMENT '最近检入时间',
  `CHECKIN_INTERVAL` bigint(20) NOT NULL COMMENT '检入间隔毫秒',
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz集群节点状态';
CREATE TABLE IF NOT EXISTS `qrtz_simple_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `REPEAT_COUNT` bigint(20) NOT NULL COMMENT '重复次数',
  `REPEAT_INTERVAL` bigint(20) NOT NULL COMMENT '重复间隔毫秒',
  `TIMES_TRIGGERED` bigint(20) NOT NULL COMMENT '已触发次数',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz简单触发器';
CREATE TABLE IF NOT EXISTS `qrtz_simprop_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `STR_PROP_1` varchar(512) DEFAULT NULL COMMENT '字符串属性1',
  `STR_PROP_2` varchar(512) DEFAULT NULL COMMENT '字符串属性2',
  `STR_PROP_3` varchar(512) DEFAULT NULL COMMENT '字符串属性3',
  `INT_PROP_1` int(11) DEFAULT NULL COMMENT '整型属性1',
  `INT_PROP_2` int(11) DEFAULT NULL COMMENT '整型属性2',
  `LONG_PROP_1` bigint(20) DEFAULT NULL COMMENT '长整型属性1',
  `LONG_PROP_2` bigint(20) DEFAULT NULL COMMENT '长整型属性2',
  `DEC_PROP_1` decimal(13,4) DEFAULT NULL COMMENT '十进制属性1',
  `DEC_PROP_2` decimal(13,4) DEFAULT NULL COMMENT '十进制属性2',
  `BOOL_PROP_1` varchar(1) DEFAULT NULL COMMENT '布尔属性1',
  `BOOL_PROP_2` varchar(1) DEFAULT NULL COMMENT '布尔属性2',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz属性型触发器';
CREATE TABLE IF NOT EXISTS `qrtz_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL COMMENT '调度器实例名',
  `TRIGGER_NAME` varchar(190) NOT NULL COMMENT '触发器名',
  `TRIGGER_GROUP` varchar(190) NOT NULL COMMENT '触发器组',
  `JOB_NAME` varchar(190) NOT NULL COMMENT '关联任务名',
  `JOB_GROUP` varchar(190) NOT NULL COMMENT '关联任务组',
  `DESCRIPTION` varchar(250) DEFAULT NULL COMMENT '描述',
  `NEXT_FIRE_TIME` bigint(20) DEFAULT NULL COMMENT '下次触发时间戳',
  `PREV_FIRE_TIME` bigint(20) DEFAULT NULL COMMENT '上次触发时间戳',
  `PRIORITY` int(11) DEFAULT NULL COMMENT '优先级',
  `TRIGGER_STATE` varchar(16) NOT NULL COMMENT '触发器状态',
  `TRIGGER_TYPE` varchar(8) NOT NULL COMMENT '触发器类型',
  `START_TIME` bigint(20) NOT NULL COMMENT '开始时间',
  `END_TIME` bigint(20) DEFAULT NULL COMMENT '结束时间',
  `CALENDAR_NAME` varchar(190) DEFAULT NULL COMMENT '日历名',
  `MISFIRE_INSTR` smallint(6) DEFAULT NULL COMMENT '错过触发策略',
  `JOB_DATA` blob COMMENT '任务数据',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_J` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_C` (`SCHED_NAME`,`CALENDAR_NAME`),
  KEY `IDX_QRTZ_T_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_STATE` (`SCHED_NAME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_STATE` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_G_STATE` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NEXT_FIRE_TIME` (`SCHED_NAME`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST` (`SCHED_NAME`,`TRIGGER_STATE`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz触发器';

-- =============================================================
-- 二、初始化数据
-- =============================================================

-- 平台管理员（默认账号 admin）
INSERT INTO `ai_system_user` VALUES (1,'default','admin','$2a$10$q.BRRlLc6s5V7KaoW3NiheCVoXnFo8W6P4NUtG8k9jh1iq.NBoJku','平台管理员',NULL,NULL,NULL,1,0,NULL,'2026-09-19 23:57:26',NULL,'2026-09-29 14:24:59');
INSERT INTO `ai_system_menu` VALUES (1101,'default','community-admin','overview',0,'GROUP','总览',NULL,'DashboardOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1102,'default','community-admin','chat',0,'GROUP','运行中心',NULL,'MessageOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:31:14');
INSERT INTO `ai_system_menu` VALUES (1103,'default','community-admin','model-agent',0,'GROUP','模型与 Agent',NULL,'RobotOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1104,'default','community-admin','tool-skill',0,'GROUP','工具与技能',NULL,'ToolOutlined',4,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1105,'default','community-admin','knowledge',0,'GROUP','知识中心',NULL,'BookOutlined',5,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1106,'default','community-admin','orchestration',0,'GROUP','编排引擎',NULL,'ApartmentOutlined',6,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1107,'default','community-admin','evaluation-evolution',0,'GROUP','评测与进化',NULL,'TrophyOutlined',7,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1108,'default','community-admin','governance',0,'GROUP','治理中心',NULL,'SafetyOutlined',8,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1109,'default','community-admin','open-capability',0,'GROUP','开放能力',NULL,'GlobalOutlined',9,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1110,'default','community-admin','system',0,'GROUP','系统管理',NULL,'AuditOutlined',10,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1201,'default','community-admin','dashboard',1101,'PAGE','工作台','/dashboard','DashboardOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1202,'default','community-admin','agent-chat',1102,'PAGE','Agent 对话','/chat','MessageOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1203,'default','community-admin','conversations',1102,'PAGE','会话管理','/conversations','HistoryOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1204,'default','community-admin','models',1103,'PAGE','模型管理','/models','ThunderboltOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:24:03');
INSERT INTO `ai_system_menu` VALUES (1205,'default','community-admin','agents',1103,'PAGE','Agent 管理','/agents','AppstoreOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1206,'default','community-admin','agent-test',1103,'PAGE','Agent 测试','/agent-test','ExperimentOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1207,'default','community-admin','tools',1104,'PAGE','工具管理','/tools','ToolOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:52:21');
INSERT INTO `ai_system_menu` VALUES (1208,'default','community-admin','mcp',1104,'PAGE','MCP 接入','/mcp','DeploymentUnitOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1209,'default','community-admin','skills',1104,'PAGE','技能管理','/skills','StarOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:52:21');
INSERT INTO `ai_system_menu` VALUES (1210,'default','community-admin','skill-usage',1104,'PAGE','技能用量','/skill-usage','BarChartOutlined',4,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1211,'default','community-admin','knowledge-bases',1105,'PAGE','知识库','/knowledge-bases','BookOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1212,'default','community-admin','data-sources',1105,'PAGE','数据源','/data-sources','DatabaseOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1213,'default','community-admin','vector-stores',1105,'PAGE','向量存储','/vector-stores','ClusterOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1215,'default','community-admin','workflows',1106,'PAGE','工作流','/workflows','ApartmentOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1216,'default','community-admin','evaluations',1107,'PAGE','Agent 评测','/evaluations','TrophyOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1217,'default','community-admin','governance-dashboard',1108,'PAGE','治理驾驶舱','/governance-dashboard','DashboardOutlined',1,1,1,NULL,'ai.governance.enterprise',0,NULL,'2026-09-15 00:01:36',NULL,'2026-10-05 17:13:13');
INSERT INTO `ai_system_menu` VALUES (1218,'default','community-admin','governance-inbox',1108,'PAGE','治理收件箱','/governance-inbox','NotificationOutlined',2,1,1,NULL,'ai.governance.enterprise',0,NULL,'2026-09-15 00:01:36',NULL,'2026-10-05 17:13:13');
INSERT INTO `ai_system_menu` VALUES (1219,'default','community-admin','trace-runs',1108,'PAGE','运行回放','/trace-runs','HistoryOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1221,'default','community-admin','approvals',1108,'PAGE','变更审批','/approvals','SolutionOutlined',5,1,1,NULL,'ai.governance.enterprise',0,NULL,'2026-09-15 00:01:36',NULL,'2026-10-05 17:13:13');
INSERT INTO `ai_system_menu` VALUES (1223,'default','community-admin','memory',1108,'PAGE','记忆检索','/memory','CloudServerOutlined',7,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:11:08');
INSERT INTO `ai_system_menu` VALUES (1224,'default','community-admin','mcp-expose',1109,'PAGE','MCP 出口','/mcp-expose','ApiOutlined',6,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1225,'default','community-admin','a2a-card',1109,'PAGE','A2A 卡片','/a2a-card','DeploymentUnitOutlined',7,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1226,'default','community-admin','agent-memory',1108,'PAGE','记忆条目','/agent-memory','DatabaseOutlined',10,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-25 17:11:08');
INSERT INTO `ai_system_menu` VALUES (1227,'default','community-admin','triggers',1108,'PAGE','触发规则','/triggers','ThunderboltOutlined',11,1,1,NULL,'ai.governance.enterprise',0,NULL,'2026-09-15 00:01:36',NULL,'2026-10-05 17:13:13');
INSERT INTO `ai_system_menu` VALUES (1228,'default','community-admin','open-capabilities',1109,'PAGE','开放能力','/open-capabilities','GlobalOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1232,'default','community-admin','connectors',1109,'PAGE','连接器','/connectors','ApiOutlined',5,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1233,'default','community-admin','users',1110,'PAGE','用户管理','/users','UserOutlined',1,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1234,'default','community-admin','menus',1110,'PAGE','菜单管理','/menus','ProfileOutlined',2,1,1,NULL,NULL,0,NULL,'2026-09-15 00:01:36',NULL,'2026-09-15 00:01:36');
INSERT INTO `ai_system_menu` VALUES (1290,'default','community-admin','workbench',1102,'PAGE','本地工作台','/workbench','FolderOpenOutlined',3,1,1,NULL,NULL,0,NULL,'2026-09-21 03:37:28',NULL,'2026-09-25 17:33:51');
INSERT INTO `ai_system_menu` VALUES (1291,'default','community-admin','tool-usage',1104,'PAGE','工具用量','/tool-usage','BarChartOutlined',5,1,1,NULL,NULL,0,NULL,'2026-09-24 09:49:08',NULL,'2026-09-24 09:49:08');
INSERT INTO `ai_system_menu` VALUES (1292,'default','community-admin','knowledge-bases-rag',1105,'PAGE','RAG 检索','/knowledge-bases/rag','SearchOutlined',5,1,1,NULL,NULL,0,NULL,'2026-09-24 10:10:56',NULL,'2026-09-24 10:10:56');
INSERT INTO `ai_tool_config` VALUES (2081781786643271681,'HumanApprovalTool','HumanApprovalTool','当操作可能带来风险、涉及敏感数据、或需要人工确认时，调用此工具请求人工审批。审批通过后可继续执行操作。参数options和inputFields为空字符串时表示仅需确认无需选择或填表。','com.yangqiongai.ai.agent.tool.approval.HumanApprovalTool','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-09-14 08:40:02');
INSERT INTO `ai_tool_config` VALUES (2081781786727157762,'CalculatorTool','CalculatorTool','幂运算。输入底数和指数，返回 base 的 exponent 次幂。适用于复利计算、面积/体积计算等场景。','com.yangqiongai.ai.agent.tool.builtin.CalculatorTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-04 22:51:03');
INSERT INTO `ai_tool_config` VALUES (2081781786811043841,'DateTimeTool','DateTimeTool','获取当前日期，返回年月日和星期几。当只需要日期不需要时间时调用此工具。','com.yangqiongai.ai.agent.tool.builtin.DateTimeTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-04 22:51:03');
INSERT INTO `ai_tool_config` VALUES (2081781786920095745,'ExchangeRateTool','ExchangeRateTool','获取汇率工具支持的所有货币代码列表。','com.yangqiongai.ai.agent.tool.builtin.ExchangeRateTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-04 21:20:37');
INSERT INTO `ai_tool_config` VALUES (2081781787045924865,'HolidayTool','HolidayTool','查询指定日期之后最近的节假日。输入参考日期（格式 yyyy-MM-dd），返回下一个节假日的日期和名称。','com.yangqiongai.ai.agent.tool.builtin.HolidayTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-05 01:55:32');
INSERT INTO `ai_tool_config` VALUES (2081781787154976770,'HttpRequestTool','HttpRequestTool','发送带自定义请求头的HTTP POST请求。headersJson为JSON格式字符串。适用于需要认证的API调用。','com.yangqiongai.ai.agent.tool.builtin.HttpRequestTool','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-05 01:55:32');
INSERT INTO `ai_tool_config` VALUES (2081781787276611585,'QrCodeTool','QrCodeTool','生成vCard名片二维码。输入姓名、电话、邮箱、组织，生成扫码即可保存联系人的名片二维码。','com.yangqiongai.ai.agent.tool.builtin.QrCodeTool','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-05 01:55:32');
INSERT INTO `ai_tool_config` VALUES (2081781787381469186,'UnitConverterTool','UnitConverterTool','温度单位换算。支持单位：C(摄氏度)、F(华氏度)、K(开尔文)。','com.yangqiongai.ai.agent.tool.builtin.UnitConverterTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-10-05 01:55:32');
INSERT INTO `ai_tool_config` VALUES (2081781787456966658,'CodeExecutionTool','CodeExecutionTool','执行沙箱代码，支持多种编程语言的代码执行','com.yangqiongai.ai.agent.tool.sandbox.CodeExecutionTool','TOOL','CUSTOM',0,NULL,1,NULL,'advanced','default','system','2026-07-28 00:40:30','system','2026-09-24 23:34:01');
INSERT INTO `ai_tool_config` VALUES (2081781787540852737,'DynamicSkillInvoker','DynamicSkillInvoker','动态执行技能脚本，根据技能名称和参数执行对应的技能','com.yangqiongai.ai.agent.tool.sandbox.DynamicSkillInvoker','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:30','system','2026-09-14 08:40:02');
INSERT INTO `ai_tool_config` VALUES (2081781787956088834,'ContextAwareRagTool','ContextAwareRagTool','从指定知识库或文档中检索与问题相关的上下文，返回结果可作为回答依据','com.yangqiongai.ai.agent.tool.rag.ContextAwareRagTool','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-07-28 00:40:31','system','2026-09-14 08:40:02');
INSERT INTO `ai_tool_config` VALUES (2098847219686866946,'WorkflowDesignTool','WorkflowDesignTool','按名称查询现有工作流定义JSON，用于修改已有流程。工作流不存在时返回空结果。','com.yangqiongai.ai.platform.api.workflow.tool.WorkflowDesignTool','TOOL','CUSTOM',0,NULL,1,NULL,NULL,'default','system','2026-09-13 02:52:27','system','2026-10-05 01:55:31');
INSERT INTO `ai_tool_config` VALUES (2101360299478093826,'WebSearchTool','WebSearchTool','指定搜索源执行Web搜索，sourceName可选值: duckduckgo、tavily等，不传则使用默认搜索源','com.yangqiongai.ai.agent.tool.search.WebSearchTool','TOOL','BUILTIN',0,NULL,1,NULL,NULL,'default','system','2026-09-20 01:18:31','system','2026-10-05 01:55:32');
-- 演示：工具分类（基础/查询/进阶）
INSERT INTO `ai_tool_config_category` VALUES (209930600200000011,'basic','基础工具',NULL,1,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_tool_config_category` VALUES (209930600200000012,'info','信息查询',NULL,2,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_tool_config_category` VALUES (209930600200000013,'advanced','进阶能力',NULL,3,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_model_info` VALUES (1,'bge-base-zh-djl','bge-base-zh-v1.5','djl','EMBEDDING',NULL,NULL,'{\"engine\": \"PyTorch\", \"modelUrl\": \"djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5\", \"normalize\": true, \"dimensions\": 768, \"maxSeqLength\": 512}',0,1,NULL,'default','system','2026-07-28 00:24:29',NULL,NULL,1,0);
INSERT INTO `ai_model_info` VALUES (2,'deepseek','deepseek-flash','openai','deepseek','https://api.deepseek.com',NULL,'{\"max-tokens\": 4096, \"temperature\": 0.1}',1,1,NULL,'default',NULL,NULL,NULL,NULL,1,0);
INSERT INTO `ai_agent` VALUES (1,'default','默认对话','默认通用对话处理器',NULL,'CHAT',NULL,1,1,'CHAT',NULL,1,NULL,NULL,NULL,'default',NULL,NULL,'admin','2026-09-12 11:17:43');
INSERT INTO `ai_agent` VALUES (8,'orchestration','多代理编排','多代理编排处理器',NULL,'ORCHESTRATION',NULL,1,8,'ORCHESTRATION',NULL,1,NULL,NULL,NULL,'default',NULL,NULL,'system','2026-09-11 21:50:21');
INSERT INTO `ai_agent` VALUES (9,'directLlm','直接大模型','直接调用大模型处理器（不走ReAct循环，保留安全/重试/历史/任务记录）',NULL,'CHAT',NULL,1,9,'CHAT',NULL,1,NULL,NULL,'直接LLM调用，无工具无多轮推理','default',NULL,'2026-07-28 00:24:29','system','2026-09-11 21:50:21');
INSERT INTO `ai_agent` VALUES (2081781724169113601,'localAgent','Local','LocalAgentProcessor 处理器',NULL,'CHAT',NULL,1,99,'CHAT',NULL,1,NULL,NULL,NULL,'default','system','2026-07-28 00:40:15','system','2026-09-11 21:50:21');
INSERT INTO `ai_agent` VALUES (2098847202142093314,'workflowDesigner','WorkflowDesigner','WorkflowDesignerAgentProcessor 处理器',NULL,'CHAT',NULL,1,99,'CHAT',NULL,1,NULL,NULL,NULL,'default','system','2026-09-13 02:52:22','system','2026-09-13 02:52:22');
INSERT INTO `ai_agent` VALUES (2099305363957350402,'support-agent','客服专员','演示客服专员智能体',NULL,'general','customer',1,16,NULL,'{\"topK\": 8, \"model\": \"deepseek\", \"systemPrompt\": \"你是客服专员(1.0.1)\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:12:57','system','2026-09-21 01:01:19');
INSERT INTO `ai_agent` VALUES (209930600100000001,'data-analyst','数据分析师','演示数据分析智能体',NULL,'general','office',1,10,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是数据分析师，擅长数据解读、指标分析与可视化建议，回答时给出结构化的分析思路与结论。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent` VALUES (209930600100000002,'copywriter','文案策划','演示文案创作智能体',NULL,'general','content',1,11,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是文案策划，擅长营销文案、产品介绍与新媒体内容创作，能根据场景切换语言风格。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent` VALUES (209930600100000003,'code-assistant','编程专家','演示编程智能体',NULL,'general','dev-support',1,12,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是编程专家，擅长代码编写、调试与优化，回答时附示例代码并解释关键逻辑。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent` VALUES (209930600100000004,'translator','资深译员','演示翻译智能体',NULL,'general','content',1,13,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是资深译员，支持中英等多语言互译，译文忠实原意、表达自然，专业术语准确。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent` VALUES (209930600100000005,'travel-planner','旅行规划师','演示旅行规划智能体',NULL,'general','office',1,14,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是旅行规划师，擅长行程安排、景点推荐与预算规划，输出清晰的每日日程表。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent` VALUES (209930600100000006,'meeting-notes','会议纪要专员','演示会议纪要智能体',NULL,'general','office',1,15,NULL,'{\"model\": \"deepseek\", \"systemPrompt\": \"你是会议纪要专员，擅长将讨论内容整理为结构化纪要，输出议题、结论与待办事项。\"}',1,NULL,NULL,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
-- 演示：智能体目录（分组挂载于 ai_agent.directory_code）
INSERT INTO `ai_agent_directory` VALUES (209930600200000001,'office','办公提效',NULL,1,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent_directory` VALUES (209930600200000002,'content','内容创作',NULL,2,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent_directory` VALUES (209930600200000003,'dev-support','研发支持',NULL,3,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_agent_directory` VALUES (209930600200000004,'customer','客户服务',NULL,4,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');
INSERT INTO `ai_mcp_server_expose` VALUES (2099306858199142402,'default','AGENT','support-agent','客服专员(MCP出口)','演示智能体的MCP出口暴露',0,1,NULL,'system','2026-09-14 09:18:53','system','2026-09-25 13:07:34');
INSERT INTO `ai_mcp_server_expose` VALUES (2103363530035134466,'default','TOOL','add','加法计算','精确加法运算工具，输入两个数字返回它们的和',0,1,NULL,'system','2026-09-25 13:58:39','system','2026-09-25 13:58:39');

-- 演示：客服专员 Agent 定义/版本/发布链（1.0.0 -> 1.0.1）
INSERT INTO `ai_agent_definition` VALUES (2099305363932184578,'support-agent','客服专员','演示客服专员智能体','general',2099306735532527618,'ENABLED',0,0,80.00,'default','system','2026-09-14 09:12:57','system','2026-09-14 09:12:57',1);
INSERT INTO `ai_agent_version` VALUES (2099305503040471041,'support-agent','1.0.0','{\"model\":\"deepseek\",\"systemPrompt\":\"你是客服专员\"}','8d7bb2aa411891848e3acf709de1ad600b5b8ce2b1a06b8c461996e8713d71c1','初始版本','PUBLISHED',NULL,NULL,NULL,'admin','2026-09-14 09:13:37','2026-09-14 09:13:38','default','system','2026-09-14 09:13:30','system','2026-09-14 09:13:37',NULL);
INSERT INTO `ai_agent_version` VALUES (2099306735532527618,'support-agent','1.0.1','{\"model\":\"deepseek\",\"systemPrompt\":\"你是客服专员(1.0.1)\"}','c30dd586ab0de0a70c7a82e0af368f1668ab82bf2b712d28bf0595e3929a50c5','1.0.1版本','PUBLISHED',NULL,NULL,NULL,'admin','2026-09-14 09:18:31','2026-09-14 09:18:31','default','system','2026-09-14 09:18:24','system','2026-09-14 09:18:31',NULL);
INSERT INTO `ai_agent_release` VALUES (2099306767610564609,'R202609140918312581319','support-agent','PUBLISH',2099305503040471041,2099306735532527618,NULL,'SUCCESS','[{\"gate\":\"evaluation\",\"passed\":true,\"message\":null,\"evidenceId\":null},{\"gate\":\"approval\",\"passed\":true,\"message\":null,\"evidenceId\":null}]',NULL,'admin','default','system','2026-09-14 09:18:31','system','2026-09-14 09:18:31');

-- 演示：评测数据集、评测用例与采样配置
INSERT INTO `ai_eval_dataset` VALUES (2026091500000001,'ds-support-demo','客服质量评测集','客服专员回答质量评测用例集','ENABLED',3,'default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');
INSERT INTO `ai_eval_dataset_case` VALUES (2026091500000011,2026091500000001,'SUP-001','退款政策问答','我想退款，下单7天了还能退吗？','7天无理由退款范围内可退，请提供订单号发起售后申请。',NULL,'LLM_JUDGE','default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');
INSERT INTO `ai_eval_dataset_case` VALUES (2026091500000012,2026091500000001,'SUP-002','物流进度查询','我的订单什么时候能到？','请告知订单号，我将为您查询最新物流进度与预计送达时间。',NULL,'LLM_JUDGE','default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');
INSERT INTO `ai_eval_dataset_case` VALUES (2026091500000013,2026091500000001,'SUP-003','发票开具咨询','怎么开电子发票？','订单完成后可在订单详情页申请电子发票，1个工作日内开具。',NULL,'LLM_JUDGE','default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');
INSERT INTO `ai_eval_sampling_config` VALUES (2026091500000201,'support-agent',0.5000,50,1,2026091500000001,1,'default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');
INSERT INTO `ai_eval_sampling_config` VALUES (2026091500000202,'default',0.3000,30,0,NULL,1,'default','admin','2026-09-15 09:35:44',NULL,'2026-09-15 09:35:44');

-- 演示：MCP 服务器配置（open-websearch）
INSERT INTO `ai_mcp_server_config` VALUES (2,'open-websearch','多引擎免费搜索','stdio','{\"command\": \"npx.cmd\", \"envConfig\": {\"DEFAULT_SEARCH_ENGINE\": \"duckduckgo\"}, \"commandArgs\": [\"-y\", \"open-websearch@latest\"]}',NULL,NULL,0,'免费多引擎搜索MCP（DuckDuckGo/搜狗/百度/Bing/Brave），无需API Key',NULL,NULL,'default','system','2026-07-11 20:55:38',NULL,NULL);

-- 演示：最简可运行工作流（开始→智能体→结束）
INSERT INTO `ai_workflow_definition` VALUES (209930600300000001,'demo_simple_qa','演示·智能问答流程','最简演示流程：开始节点注入初始问题，由默认对话智能体作答后结束，可直接一键运行体验工作流执行链路','CUSTOM',1,'{"name":"demo_simple_qa","description":"最简演示流程：开始→智能体→结束","nodes":[{"id":"start","name":"开始","type":"START","config":{"initialPrompt":"用两句话介绍智能体平台能帮助用户做什么"},"position":{"x":400,"y":60}},{"id":"agent1","name":"智能体","type":"AGENT","config":{"agentCode":"default"},"position":{"x":400,"y":200}},{"id":"end","name":"结束","type":"END","position":{"x":400,"y":340}}],"edges":[{"id":"e1","sourceId":"start","targetId":"agent1","type":"NORMAL"},{"id":"e2","sourceId":"agent1","targetId":"end","type":"NORMAL"}],"stateConfig":{"persistEnabled":true,"ttlHours":48},"errorStrategy":"STOP","maxRetries":0,"nodeTimeoutSeconds":120}',1,NULL,'default','system','2026-09-14 09:30:00','system','2026-09-14 09:30:00');

SET FOREIGN_KEY_CHECKS = 1;
