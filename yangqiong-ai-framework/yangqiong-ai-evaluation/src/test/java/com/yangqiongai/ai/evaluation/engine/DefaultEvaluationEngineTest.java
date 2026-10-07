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
package com.yangqiongai.ai.evaluation.engine;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.evaluation.dataset.GoldenCase;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.report.CaseResult;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import com.yangqiongai.ai.evaluation.report.EvaluationReportGenerator;
import com.yangqiongai.ai.evaluation.scoring.ExactMatchStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEvaluationEngine 单元测试")
class DefaultEvaluationEngineTest {

    @Mock
    private AgentEngine agentEngine;

    @Mock
    private EvaluationReportGenerator reportGenerator;

    private DefaultEvaluationEngine engine;

    private final ExactMatchStrategy exactMatchStrategy = new ExactMatchStrategy();

    @BeforeEach
    void setUp() {
        engine = new DefaultEvaluationEngine(List.of(exactMatchStrategy));
        ReflectionTestUtils.setField(engine, "agentEngine", agentEngine);
        ReflectionTestUtils.setField(engine, "reportGenerator", reportGenerator);
    }

    @Test
    @DisplayName("evaluate: 正常流程 → 返回报告")
    void evaluate_normalFlow_returnsReport() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "exact_match", null);
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("hello"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        EvaluationReport report = engine.evaluate(dataset);

        assertThat(report).isNotNull();
        verify(agentEngine).run(any(AgentRequest.class));
        verify(reportGenerator).generate(eq("ds-1"), eq("test"), any(List.class), anyLong());
    }

    @Test
    @DisplayName("evaluate: Agent异常 → 评分0.0")
    void evaluate_agentException_scoreZero() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "exact_match", null);
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenThrow(new RuntimeException("agent error"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        EvaluationReport report = engine.evaluate(dataset);

        assertThat(report).isNotNull();
        verify(reportGenerator).generate(any(), any(), argThat(results -> {
            CaseResult cr = ((List<CaseResult>) results).get(0);
            return cr.getScore() == 0.0 && cr.getErrorMessage() != null;
        }), anyLong());
    }

    @Test
    @DisplayName("evaluate: 未知策略 → 错误信息")
    void evaluate_unknownStrategy_errorMessage() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "unknown_strategy", null);
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("hello"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        EvaluationReport report = engine.evaluate(dataset);

        assertThat(report).isNotNull();
        verify(reportGenerator).generate(any(), any(), argThat(results -> {
            CaseResult cr = ((List<CaseResult>) results).get(0);
            return cr.getErrorMessage() != null && cr.getErrorMessage().contains("Unknown scoring strategy");
        }), anyLong());
    }

    @Test
    @DisplayName("evaluate: Agent执行失败 → 评分0.0")
    void evaluate_agentFailure_scoreZero() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "exact_match", null);
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.failure("something went wrong"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        EvaluationReport report = engine.evaluate(dataset);

        assertThat(report).isNotNull();
        verify(reportGenerator).generate(any(), any(), argThat(results -> {
            CaseResult cr = ((List<CaseResult>) results).get(0);
            return cr.getScore() == 0.0 && cr.getActualOutput() == null;
        }), anyLong());
    }

    @Test
    @DisplayName("evaluate: body中包含agentCode → 传递给AgentRequest")
    void evaluate_bodyWithAgentCode_passesToRequest() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "exact_match",
                Map.of("agentCode", "chat"));
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("hello"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        engine.evaluate(dataset);

        verify(agentEngine).run(argThat(req -> "chat".equals(req.getAgentCode())));
    }

    @Test
    @DisplayName("evaluate: 策略名大写写法 → 正常匹配并评分")
    void evaluate_upperCaseStrategy_stillMatches() {
        GoldenCase goldenCase = new GoldenCase("case-1", "hello", "hello", "EXACT_MATCH", null);
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of(goldenCase));

        when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("hello"));
        when(reportGenerator.generate(any(), any(), any(), anyLong()))
                .thenReturn(new EvaluationReport());

        engine.evaluate(dataset);

        verify(reportGenerator).generate(any(), any(), argThat(results -> {
            CaseResult cr = ((List<CaseResult>) results).get(0);
            return cr.getScore() == 1.0 && cr.getErrorMessage() == null;
        }), anyLong());
    }
}
