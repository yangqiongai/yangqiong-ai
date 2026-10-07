/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.common.exception;

/**
 * AI平台异常码
 * @author yangqiong
 */
public enum AiErrorCode {

    // 通用异常 1xxxx
    UNKNOWN(10000, "未知异常"),
    PARAM_ERROR(10001, "参数错误"),
    BAD_REQUEST(10001, "请求参数错误"),
    NOT_FOUND(10002, "资源不存在"),
    UNAUTHORIZED(10003, "未授权"),
    FORBIDDEN(10004, "禁止访问"),

    // Agent 异常 2xxxx
    AGENT_RUNTIME_ERROR(20001, "Agent运行时异常"),
    AGENT_TASK_NOT_FOUND(20002, "任务不存在"),
    AGENT_TASK_FAILED(20003, "Agent任务执行失败"),
    AGENT_TASK_TIMEOUT(20004, "Agent任务执行超时"),
    AGENT_TASK_CANCELLED(20005, "Agent任务已取消"),
    AGENT_ASSEMBLY_ERROR(20006, "Agent装配异常"),
    AGENT_INTENT_ROUTE_ERROR(20007, "意图路由异常"),
    AGENT_INPUT_BLOCKED(20008, "输入内容被安全护栏拦截"),
    AGENT_OUTPUT_BLOCKED(20009, "输出内容被安全护栏拦截"),
    TEXT2SQL_VALIDATION_FAILED(20010, "SQL校验失败"),
    TEXT2SQL_GENERATION_FAILED(20011, "SQL生成失败"),
    TEXT2SQL_EXECUTION_FAILED(20012, "SQL执行失败"),
    TEXT2SQL_DATASOURCE_NOT_FOUND(20013, "数据源不存在"),
    TEXT2SQL_RATE_LIMITED(20014, "查询频率超限"),
    TEXT2SQL_PROMPT_INJECTION(20015, "疑似Prompt Injection"),
    TEXT2SQL_CONCURRENT_LIMIT(20016, "并发查询数超限"),
    TEXT2SQL_CIRCUIT_OPEN(20017, "服务暂不可用"),

    // Skill 异常 3xxxx
    SKILL_NOT_FOUND(30001, "技能不存在"),
    SKILL_LOAD_FAILED(30002, "技能加载失败"),
    SKILL_PARSE_ERROR(30003, "技能解析异常"),
    SKILL_GENERATE_FAILED(30004, "技能生成失败"),
    SKILL_VALIDATION_FAILED(30005, "技能验证失败"),
    SKILL_RESUME_TOKEN_INVALID(30006, "技能生成恢复令牌无效"),
    SKILL_CHANGE_PENDING_APPROVAL(30007, "技能变更已转审批"),

    // MCP 异常 4xxxx
    MCP_CLIENT_INIT_FAILED(40001, "MCP客户端初始化失败"),
    MCP_CLIENT_CONNECTION_ERROR(40002, "MCP客户端连接异常"),
    MCP_TOOL_CALL_FAILED(40003, "MCP工具调用失败"),
    MCP_TOOL_NOT_FOUND(40004, "MCP工具不存在"),
    MCP_TOOL_FORBIDDEN(40005, "MCP工具被策略禁止"),

    // RAG 异常 5xxxx
    RAG_RETRIEVE_FAILED(50001, "RAG检索失败"),
    RAG_EMBEDDING_FAILED(50002, "向量化失败"),
    RAG_DOCUMENT_PARSE_ERROR(50003, "文档解析异常"),
    RAG_KB_NOT_FOUND(50004, "知识库不存在"),
    RAG_DOCUMENT_NOT_FOUND(50005, "文档不存在"),
    RAG_ADMIN_TOKEN_INVALID(50006, "管理令牌无效或未配置"),
    RAG_INGEST_FAILED(50019, "文档入库失败"),
    RAG_REPROCESS_FAILED(50020, "文档重处理失败"),
    RAG_DOCUMENT_ALREADY_EXISTS(50021, "文档已存在"),
    RAG_KB_DOCUMENT_NOT_FOUND(50022, "知识库文档不存在"),

    // 知识图谱RAG异常 50007-50018
    RAG_GRAPH_EXTRACT_FAILED(50007, "图谱抽取失败"),
    RAG_GRAPH_SCHEMA_ERROR(50008, "图谱Schema异常"),
    RAG_GRAPH_ENTITY_RESOLVE_FAILED(50009, "实体融合失败"),
    RAG_GRAPH_COMMUNITY_FAILED(50010, "社区发现失败"),
    RAG_GRAPH_INDEX_FAILED(50011, "图谱索引构建失败"),
    RAG_GRAPH_RETRIEVE_FAILED(50012, "图谱检索失败"),
    RAG_GRAPH_VECTOR_STORE_FAILED(50013, "图谱向量存储失败"),
    RAG_GRAPH_BUDGET_EXCEEDED(50014, "图谱LLM预算超限"),
    RAG_GRAPH_BUILD_FAILED(50015, "图谱构建失败"),
    RAG_GRAPH_VERSION_FAILED(50016, "图谱版本管理失败"),
    RAG_GRAPH_PERSISTENCE_FAILED(50017, "图谱持久化失败"),
    RAG_GRAPH_LOCK_FAILED(50018, "图谱锁获取失败"),

    // Wiki 异常 6xxxx
    WIKI_PROJECT_NOT_FOUND(60001, "Wiki项目不存在"),
    WIKI_INGEST_FAILED(60002, "Wiki ingest失败"),
    WIKI_SYNC_FAILED(60003, "Wiki同步失败"),
    WIKI_GRAPH_BUILD_FAILED(60004, "知识图谱构建失败"),

    // 模型异常 7xxxx
    MODEL_NOT_FOUND(70001, "模型不存在"),
    MODEL_CALL_FAILED(70002, "模型调用失败"),
    MODEL_CONFIG_ERROR(70003, "模型配置异常"),

    // 存储异常 8xxxx
    STORAGE_MINIO_ERROR(80001, "MinIO存储异常"),
    STORAGE_QDRANT_ERROR(80002, "Qdrant向量库异常"),
    STORAGE_NEO4J_ERROR(80003, "Neo4j图数据库异常"),
    STORAGE_LOCAL_ERROR(80004, "本地文件存储异常"),

    // 对话异常 9xxxx
    CONVERSATION_NOT_FOUND(90001, "会话不存在"),
    CONVERSATION_MEMORY_ERROR(90002, "对话记忆异常"),
    CONVERSATION_QUOTA_EXCEEDED(90003, "会话配额超限"),
    CONVERSATION_TOKEN_BUDGET_EXCEEDED(90004, "Token预算超限"),
    AGENT_QUOTA_EXCEEDED(90005, "Agent配额超限"),

    // 租户异常 10xxxx
    TENANT_NOT_FOUND(100001, "租户不存在"),
    TENANT_DISABLED(100002, "租户已停用"),
    TENANT_DEFAULT_CANNOT_DELETE(100003, "默认租户不允许删除"),
    TENANT_DEFAULT_CANNOT_DISABLE(100004, "默认租户不允许停用"),
    TENANT_HAS_DATA(100005, "租户存在业务数据，无法删除"),
    TENANT_MEMBER_EXISTS(100006, "租户成员已存在"),
    TENANT_MEMBER_NOT_FOUND(100007, "租户成员不存在"),
    TENANT_PERSONAL_CANNOT_ADD_MEMBER(100008, "个人版租户不允许添加成员"),
    USER_NOT_FOUND(100009, "用户不存在"),
    TENANT_CODE_DUPLICATE(100010, "企业标识已被其他租户使用"),
    TENANT_CODE_INVALID(100011, "企业标识格式不正确（2-64位，小写字母开头，可含数字和中划线）"),

    // 套餐异常 11xxxx
    PLAN_NOT_FOUND(110001, "套餐不存在"),
    PLAN_OFFLINE(110002, "套餐已下架"),
    PLAN_TIER_INVALID(110003, "套餐层级非法，无法升降级"),
    TENANT_DOWNGRADE_QUOTA_EXCEEDED(110004, "当前用量超出目标套餐上限，无法降级"),
    PLAN_CAPABILITY_NOT_ALLOWED(110005, "当前套餐不支持该能力"),
    PLAN_MODEL_NOT_ALLOWED(110006, "当前套餐不允许使用该模型"),
    PLAN_SKILL_NOT_ALLOWED(110007, "当前套餐不允许使用该技能"),
    PLAN_TOOL_NOT_ALLOWED(110008, "当前套餐不允许使用该工具"),
    PLAN_USER_LIMIT_EXCEEDED(110009, "当前套餐成员数量已达上限"),
    PLAN_KB_LIMIT_EXCEEDED(110010, "当前套餐知识库数量已达上限"),
    PLAN_STORAGE_LIMIT_EXCEEDED(110011, "当前套餐存储空间已超上限"),
    PLAN_TOKEN_QUOTA_EXCEEDED(110012, "当前套餐月度Token配额已用尽"),
    PLAN_SESSION_LIMIT_EXCEEDED(110013, "当前套餐并发会话数已达上限"),
    PLAN_IN_USE(110014, "套餐正在被租户使用，无法删除"),
    PLAN_EXPIRED(110015, "租户套餐已过期");

    private final int code;

    private final String message;

    AiErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
