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
package com.yangqiongai.ai.rag.grader;

import java.util.Collections;
import java.util.List;

/**
 * 上下文评估结果
 * @author yangqiong
 */
public class ContextGradeResult {

    /**
     * 证据是否足以回答查询
     */
    private final boolean sufficient;

    /**
     * 缺失维度（如时效性、具体数据等，用于提示重检索方向）
     */
    private final List<String> missingDimensions;

    /**
     * 置信度 0.0-1.0
     */
    private final double confidence;

    /**
     * LLM给出的简短理由（用于日志）
     */
    private final String reason;

    private ContextGradeResult(boolean sufficient, List<String> missingDimensions,
                                double confidence, String reason) {
        this.sufficient = sufficient;
        this.missingDimensions = missingDimensions != null
                ? Collections.unmodifiableList(missingDimensions) : Collections.emptyList();
        this.confidence = confidence;
        this.reason = reason;
    }

    /**
     * 构造"充分"评估结果（用于降级场景）
     * @param reason
     * @return
     */
    public static ContextGradeResult sufficient(String reason) {
        return new ContextGradeResult(true, Collections.emptyList(), 1.0, reason);
    }

    /**
     * 构造"不充分"评估结果
     * @param missingDimensions
     * @param confidence
     * @param reason
     * @return
     */
    public static ContextGradeResult insufficient(List<String> missingDimensions,
                                                   double confidence, String reason) {
        return new ContextGradeResult(false, missingDimensions, confidence, reason);
    }

    public boolean isSufficient() {
        return sufficient;
    }

    public List<String> getMissingDimensions() {
        return missingDimensions;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ContextGradeResult{sufficient=" + sufficient
                + ", missing=" + missingDimensions
                + ", confidence=" + confidence
                + ", reason='" + reason + "'}";
    }
}
