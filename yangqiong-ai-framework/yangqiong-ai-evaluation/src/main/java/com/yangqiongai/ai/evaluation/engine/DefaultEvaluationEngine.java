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
import com.yangqiongai.ai.evaluation.scoring.ScoringStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认评测引擎
 * @author yangqiong
 */
@Service
public class DefaultEvaluationEngine implements EvaluationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultEvaluationEngine.class);

    /**
     * 单个评测用例超时时间（分钟）
     */
    private static final long CASE_TIMEOUT_MINUTES = 2;

    /**
     * 评测用例执行线程池
     */
    private static final ExecutorService EVALUATION_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "eval-case-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Autowired
    private AgentEngine agentEngine;

    @Autowired
    private EvaluationReportGenerator reportGenerator;

    private final Map<String, ScoringStrategy> strategyMap;

    @Autowired
    public DefaultEvaluationEngine(List<ScoringStrategy> strategies) {
        // 注册表key统一小写，查询时同样转小写，兼容用例填大写策略名（如LLM_JUDGE）
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(s -> s.getStrategyName().toLowerCase(), Function.identity()));
    }

    /**
     * 执行评测
     * @param dataset
     * @return
     */
    @Override
    public EvaluationReport evaluate(GoldenDataset dataset) {
        List<CaseResult> results = new ArrayList<>();
        long totalStart = System.currentTimeMillis();

        for (GoldenCase goldenCase : dataset.getCases()) {
            long caseStart = System.currentTimeMillis();
            CaseResult caseResult;
            try {
                caseResult = CompletableFuture.supplyAsync(() -> evaluateCase(goldenCase), EVALUATION_EXECUTOR)
                        .get(CASE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.warn("评测用例执行超时, caseId={}, timeout={}min", goldenCase.getCaseId(), CASE_TIMEOUT_MINUTES);
                caseResult = new CaseResult(
                        goldenCase.getCaseId(),
                        goldenCase.getQuery(),
                        null,
                        goldenCase.getExpectedOutput(),
                        0.0,
                        false,
                        goldenCase.getScoringCriteria(),
                        "评测用例执行超时（" + CASE_TIMEOUT_MINUTES + "分钟）",
                        0
                );
            } catch (ExecutionException e) {
                log.error("评测用例执行异常, caseId={}", goldenCase.getCaseId(), e.getCause());
                caseResult = new CaseResult(
                        goldenCase.getCaseId(),
                        goldenCase.getQuery(),
                        null,
                        goldenCase.getExpectedOutput(),
                        0.0,
                        false,
                        goldenCase.getScoringCriteria(),
                        "评测用例执行异常: " + e.getCause().getMessage(),
                        0
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("评测用例执行被中断, caseId={}", goldenCase.getCaseId());
                caseResult = new CaseResult(
                        goldenCase.getCaseId(),
                        goldenCase.getQuery(),
                        null,
                        goldenCase.getExpectedOutput(),
                        0.0,
                        false,
                        goldenCase.getScoringCriteria(),
                        "评测用例执行被中断",
                        0
                );
            }
            caseResult.setDurationMs(System.currentTimeMillis() - caseStart);
            results.add(caseResult);
        }

        long totalDurationMs = System.currentTimeMillis() - totalStart;
        return reportGenerator.generate(dataset.getDatasetId(), dataset.getName(), results, totalDurationMs);
    }

    private CaseResult evaluateCase(GoldenCase goldenCase) {
        String agentCode = null;
        if (goldenCase.getBody() != null) {
            Object agentCodeObj = goldenCase.getBody().get("agentCode");
            if (agentCodeObj != null) {
                agentCode = agentCodeObj.toString();
            }
        }

        AgentRequest request = new AgentRequest()
                .agentCode(agentCode)
                .input(goldenCase.getQuery());

        String actualOutput;
        String errorMessage = null;
        try {
            AgentResult agentResult = agentEngine.run(request);
            if (agentResult.isSuccess()) {
                actualOutput = agentResult.getOutputAsText();
            } else {
                actualOutput = null;
                errorMessage = agentResult.getErrorMessage();
            }
        } catch (Exception e) {
            actualOutput = null;
            errorMessage = e.getMessage();
        }

        // 策略名统一小写查询，兼容用例填大写写法
        String criteriaKey = goldenCase.getScoringCriteria() == null
                ? null : goldenCase.getScoringCriteria().toLowerCase();
        ScoringStrategy strategy = strategyMap.get(criteriaKey);
        double score = 0.0;
        String strategyName = goldenCase.getScoringCriteria();
        if (strategy != null && actualOutput != null) {
            score = strategy.score(actualOutput, goldenCase.getExpectedOutput());
        } else if (strategy == null) {
            errorMessage = "Unknown scoring strategy: " + goldenCase.getScoringCriteria();
        }

        boolean passed = score >= 0.6;

        return new CaseResult(
                goldenCase.getCaseId(),
                goldenCase.getQuery(),
                actualOutput,
                goldenCase.getExpectedOutput(),
                score,
                passed,
                strategyName,
                errorMessage,
                0
        );
    }
}
