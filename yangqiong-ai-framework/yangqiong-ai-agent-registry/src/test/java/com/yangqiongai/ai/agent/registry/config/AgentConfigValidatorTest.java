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
package com.yangqiongai.ai.agent.registry.config;

import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent配置校验器单元测试
 * @author yangqiong
 */
@DisplayName("AgentConfigValidator 单元测试")
class AgentConfigValidatorTest {

    private static final String VALID_CONFIG = """
            {
              "model": "deepseek-v3",
              "systemPrompt": "你是选矿药剂技术顾问",
              "maxIterations": 15,
              "temperature": 0.3,
              "tools": ["reagent_query", "calc_cost"],
              "skills": ["sales_playbook"],
              "knowledgeBase": {"kbCodes": ["mineral_kb", "product_kb"], "topK": 5},
              "bindingMode": "append"
            }
            """;

    @Test
    @DisplayName("合法配置通过校验")
    void validConfigPasses() {
        assertThatCode(() -> AgentConfigValidator.validate(VALID_CONFIG)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非法JSON被拒绝")
    void invalidJsonRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{not-json"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("不是合法JSON");
    }

    @Test
    @DisplayName("configJson为空被拒绝")
    void blankConfigRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(null)).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> AgentConfigValidator.validate("  ")).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("configJson非JSON对象被拒绝")
    void nonObjectConfigRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("[1,2,3]"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("JSON对象");
    }

    @Test
    @DisplayName("model缺失被拒绝")
    void missingModelRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"systemPrompt\":\"x\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("model必填");
    }

    @Test
    @DisplayName("model为空字符串被拒绝")
    void blankModelRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"  \"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("model必填");
    }

    @Test
    @DisplayName("maxIterations越界被拒绝")
    void maxIterationsOutOfRangeRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"maxIterations\":0}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("maxIterations");
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"maxIterations\":51}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    @DisplayName("maxIterations边界值1和50通过")
    void maxIterationsBoundaryPass() {
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"maxIterations\":1}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"maxIterations\":50}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maxIterations非整数被拒绝")
    void maxIterationsNonIntRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"maxIterations\":\"abc\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    @DisplayName("temperature越界被拒绝")
    void temperatureOutOfRangeRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"temperature\":-0.1}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"temperature\":2.1}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    @DisplayName("temperature边界值0和2通过")
    void temperatureBoundaryPass() {
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"temperature\":0}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"temperature\":2}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tools非数组被拒绝")
    void toolsNonArrayRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"tools\":\"t1\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("tools");
    }

    @Test
    @DisplayName("tools含非字符串元素被拒绝")
    void toolsNonStringItemRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"tools\":[\"t1\",123]}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("tools");
    }

    @Test
    @DisplayName("skills非字符串数组被拒绝")
    void skillsNonArrayRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"skills\":[true]}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("skills");
    }

    @Test
    @DisplayName("knowledgeBase无kbCode和kbCodes被拒绝")
    void knowledgeBaseWithoutKbRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":{\"topK\":5}}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase");
    }

    @Test
    @DisplayName("knowledgeBase空kbCodes数组被拒绝")
    void knowledgeBaseEmptyKbCodesRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":{\"kbCodes\":[]}}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase");
    }

    @Test
    @DisplayName("knowledgeBase单kbCode通过")
    void knowledgeBaseWithKbCodePasses() {
        assertThatCode(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":{\"kbCode\":\"kb1\"}}")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("knowledgeBase数组对象格式通过")
    void knowledgeBaseArrayWithKbNamePasses() {
        assertThatCode(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":[{\"kbCode\":\"kb1\",\"kbName\":\"预算知识库\"},{\"kbCode\":\"kb2\",\"kbName\":\"产品知识库\"}]}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("knowledgeBase数组项缺kbCode被拒绝")
    void knowledgeBaseArrayItemWithoutKbCodeRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":[{\"kbCode\":\"kb1\"},{\"kbName\":\"无名\"}]}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase数组项必须包含kbCode");
    }

    @Test
    @DisplayName("knowledgeBase数组项非对象被拒绝")
    void knowledgeBaseArrayNonObjectItemRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":[\"kb1\"]}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase数组项必须是JSON对象");
    }

    @Test
    @DisplayName("knowledgeBase空数组被拒绝")
    void knowledgeBaseEmptyArrayRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":[]}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase数组不能为空");
    }

    @Test
    @DisplayName("knowledgeBase字符串被拒绝")
    void knowledgeBaseStringRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"knowledgeBase\":\"kb1\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("knowledgeBase必须是JSON对象或对象数组");
    }

    @Test
    @DisplayName("bindingMode非法值被拒绝")
    void invalidBindingModeRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"bindingMode\":\"overlay\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("bindingMode");
    }

    @Test
    @DisplayName("bindingMode大小写不敏感通过")
    void bindingModeCaseInsensitivePasses() {
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"bindingMode\":\"REPLACE\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("hash计算稳定且不同内容哈希不同")
    void hashStableAndDistinct() {
        String hash1 = AgentConfigValidator.hash(VALID_CONFIG);
        String hash2 = AgentConfigValidator.hash(VALID_CONFIG);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(AgentConfigValidator.hash("{\"model\":\"other\"}")).isNotEqualTo(hash1);
    }

    @Test
    @DisplayName("未配置outputSchema通过")
    void noOutputSchemaPasses() {
        assertThatCode(() -> AgentConfigValidator.validate(VALID_CONFIG)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("outputSchema为null时视为未配置通过")
    void nullOutputSchemaPasses() {
        assertThatCode(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"outputSchema\":null}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("合法outputSchema通过")
    void validOutputSchemaPasses() {
        String config = """
                {"model":"m","outputSchema":{"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}}
                """;
        assertThatCode(() -> AgentConfigValidator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("outputSchema非对象被拒绝")
    void nonObjectOutputSchemaRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate("{\"model\":\"m\",\"outputSchema\":\"string\"}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("outputSchema必须是JSON对象");
    }

    @Test
    @DisplayName("outputSchema非法draft-07结构被拒绝")
    void invalidDraft07OutputSchemaRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"outputSchema\":{\"type\":\"str\"}}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("outputSchema不合法");
    }

    @Test
    @DisplayName("outputSchema必填字段错误被拒绝")
    void invalidRequiredOutputSchemaRejected() {
        assertThatThrownBy(() -> AgentConfigValidator.validate(
                "{\"model\":\"m\",\"outputSchema\":{\"type\":\"object\",\"properties\":\"bad\"}}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("outputSchema不合法");
    }
}
