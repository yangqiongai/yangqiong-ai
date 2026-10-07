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
package com.yangqiongai.ai.platform.api.evaluation;

import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.engine.EvaluationEngine;
import com.yangqiongai.ai.evaluation.report.CaseResult;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测运行编排测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationRunServiceTest {

    @Mock
    private EvalRunRepository evalRunRepository;

    @Mock
    private EvalDatasetRepository evalDatasetRepository;

    @Mock
    private DatasetLoader datasetLoader;

    @Mock
    private EvaluationEngine evaluationEngine;

    private EvaluationRunService service;

    @BeforeEach
    void setUp() {
        AgentEvalProperties properties = new AgentEvalProperties();
        properties.setEnabled(true);
        service = new EvaluationRunService(properties);
        inject("evalRunRepository", evalRunRepository);
        inject("evalDatasetRepository", evalDatasetRepository);
        inject("datasetLoader", datasetLoader);
        inject("evaluationEngine", evaluationEngine);
    }

    /**
     * 反射注入mock依赖
     * @param fieldName
     * @param value
     */
    private void inject(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = EvaluationRunService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private EvalDatasetEntity dataset(Long id, String code, String status) {
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setId(id);
        entity.setDatasetCode(code);
        entity.setStatus(status);
        return entity;
    }

    private EvalRunEntity run(Long id, String datasetCode, String status) {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(id);
        entity.setDatasetId(1L);
        entity.setDatasetCode(datasetCode);
        entity.setStatus(status);
        entity.setScopeId("default");
        entity.setThreshold(BigDecimal.valueOf(80.0));
        entity.setStartedAt(LocalDateTime.now());
        return entity;
    }

    private EvaluationReport report(double avgScore, boolean overallPassed) {
        CaseResult result = new CaseResult("c1", "q", "actual", "expected",
                avgScore, overallPassed, "contains_all", null, 10);
        return new EvaluationReport("recruit", "面试数据集", 1,
                overallPassed ? 1 : 0, overallPassed ? 0 : 1, avgScore, overallPassed,
                List.of(result), LocalDateTime.now(), 100);
    }

    private void stubTriggerHappyPath() {
        when(evalDatasetRepository.findDatasetById(1L)).thenReturn(dataset(1L, "recruit", "ENABLED"));
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(new EvalDatasetCaseEntity()));
        doAnswer(invocation -> {
            invocation.getArgument(0, EvalRunEntity.class).setId(100L);
            return null;
        }).when(evalRunRepository).insertRun(any(EvalRunEntity.class));
    }

    @Test
    void 数据集不存在时触发拒绝() {
        when(evalDatasetRepository.findDatasetById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.triggerRun(1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据集不存在");
    }

    @Test
    void 数据集未启用时触发拒绝() {
        when(evalDatasetRepository.findDatasetById(1L)).thenReturn(dataset(1L, "recruit", "DISABLED"));

        assertThatThrownBy(() -> service.triggerRun(1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据集未启用");
    }

    @Test
    void 数据集无用例时触发拒绝() {
        when(evalDatasetRepository.findDatasetById(1L)).thenReturn(dataset(1L, "recruit", "ENABLED"));
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.triggerRun(1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无用例");
    }

    @Test
    void 触发成功创建RUNNING并异步写终态() {
        stubTriggerHappyPath();
        when(datasetLoader.load("db:recruit")).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        // 引擎分值为0-1域
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenReturn(report(0.9, true));

        EvalRunEntity run = service.triggerRun(1L, "recruiter", 85.0);

        assertThat(run.getId()).isEqualTo(100L);
        assertThat(run.getStatus()).isEqualTo(EvaluationRunService.STATUS_RUNNING);
        assertThat(run.getAgentCode()).isEqualTo("recruiter");
        assertThat(run.getThreshold()).isEqualByComparingTo(BigDecimal.valueOf(85.0));
        ArgumentCaptor<EvalRunEntity> captor = ArgumentCaptor.forClass(EvalRunEntity.class);
        verify(evalRunRepository, timeout(5000)).updateRun(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EvaluationRunService.STATUS_PASSED);
        // 落库换算为0-100域
        assertThat(captor.getValue().getAvgScore()).isEqualByComparingTo(BigDecimal.valueOf(90.0));
        assertThat(captor.getValue().getFinishedAt()).isNotNull();
    }

    @Test
    void 均分低于阈值时判定FAILED() {
        stubTriggerHappyPath();
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenReturn(report(0.9, true));

        service.triggerRun(1L, null, 95.0);

        verify(evalRunRepository, timeout(5000)).updateRun(
                org.mockito.ArgumentMatchers.argThat(r -> EvaluationRunService.STATUS_FAILED.equals(r.getStatus())));
    }

    @Test
    void 整体未通过时判定FAILED() {
        stubTriggerHappyPath();
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenReturn(report(0.95, false));

        service.triggerRun(1L, null, null);

        verify(evalRunRepository, timeout(5000)).updateRun(
                org.mockito.ArgumentMatchers.argThat(r -> EvaluationRunService.STATUS_FAILED.equals(r.getStatus())));
    }

    @Test
    void 用例明细随结果落库() {
        stubTriggerHappyPath();
        when(evalDatasetRepository.findCases(1L)).thenReturn(List.of(caseEntity(11L, "c1")));
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenReturn(report(0.9, true));

        service.triggerRun(1L, null, null);

        ArgumentCaptor<List<EvalRunCaseEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(evalRunRepository, timeout(5000)).insertRunCases(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getCaseId()).isEqualTo(11L);
        assertThat(captor.getValue().get(0).getCaseNo()).isEqualTo("c1");
        assertThat(captor.getValue().get(0).getPassed()).isEqualTo(1);
        // 用例分值落库换算为0-100域
        assertThat(captor.getValue().get(0).getScore()).isEqualByComparingTo(BigDecimal.valueOf(90.0));
    }

    @Test
    void 引擎异常时写ERROR终态() {
        stubTriggerHappyPath();
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenThrow(new RuntimeException("模型连接失败"));

        service.triggerRun(1L, null, null);

        ArgumentCaptor<EvalRunEntity> captor = ArgumentCaptor.forClass(EvalRunEntity.class);
        verify(evalRunRepository, timeout(5000)).updateRun(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EvaluationRunService.STATUS_ERROR);
        assertThat(captor.getValue().getErrorMessage()).contains("模型连接失败");
    }

    @Test
    void 取消不存在的运行拒绝() {
        when(evalRunRepository.findRunById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.cancelRun(9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void 取消非RUNNING状态拒绝() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, "recruit", EvaluationRunService.STATUS_PASSED));

        assertThatThrownBy(() -> service.cancelRun(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅RUNNING状态可取消");
    }

    @Test
    void 取消RUNNING运行置CANCELLED并丢弃结果() {
        stubTriggerHappyPath();
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenAnswer(invocation -> {
            Thread.sleep(600);
            return report(0.9, true);
        });

        EvalRunEntity run = service.triggerRun(1L, null, null);
        when(evalRunRepository.findRunById(run.getId())).thenReturn(run);
        service.cancelRun(run.getId());

        ArgumentCaptor<EvalRunEntity> captor = ArgumentCaptor.forClass(EvalRunEntity.class);
        verify(evalRunRepository, timeout(2000)).updateRun(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EvaluationRunService.STATUS_CANCELLED);
        org.mockito.Mockito.verify(evalRunRepository,
                org.mockito.Mockito.after(1500).never()).updateRun(
                org.mockito.ArgumentMatchers.argThat(r ->
                        EvaluationRunService.STATUS_PASSED.equals(r.getStatus())
                                || EvaluationRunService.STATUS_FAILED.equals(r.getStatus())));
    }

    @Test
    void 取消后RUNNING记录不写入用例明细() {
        stubTriggerHappyPath();
        when(datasetLoader.load(anyString())).thenReturn(new GoldenDataset("recruit", "n", "d", List.of()));
        when(evaluationEngine.evaluate(any(GoldenDataset.class))).thenAnswer(invocation -> {
            Thread.sleep(600);
            return report(0.9, true);
        });

        EvalRunEntity run = service.triggerRun(1L, null, null);
        when(evalRunRepository.findRunById(run.getId())).thenReturn(run);
        service.cancelRun(run.getId());

        org.mockito.Mockito.verify(evalRunRepository,
                org.mockito.Mockito.after(1500).never()).insertRunCases(any());
    }

    private EvalDatasetCaseEntity caseEntity(Long id, String caseNo) {
        EvalDatasetCaseEntity entity = new EvalDatasetCaseEntity();
        entity.setId(id);
        entity.setCaseNo(caseNo);
        return entity;
    }
}
