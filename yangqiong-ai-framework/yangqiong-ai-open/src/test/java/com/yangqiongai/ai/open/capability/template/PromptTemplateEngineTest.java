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
package com.yangqiongai.ai.open.capability.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt模板引擎
 * @author yangqiong
 */
class PromptTemplateEngineTest {

    private PromptTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PromptTemplateEngine();
    }

    @Test
    void testRenderWithVariables() {
        String template = "项目名称：${projectName}，预算：${budget}万元";
        Map<String, Object> variables = Map.of(
                "projectName", "智慧政务平台",
                "budget", "500"
        );

        String result = engine.render(template, variables);

        assertThat(result).isEqualTo("项目名称：智慧政务平台，预算：500万元");
    }

    @Test
    void testRenderWithNullContent() {
        String result = engine.render(null, Map.of("key", "value"));
        assertThat(result).isEmpty();
    }

    @Test
    void testRenderWithNullVariables() {
        String template = "原始模板内容：${variable}";
        String result = engine.render(template, null);
        assertThat(result).isEqualTo(template);
    }

    @Test
    void testRenderWithEmptyVariables() {
        String template = "原始模板内容：${variable}";
        String result = engine.render(template, Collections.emptyMap());
        assertThat(result).isEqualTo(template);
    }

    @Test
    void testRenderWithMultipleVariables() {
        String template = "您好，${name}！您的订单${orderId}状态为${status}。";
        Map<String, Object> variables = Map.of(
                "name", "张三",
                "orderId", "ORD-2024-001",
                "status", "已发货"
        );

        String result = engine.render(template, variables);

        assertThat(result).isEqualTo("您好，张三！您的订单ORD-2024-001状态为已发货。");
    }

    @Test
    void testRenderWithNonExistentVariable() {
        String template = "值：${nonExistent}";
        Map<String, Object> variables = Map.of("existing", "value");

        String result = engine.render(template, variables);

        // SpEL 不会替换未定义的变量，会保留原样
        assertThat(result).isEqualTo("值：${nonExistent}");
    }

    @Test
    void testRenderWithEmptyTemplate() {
        String result = engine.render("", Map.of("key", "value"));
        assertThat(result).isEmpty();
    }
}