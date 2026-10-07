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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.engine.EvaluationEngine;
import com.yangqiongai.ai.evaluation.report.CaseResult;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 评测运行编排
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(prefix = "ai.agent.evaluation", name = "enabled", havingValue = "true")
public class EvaluationRunService {

    public static final String STATUS_RUNNING = "RUNNING";

    public static final String STATUS_PASSED = "PASSED";

    public static final String STATUS_FAILED = "FAILED";

    public static final String STATUS_ERROR = "ERROR";

    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 默认判定阈值(与发布门禁默认一致，0-100分)
     */
    public static final double DEFAULT_THRESHOLD = 80.0;

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunService.class);

    @Autowired
    private EvalRunRepository evalRunRepository;

    @Autowired
    private EvalDatasetRepository evalDatasetRepository;

    @Autowired
    private DatasetLoader datasetLoader;

    @Autowired
    private EvaluationEngine evaluationEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 评判模型编码快照来源(与LlmJudgeStrategy同配置键，run触发时固化用于审计)
     */
    @Value("${ai.evaluation.judge.model-code:defaultAgent}")
    private String judgeModelCode;

    private final ExecutorService worker;

    /**
     * 运行中任务的Future映射(runId→future，支持取消与取消检查)
     */
    private final Map<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();

    /**
     * 按配置线程数初始化run编排worker池
     * @param properties 评测配置
     */
    public EvaluationRunService(AgentEvalProperties properties) {
        this.worker = Executors.newFixedThreadPool(Math.max(1, properties.getRunWorkers()), r -> {
            Thread thread = new Thread(r, "agent-eval-run-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 触发评测运行(异步执行，返回runId供前端轮询终态)
     * @param datasetId 数据集ID
     * @param agentCode 被测Agent编码(可空=用例body内指定)
     * @param threshold 判定阈值(空=80.0)
     * @return
     */
    public EvalRunEntity triggerRun(Long datasetId, String agentCode, Double threshold) {
        EvalDatasetEntity dataset = evalDatasetRepository.findDatasetById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        if (!"ENABLED".equals(dataset.getStatus())) {
            throw new IllegalArgumentException("数据集未启用: " + dataset.getDatasetCode());
        }
        if (evalDatasetRepository.findCases(datasetId).isEmpty()) {
            throw new IllegalArgumentException("数据集无用例，请先导入用例: " + dataset.getDatasetCode());
        }

        EvalRunEntity run = new EvalRunEntity();
        run.setDatasetId(datasetId);
        run.setDatasetCode(dataset.getDatasetCode());
        run.setAgentCode(agentCode);
        run.setJudgeModelCode(judgeModelCode);
        run.setStatus(STATUS_RUNNING);
        run.setThreshold(threshold != null
                ? BigDecimal.valueOf(threshold).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(DEFAULT_THRESHOLD));
        run.setStartedAt(LocalDateTime.now());
        evalRunRepository.insertRun(run);

        Future<?> future = worker.submit(() -> executeRun(run));
        runningFutures.put(run.getId(), future);
        return run;
    }

    /**
     * 取消运行(RUNNING状态置CANCELLED并中断worker，结果丢弃)
     * @param runId
     */
    public void cancelRun(Long runId) {
        EvalRunEntity run = evalRunRepository.findRunById(runId);
        if (run == null) {
            throw new IllegalArgumentException("评测运行不存在: " + runId);
        }
        if (!STATUS_RUNNING.equals(run.getStatus())) {
            throw new IllegalArgumentException("仅RUNNING状态可取消，当前状态: " + run.getStatus());
        }
        run.setStatus(STATUS_CANCELLED);
        run.setFinishedAt(LocalDateTime.now());
        evalRunRepository.updateRun(run);
        Future<?> future = runningFutures.remove(runId);
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * worker执行体(恢复scope上下文→整集评测→落明细→写终态)
     * <p>
     * 取消语义：cancelRun先置CANCELLED终态并cancel(true)中断worker；
     * worker在评测返回后检查取消标记，已取消则丢弃结果保持CANCELLED。
     * </p>
     * @param run
     */
    private void executeRun(EvalRunEntity run) {
        ScopeContext.setScopeId(run.getScopeId());
        try {
            if (isCancelled(run.getId())) {
                return;
            }
            GoldenDataset dataset = datasetLoader.load("db:" + run.getDatasetCode());
            EvaluationReport report = evaluationEngine.evaluate(dataset);

            // cancel(true)中断后引擎对中断快速返回，此处丢弃结果保持CANCELLED终态
            if (isCancelled(run.getId()) || Thread.currentThread().isInterrupted()) {
                log.info("评测运行已取消，丢弃结果: runId={}", run.getId());
                return;
            }
            saveRunResult(run, report);
        } catch (Exception e) {
            if (isCancelled(run.getId())) {
                log.info("评测运行已取消: runId={}", run.getId());
                return;
            }
            log.error("评测运行执行异常: runId={}", run.getId(), e);
            markError(run, e.getMessage());
        } finally {
            runningFutures.remove(run.getId());
            ScopeContext.clear();
        }
    }

    /**
     * 检查运行是否已被取消
     * @param runId
     * @return
     */
    private boolean isCancelled(Long runId) {
        Future<?> future = runningFutures.get(runId);
        return future != null && future.isCancelled();
    }

    /**
     * 落运行结果(用例明细+整体指标+报告快照+终态)
     * @param run
     * @param report
     */
    private void saveRunResult(EvalRunEntity run, EvaluationReport report) {
        List<CaseResult> results = report.getResults() != null ? report.getResults() : List.of();
        evalRunRepository.insertRunCases(toRunCases(run, results));

        run.setTotalCases(report.getTotalCases());
        run.setPassedCases(report.getPassedCases());
        run.setFailedCases(report.getFailedCases());
        // 引擎分值为0-1域，换算为0-100落库
        run.setAvgScore(BigDecimal.valueOf(report.getAverageScore() * 100).setScale(4, RoundingMode.HALF_UP));
        run.setStatus(decideStatus(run, report));
        run.setFinishedAt(LocalDateTime.now());
        run.setReportJson(serializeReport(run.getId(), report));
        evalRunRepository.updateRun(run);
        log.info("评测运行完成: runId={}, status={}, avgScore={}, passed={}/{}",
                run.getId(), run.getStatus(), run.getAvgScore(), report.getPassedCases(), report.getTotalCases());
        publishFinished(run);
    }

    /**
     * 判定终态(整体通过且均分达阈值=通过，均分经0-1→0-100换算后与阈值比较)
     * @param run
     * @param report
     * @return
     */
    private String decideStatus(EvalRunEntity run, EvaluationReport report) {
        double threshold = run.getThreshold() != null ? run.getThreshold().doubleValue() : DEFAULT_THRESHOLD;
        double avgScore = report.getAverageScore() * 100;
        return report.isOverallPassed() && avgScore >= threshold
                ? STATUS_PASSED : STATUS_FAILED;
    }

    /**
     * 报告快照序列化(失败不影响终态写入)
     * @param runId
     * @param report
     * @return
     */
    private String serializeReport(Long runId, EvaluationReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("评测报告序列化失败: runId={}", runId, e);
            return null;
        }
    }

    /**
     * 用例结果转明细实体(case_no回溯case_id)
     * @param run
     * @param results
     * @return
     */
    private List<EvalRunCaseEntity> toRunCases(EvalRunEntity run, List<CaseResult> results) {
        Map<String, Long> caseIdByNo = new HashMap<>();
        for (EvalDatasetCaseEntity caseEntity : evalDatasetRepository.findCases(run.getDatasetId())) {
            caseIdByNo.put(caseEntity.getCaseNo(), caseEntity.getId());
        }
        List<EvalRunCaseEntity> runCases = new ArrayList<>(results.size());
        for (CaseResult result : results) {
            EvalRunCaseEntity runCase = new EvalRunCaseEntity();
            runCase.setRunId(run.getId());
            runCase.setCaseId(caseIdByNo.get(result.getCaseId()));
            runCase.setCaseNo(result.getCaseId());
            runCase.setActualOutput(result.getActualOutput());
            runCase.setExpectedOutput(result.getExpectedOutput());
            // 引擎分值为0-1域，换算为0-100落库
            runCase.setScore(BigDecimal.valueOf(result.getScore() * 100).setScale(4, RoundingMode.HALF_UP));
            runCase.setPassed(result.isPassed() ? 1 : 0);
            runCase.setStrategyName(result.getStrategyName());
            runCase.setErrorMessage(result.getErrorMessage());
            runCase.setDurationMs(result.getDurationMs());
            runCases.add(runCase);
        }
        return runCases;
    }

    /**
     * 运行异常写ERROR终态
     * @param run
     * @param errorMessage
     */
    private void markError(EvalRunEntity run, String errorMessage) {
        try {
            run.setStatus(STATUS_ERROR);
            run.setErrorMessage(errorMessage != null
                    ? errorMessage.substring(0, Math.min(errorMessage.length(), 1024)) : null);
            run.setFinishedAt(LocalDateTime.now());
            evalRunRepository.updateRun(run);
            publishFinished(run);
        } catch (Exception e) {
            log.error("评测运行异常终态写入失败: runId={}", run.getId(), e);
        }
    }

    /**
     * 发布评测运行完成事件(失败不影响主流程)
     * @param run
     */
    private void publishFinished(EvalRunEntity run) {
        try {
            eventPublisher.publishEvent(new EvalRunFinishedEvent(
                    run.getId(), run.getAgentCode(), run.getDatasetId(), run.getStatus()));
        } catch (Exception e) {
            log.warn("评测运行完成事件发布失败: runId={}", run.getId(), e);
        }
    }

    /**
     * 停止时终止worker池
     */
    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }
}
