-- =====================================================
-- V33: 将业务表 tenant_id 列重命名为 scope_id
-- =====================================================
-- 脚本说明：
--   1. 本脚本位于 ai-server 模块，所有版本均会执行
--   2. 将 V32a 中添加的 tenant_id 列重命名为 scope_id，与代码中 scopeId 命名一致
--   3. 同时将索引 idx_tenant_id 重命名为 idx_scope_id
--   4. ai_tenant 等管理表的 tenant_id 列不做修改（属于 ai-tenant 模块内部字段）
-- =====================================================


-- =====================================================
-- 知识库模块（9 张）
-- =====================================================

ALTER TABLE ai_knowledge_base RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_knowledge_base RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_knowledge_base_config RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_knowledge_base_config RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_kb_document RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_kb_document RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_kb_slice_record RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_kb_slice_record RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_kb_parent_chunk RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_kb_parent_chunk RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_document_processing_job RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_document_processing_job RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_file_type_task_mapping RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_file_type_task_mapping RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_recall_record RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_recall_record RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_kb_api_key RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_kb_api_key RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- Wiki 模块（6 张）
-- =====================================================

ALTER TABLE ai_wiki_project RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_project RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_wiki_compile_task RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_compile_task RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_wiki_source_bind RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_source_bind RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_wiki_review_record RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_review_record RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_wiki_page_index RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_page_index RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_wiki_graph_snapshot RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_wiki_graph_snapshot RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 对话模块（5 张）
-- 注：ai_session_tag 实际表名为 bss_ai_session_tag
-- =====================================================

ALTER TABLE ai_conversation_session RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_conversation_session RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_chat_memory RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_chat_memory RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE bss_ai_session_tag RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE bss_ai_session_tag RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_conversation_summary_history RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_conversation_summary_history RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_user_long_term_memory RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_user_long_term_memory RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- Agent 模块（4 张）
-- =====================================================

ALTER TABLE ai_agent_task RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_task RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_task_step RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_task_step RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_task_event RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_task_event RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_schedule RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_schedule RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 技能模块（4 张）
-- =====================================================

ALTER TABLE ai_agent_skill_config RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_skill_config RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_skill_version RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_skill_version RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_skill_gen_draft RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_skill_gen_draft RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_skill_usage RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_skill_usage RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- MCP 模块（1 张）
-- =====================================================

ALTER TABLE ai_mcp_server_config RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_mcp_server_config RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 工具模块（2 张）
-- =====================================================

ALTER TABLE ai_tool_config RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_tool_config RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_agent_type_tool_rel RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_agent_type_tool_rel RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 模型模块（1 张）
-- =====================================================

ALTER TABLE ai_model_info RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_model_info RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 工作流模块（2 张）
-- =====================================================

ALTER TABLE ai_workflow_definition RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_workflow_definition RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_workflow_execution_history RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_workflow_execution_history RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- Text2SQL 模块（5 张）
-- =====================================================

ALTER TABLE ai_text2sql_datasource RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_text2sql_datasource RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_text2sql_table_registry RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_text2sql_table_registry RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_text2sql_column_registry RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_text2sql_column_registry RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_text2sql_training_corpus RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_text2sql_training_corpus RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_text2sql_execution_history RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_text2sql_execution_history RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 图谱模块（11 张）
-- =====================================================

ALTER TABLE ai_graph_schema RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_schema RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_entity RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_entity RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_entity_alias RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_entity_alias RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_entity_tag RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_entity_tag RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_entity_domain RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_entity_domain RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_triple RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_triple RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_community RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_community RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_community_member RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_community_member RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_build_record RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_build_record RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_build_staging RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_build_staging RENAME INDEX idx_tenant_id TO idx_scope_id;

ALTER TABLE ai_graph_version RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_graph_version RENAME INDEX idx_tenant_id TO idx_scope_id;


-- =====================================================
-- 审批模块（1 张）
-- =====================================================

ALTER TABLE ai_pending_request RENAME COLUMN tenant_id TO scope_id;
ALTER TABLE ai_pending_request RENAME INDEX idx_tenant_id TO idx_scope_id;
