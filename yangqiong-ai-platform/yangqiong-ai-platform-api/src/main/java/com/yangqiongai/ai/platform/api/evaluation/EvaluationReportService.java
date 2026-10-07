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

import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 评测报告对比
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(prefix = "ai.agent.evaluation", name = "enabled", havingValue = "true")
public class EvaluationReportService {

    @Autowired
    private EvalRunRepository evalRunRepository;

    /**
     * 两次运行对比(整体指标diff+按case_no对齐的用例级矩阵)
     * @param leftId
     * @param rightId
     * @return
     */
    public Map<String, Object> compare(Long leftId, Long rightId) {
        EvalRunEntity left = requireRun(leftId);
        EvalRunEntity right = requireRun(rightId);
        List<EvalRunCaseEntity> leftCases = evalRunRepository.findRunCases(leftId);
        List<EvalRunCaseEntity> rightCases = evalRunRepository.findRunCases(rightId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", runSummary(left));
        result.put("right", runSummary(right));
        result.put("summaryDiff", summaryDiff(left, right));
        result.put("caseMatrix", caseMatrix(leftCases, rightCases));
        return result;
    }

    /**
     * 校验运行存在
     * @param runId
     * @return
     */
    private EvalRunEntity requireRun(Long runId) {
        EvalRunEntity run = evalRunRepository.findRunById(runId);
        if (run == null) {
            throw new IllegalArgumentException("评测运行不存在: " + runId);
        }
        return run;
    }

    /**
     * 运行整体指标概要
     * @param run
     * @return
     */
    private Map<String, Object> runSummary(EvalRunEntity run) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", run.getId());
        summary.put("datasetCode", run.getDatasetCode());
        summary.put("agentCode", run.getAgentCode());
        summary.put("status", run.getStatus());
        summary.put("totalCases", run.getTotalCases());
        summary.put("passedCases", run.getPassedCases());
        summary.put("failedCases", run.getFailedCases());
        summary.put("avgScore", run.getAvgScore());
        summary.put("threshold", run.getThreshold());
        summary.put("finishedAt", run.getFinishedAt());
        return summary;
    }

    /**
     * 整体指标diff(右减左)
     * @param left
     * @param right
     * @return
     */
    private Map<String, Object> summaryDiff(EvalRunEntity left, EvalRunEntity right) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("avgScoreLeft", left.getAvgScore());
        diff.put("avgScoreRight", right.getAvgScore());
        diff.put("avgScoreDelta", delta(left.getAvgScore(), right.getAvgScore()));
        diff.put("passedCasesLeft", left.getPassedCases());
        diff.put("passedCasesRight", right.getPassedCases());
        diff.put("passedCasesDelta", intDelta(left.getPassedCases(), right.getPassedCases()));
        diff.put("failedCasesLeft", left.getFailedCases());
        diff.put("failedCasesRight", right.getFailedCases());
        diff.put("failedCasesDelta", intDelta(left.getFailedCases(), right.getFailedCases()));
        diff.put("totalCasesLeft", left.getTotalCases());
        diff.put("totalCasesRight", right.getTotalCases());
        diff.put("statusLeft", left.getStatus());
        diff.put("statusRight", right.getStatus());
        return diff;
    }

    /**
     * 用例级矩阵(按case_no对齐，右侧score减左侧为正=改善)
     * @param leftCases
     * @param rightCases
     * @return
     */
    private List<Map<String, Object>> caseMatrix(List<EvalRunCaseEntity> leftCases, List<EvalRunCaseEntity> rightCases) {
        Map<String, EvalRunCaseEntity> leftByNo = byCaseNo(leftCases);
        Map<String, EvalRunCaseEntity> rightByNo = byCaseNo(rightCases);
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (String caseNo : new TreeMap<>(leftByNo).keySet()) {
            matrix.add(caseRow(caseNo, leftByNo.get(caseNo), rightByNo.get(caseNo)));
        }
        for (String caseNo : new TreeMap<>(rightByNo).keySet()) {
            if (!leftByNo.containsKey(caseNo)) {
                matrix.add(caseRow(caseNo, null, rightByNo.get(caseNo)));
            }
        }
        return matrix;
    }

    /**
     * 单用例对比行
     * @param caseNo
     * @param left
     * @param right
     * @return
     */
    private Map<String, Object> caseRow(String caseNo, EvalRunCaseEntity left, EvalRunCaseEntity right) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseNo", caseNo);
        row.put("presentLeft", left != null);
        row.put("presentRight", right != null);
        row.put("expectedOutput", left != null ? left.getExpectedOutput()
                : right != null ? right.getExpectedOutput() : null);
        row.put("scoreLeft", left != null ? left.getScore() : null);
        row.put("scoreRight", right != null ? right.getScore() : null);
        row.put("scoreDelta", left != null && right != null ? delta(left.getScore(), right.getScore()) : null);
        row.put("passedLeft", left != null ? left.getPassed() : null);
        row.put("passedRight", right != null ? right.getPassed() : null);
        if (left != null && right != null) {
            row.put("passChanged", !Integer.valueOf(left.getPassed() == null ? 0 : left.getPassed())
                    .equals(right.getPassed() == null ? 0 : right.getPassed()));
        } else {
            row.put("passChanged", null);
        }
        return row;
    }

    /**
     * 明细按case_no索引
     * @param cases
     * @return
     */
    private Map<String, EvalRunCaseEntity> byCaseNo(List<EvalRunCaseEntity> cases) {
        Map<String, EvalRunCaseEntity> byNo = new LinkedHashMap<>();
        for (EvalRunCaseEntity caseEntity : cases) {
            byNo.put(caseEntity.getCaseNo(), caseEntity);
        }
        return byNo;
    }

    /**
     * 分数差值(右减左，4位小数)
     * @param left
     * @param right
     * @return
     */
    private BigDecimal delta(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return right.subtract(left).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 整数差值(右减左)
     * @param left
     * @param right
     * @return
     */
    private Integer intDelta(Integer left, Integer right) {
        if (left == null || right == null) {
            return null;
        }
        return right - left;
    }
}
