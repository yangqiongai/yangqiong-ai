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
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM评判策略
 * @author yangqiong
 */
@Component
public class LlmJudgeStrategy implements ScoringStrategy {

    /**
     * 默认评判提示词(占位符{expectedOutput}/{actualOutput}运行时替换)
     */
    private static final String DEFAULT_JUDGE_PROMPT = "你是评估裁判。请对比实际输出与期望输出，给出0到100的评分。\n"
            + "期望输出：{expectedOutput}\n"
            + "实际输出：{actualOutput}\n"
            + "仅输出一个0到100之间的数字，不要输出任何其他内容。";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.evaluation.judge.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 评判提示词模板(可配，占位符{expectedOutput}/{actualOutput}，空=默认模板)
     */
    @Value("${ai.evaluation.judge.prompt:}")
    private String judgePrompt;

    private final ContainsAllStrategy fallbackStrategy = new ContainsAllStrategy();

    /**
     * 获取策略名称
     * @return
     */
    @Override
    public String getStrategyName() {
        return "llm_judge";
    }

    /**
     * 评分
     * @param actualOutput
     * @param expectedOutput
     * @return
     */
    @Override
    public double score(String actualOutput, String expectedOutput) {
        if (languageModelFactory == null) {
            return fallbackStrategy.score(actualOutput, expectedOutput);
        }
        String prompt = buildJudgePrompt(actualOutput, expectedOutput);
        try {
            AgentMessage userMsg = AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(List.of(AgentTextBlock.builder().text(prompt).build()))
                    .build();
            String response = languageModelFactory.generateText(modelCode, List.of(userMsg));
            return parseScore(response);
        } catch (Exception e) {
            return fallbackStrategy.score(actualOutput, expectedOutput);
        }
    }

    /**
     * 构建评判提示词(模板可配，占位符替换输出与期望文本)
     * @param actualOutput
     * @param expectedOutput
     * @return
     */
    private String buildJudgePrompt(String actualOutput, String expectedOutput) {
        String template = judgePrompt != null && !judgePrompt.isBlank() ? judgePrompt : DEFAULT_JUDGE_PROMPT;
        return template.replace("{expectedOutput}", expectedOutput == null ? "" : expectedOutput)
                .replace("{actualOutput}", actualOutput == null ? "" : actualOutput);
    }

    private double parseScore(String response) {
        try {
            String trimmed = response.trim();
            StringBuilder numberBuilder = new StringBuilder();
            boolean hasDecimalPoint = false;
            for (char c : trimmed.toCharArray()) {
                if (Character.isDigit(c)) {
                    numberBuilder.append(c);
                } else if (c == '.' && !hasDecimalPoint && !numberBuilder.isEmpty()) {
                    // 允许一个小数点，但必须出现在数字之后
                    numberBuilder.append(c);
                    hasDecimalPoint = true;
                } else if (!numberBuilder.isEmpty()) {
                    break;
                }
            }
            if (numberBuilder.isEmpty()) {
                return 0.0;
            }
            double score = Double.parseDouble(numberBuilder.toString());
            score = Math.max(0.0, Math.min(100.0, score));
            return score / 100.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
