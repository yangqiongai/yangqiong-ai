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
package com.yangqiongai.ai.evaluation.scoring;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM评判策略测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmJudgeStrategy 单元测试")
class LlmJudgeStrategyTest {

    @Mock
    private LanguageModelFactory languageModelFactory;

    private LlmJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LlmJudgeStrategy();
        ReflectionTestUtils.setField(strategy, "languageModelFactory", languageModelFactory);
        ReflectionTestUtils.setField(strategy, "modelCode", "defaultAgent");
        ReflectionTestUtils.setField(strategy, "judgePrompt", "");
    }

    @Test
    @DisplayName("策略名为llm_judge")
    void strategyName() {
        assertThat(strategy.getStrategyName()).isEqualTo("llm_judge");
    }

    @Test
    @DisplayName("工厂未注入时回退ContainsAll")
    void fallbackWhenFactoryMissing() {
        ReflectionTestUtils.setField(strategy, "languageModelFactory", null);
        double score = strategy.score("包含关键词A和关键词B", "关键词A,关键词B");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("LLM返回0-100分归一化为0-1")
    void normalizeScore() {
        when(languageModelFactory.generateText(anyString(), anyList())).thenReturn("85");
        assertThat(strategy.score("实际", "期望")).isEqualTo(0.85);
    }

    @Test
    @DisplayName("LLM响应含噪声文本时提取首个数字")
    void extractNumberFromNoisyResponse() {
        when(languageModelFactory.generateText(anyString(), anyList())).thenReturn("评分：92分");
        assertThat(strategy.score("实际", "期望")).isEqualTo(0.92);
    }

    @Test
    @DisplayName("LLM响应无数字时按0分处理")
    void zeroWhenNoNumber() {
        when(languageModelFactory.generateText(anyString(), anyList())).thenReturn("无法评分");
        assertThat(strategy.score("实际", "期望")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("LLM调用异常时回退ContainsAll")
    void fallbackWhenLlmError() {
        when(languageModelFactory.generateText(anyString(), anyList())).thenThrow(new RuntimeException("超时"));
        assertThat(strategy.score("包含关键词A", "关键词A,关键词B")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("默认提示词包含期望与实际输出")
    void defaultPromptContainsOutputs() {
        when(languageModelFactory.generateText(anyString(), anyList())).thenReturn("100");
        strategy.score("实际输出内容", "期望输出内容");

        ArgumentCaptor<List<AgentMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(languageModelFactory).generateText(eq("defaultAgent"), captor.capture());
        assertThat(captor.getValue().get(0).getTextContent())
                .contains("评估裁判")
                .contains("期望输出：期望输出内容")
                .contains("实际输出：实际输出内容");
    }

    @Test
    @DisplayName("自定义提示词模板占位符替换生效")
    void customPromptTemplate() {
        ReflectionTestUtils.setField(strategy, "judgePrompt", "给【{expectedOutput}】和【{actualOutput}】打分");
        when(languageModelFactory.generateText(anyString(), anyList())).thenReturn("100");
        strategy.score("实际A", "期望B");

        ArgumentCaptor<List<AgentMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(languageModelFactory).generateText(eq("defaultAgent"), captor.capture());
        assertThat(captor.getValue().get(0).getTextContent()).isEqualTo("给【期望B】和【实际A】打分");
    }
}
