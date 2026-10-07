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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiException 单元测试")
class AiExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("仅AiErrorCode：code和message来自枚举")
        void shouldSetCodeAndMessageFromErrorCode() {
            AiException ex = new AiException(AiErrorCode.PARAM_ERROR);

            assertThat(ex.getCode()).isEqualTo(10001);
            assertThat(ex.getMessage()).isEqualTo("参数错误");
        }

        @Test
        @DisplayName("AiErrorCode + detail：message格式为 enumMessage: detail")
        void shouldCombineMessageWithDetail() {
            AiException ex = new AiException(AiErrorCode.NOT_FOUND, "用户ID=999");

            assertThat(ex.getCode()).isEqualTo(10002);
            assertThat(ex.getMessage()).isEqualTo("资源不存在: 用户ID=999");
        }

        @Test
        @DisplayName("AiErrorCode + cause：保留cause")
        void shouldPreserveCause() {
            Throwable cause = new RuntimeException("原始原因");
            AiException ex = new AiException(AiErrorCode.AGENT_RUNTIME_ERROR, cause);

            assertThat(ex.getCode()).isEqualTo(20001);
            assertThat(ex.getMessage()).isEqualTo("Agent运行时异常");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("AiErrorCode + detail + cause：同时保留detail和cause")
        void shouldPreserveBothDetailAndCause() {
            Throwable cause = new IllegalStateException("底层异常");
            AiException ex = new AiException(AiErrorCode.MODEL_CALL_FAILED, "GPT-4调用超时", cause);

            assertThat(ex.getCode()).isEqualTo(70002);
            assertThat(ex.getMessage()).isEqualTo("模型调用失败: GPT-4调用超时");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("AiException继承关系")
    class InheritanceTest {

        @Test
        @DisplayName("AiException应继承RuntimeException")
        void shouldBeRuntimeException() {
            AiException ex = new AiException(AiErrorCode.UNKNOWN);

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("AiErrorCode 枚举值测试")
    class AiErrorCodeTest {

        @Test
        @DisplayName("通用异常编码范围 1xxxx")
        void shouldHaveGeneralErrorCodes() {
            assertThat(AiErrorCode.UNKNOWN.getCode()).isEqualTo(10000);
            assertThat(AiErrorCode.PARAM_ERROR.getCode()).isEqualTo(10001);
            assertThat(AiErrorCode.NOT_FOUND.getCode()).isEqualTo(10002);
            assertThat(AiErrorCode.UNAUTHORIZED.getCode()).isEqualTo(10003);
            assertThat(AiErrorCode.FORBIDDEN.getCode()).isEqualTo(10004);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.UNKNOWN, AiErrorCode.PARAM_ERROR, AiErrorCode.NOT_FOUND,
                    AiErrorCode.UNAUTHORIZED, AiErrorCode.FORBIDDEN
            }) {
                assertThat(code.getCode()).isBetween(10000, 19999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Agent异常编码范围 2xxxx")
        void shouldHaveAgentErrorCodes() {
            assertThat(AiErrorCode.AGENT_RUNTIME_ERROR.getCode()).isEqualTo(20001);
            assertThat(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode()).isEqualTo(20002);
            assertThat(AiErrorCode.AGENT_TASK_FAILED.getCode()).isEqualTo(20003);
            assertThat(AiErrorCode.AGENT_TASK_TIMEOUT.getCode()).isEqualTo(20004);
            assertThat(AiErrorCode.AGENT_TASK_CANCELLED.getCode()).isEqualTo(20005);
            assertThat(AiErrorCode.AGENT_ASSEMBLY_ERROR.getCode()).isEqualTo(20006);
            assertThat(AiErrorCode.AGENT_INTENT_ROUTE_ERROR.getCode()).isEqualTo(20007);
            assertThat(AiErrorCode.AGENT_INPUT_BLOCKED.getCode()).isEqualTo(20008);
            assertThat(AiErrorCode.AGENT_OUTPUT_BLOCKED.getCode()).isEqualTo(20009);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.AGENT_RUNTIME_ERROR, AiErrorCode.AGENT_TASK_NOT_FOUND,
                    AiErrorCode.AGENT_TASK_FAILED, AiErrorCode.AGENT_TASK_TIMEOUT,
                    AiErrorCode.AGENT_TASK_CANCELLED, AiErrorCode.AGENT_ASSEMBLY_ERROR,
                    AiErrorCode.AGENT_INTENT_ROUTE_ERROR, AiErrorCode.AGENT_INPUT_BLOCKED,
                    AiErrorCode.AGENT_OUTPUT_BLOCKED
            }) {
                assertThat(code.getCode()).isBetween(20000, 29999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Skill异常编码范围 3xxxx")
        void shouldHaveSkillErrorCodes() {
            assertThat(AiErrorCode.SKILL_NOT_FOUND.getCode()).isEqualTo(30001);
            assertThat(AiErrorCode.SKILL_LOAD_FAILED.getCode()).isEqualTo(30002);
            assertThat(AiErrorCode.SKILL_PARSE_ERROR.getCode()).isEqualTo(30003);
            assertThat(AiErrorCode.SKILL_GENERATE_FAILED.getCode()).isEqualTo(30004);
            assertThat(AiErrorCode.SKILL_VALIDATION_FAILED.getCode()).isEqualTo(30005);
            assertThat(AiErrorCode.SKILL_RESUME_TOKEN_INVALID.getCode()).isEqualTo(30006);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.SKILL_NOT_FOUND, AiErrorCode.SKILL_LOAD_FAILED,
                    AiErrorCode.SKILL_PARSE_ERROR, AiErrorCode.SKILL_GENERATE_FAILED,
                    AiErrorCode.SKILL_VALIDATION_FAILED, AiErrorCode.SKILL_RESUME_TOKEN_INVALID
            }) {
                assertThat(code.getCode()).isBetween(30000, 39999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("MCP异常编码范围 4xxxx")
        void shouldHaveMcpErrorCodes() {
            assertThat(AiErrorCode.MCP_CLIENT_INIT_FAILED.getCode()).isEqualTo(40001);
            assertThat(AiErrorCode.MCP_CLIENT_CONNECTION_ERROR.getCode()).isEqualTo(40002);
            assertThat(AiErrorCode.MCP_TOOL_CALL_FAILED.getCode()).isEqualTo(40003);
            assertThat(AiErrorCode.MCP_TOOL_NOT_FOUND.getCode()).isEqualTo(40004);
            assertThat(AiErrorCode.MCP_TOOL_FORBIDDEN.getCode()).isEqualTo(40005);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.MCP_CLIENT_INIT_FAILED, AiErrorCode.MCP_CLIENT_CONNECTION_ERROR,
                    AiErrorCode.MCP_TOOL_CALL_FAILED, AiErrorCode.MCP_TOOL_NOT_FOUND,
                    AiErrorCode.MCP_TOOL_FORBIDDEN
            }) {
                assertThat(code.getCode()).isBetween(40000, 49999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("RAG异常编码范围 5xxxx")
        void shouldHaveRagErrorCodes() {
            assertThat(AiErrorCode.RAG_RETRIEVE_FAILED.getCode()).isEqualTo(50001);
            assertThat(AiErrorCode.RAG_EMBEDDING_FAILED.getCode()).isEqualTo(50002);
            assertThat(AiErrorCode.RAG_DOCUMENT_PARSE_ERROR.getCode()).isEqualTo(50003);
            assertThat(AiErrorCode.RAG_KB_NOT_FOUND.getCode()).isEqualTo(50004);
            assertThat(AiErrorCode.RAG_DOCUMENT_NOT_FOUND.getCode()).isEqualTo(50005);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.RAG_RETRIEVE_FAILED, AiErrorCode.RAG_EMBEDDING_FAILED,
                    AiErrorCode.RAG_DOCUMENT_PARSE_ERROR, AiErrorCode.RAG_KB_NOT_FOUND,
                    AiErrorCode.RAG_DOCUMENT_NOT_FOUND
            }) {
                assertThat(code.getCode()).isBetween(50000, 59999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Wiki异常编码范围 6xxxx")
        void shouldHaveWikiErrorCodes() {
            assertThat(AiErrorCode.WIKI_PROJECT_NOT_FOUND.getCode()).isEqualTo(60001);
            assertThat(AiErrorCode.WIKI_INGEST_FAILED.getCode()).isEqualTo(60002);
            assertThat(AiErrorCode.WIKI_SYNC_FAILED.getCode()).isEqualTo(60003);
            assertThat(AiErrorCode.WIKI_GRAPH_BUILD_FAILED.getCode()).isEqualTo(60004);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.WIKI_PROJECT_NOT_FOUND, AiErrorCode.WIKI_INGEST_FAILED,
                    AiErrorCode.WIKI_SYNC_FAILED, AiErrorCode.WIKI_GRAPH_BUILD_FAILED
            }) {
                assertThat(code.getCode()).isBetween(60000, 69999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("模型异常编码范围 7xxxx")
        void shouldHaveModelErrorCodes() {
            assertThat(AiErrorCode.MODEL_NOT_FOUND.getCode()).isEqualTo(70001);
            assertThat(AiErrorCode.MODEL_CALL_FAILED.getCode()).isEqualTo(70002);
            assertThat(AiErrorCode.MODEL_CONFIG_ERROR.getCode()).isEqualTo(70003);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.MODEL_NOT_FOUND, AiErrorCode.MODEL_CALL_FAILED,
                    AiErrorCode.MODEL_CONFIG_ERROR
            }) {
                assertThat(code.getCode()).isBetween(70000, 79999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("存储异常编码范围 8xxxx")
        void shouldHaveStorageErrorCodes() {
            assertThat(AiErrorCode.STORAGE_MINIO_ERROR.getCode()).isEqualTo(80001);
            assertThat(AiErrorCode.STORAGE_QDRANT_ERROR.getCode()).isEqualTo(80002);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.STORAGE_MINIO_ERROR, AiErrorCode.STORAGE_QDRANT_ERROR
            }) {
                assertThat(code.getCode()).isBetween(80000, 89999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("对话异常编码范围 9xxxx")
        void shouldHaveConversationErrorCodes() {
            assertThat(AiErrorCode.CONVERSATION_NOT_FOUND.getCode()).isEqualTo(90001);
            assertThat(AiErrorCode.CONVERSATION_MEMORY_ERROR.getCode()).isEqualTo(90002);

            for (AiErrorCode code : new AiErrorCode[]{
                    AiErrorCode.CONVERSATION_NOT_FOUND, AiErrorCode.CONVERSATION_MEMORY_ERROR
            }) {
                assertThat(code.getCode()).isBetween(90000, 99999);
                assertThat(code.getMessage()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("所有枚举值的code和message不为空")
        void allEnumValuesShouldHaveCodeAndMessage() {
            for (AiErrorCode errorCode : AiErrorCode.values()) {
                assertThat(errorCode.getCode()).isPositive();
                assertThat(errorCode.getMessage()).isNotNull().isNotEmpty();
            }
        }
    }
}
