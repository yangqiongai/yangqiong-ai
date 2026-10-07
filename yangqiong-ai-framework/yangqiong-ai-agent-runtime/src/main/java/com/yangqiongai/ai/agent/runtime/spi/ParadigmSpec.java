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
package com.yangqiongai.ai.agent.runtime.spi;

import java.util.Objects;

/**
 * 执行范式规格
 * @author yangqiong
 */
public final class ParadigmSpec {

    /**
     * 范式类型：默认ReAct执行循环
     */
    public static final String REACT = "react";

    /**
     * 范式类型：Reflexion反思（执行-自评估-反思教训-重试）
     */
    public static final String REFLEXION = "reflexion";

    /**
     * 范式类型：Self-Refine自我精炼（生成-批评-修订循环）
     */
    public static final String SELF_REFINE = "self-refine";

    /**
     * 范式类型：Plan-and-Execute（先规划后逐步执行）
     */
    public static final String PLAN_EXECUTE = "plan-execute";

    /**
     * 范式类型：ReWoo（规划期变量引用，独立步骤执行）
     */
    public static final String REWOO = "rewoo";

    /**
     * 范式类型：Self-Ask（问题分解-逐子问回答-汇总）
     */
    public static final String SELF_ASK = "self-ask";

    /**
     * 范式类型：元层路由（按问题特征动态选择范式，含回退ReAct）
     */
    public static final String AUTO = "auto";

    /**
     * 范式类型
     */
    private final String type;

    /**
     * 带工具的最大执行步数，null时用引擎默认值
     */
    private final Integer maxSteps;

    /**
     * Reflexion最大反思次数，null时用引擎默认值
     */
    private final Integer maxReflections;

    /**
     * Self-Refine最大修订次数，null时用引擎默认值
     */
    private final Integer maxRefinements;

    private ParadigmSpec(String type, Integer maxSteps, Integer maxReflections, Integer maxRefinements) {
        this.type = type;
        this.maxSteps = maxSteps;
        this.maxReflections = maxReflections;
        this.maxRefinements = maxRefinements;
    }

    /**
     * 构建仅含范式类型的规格
     * @param type
     * @return
     */
    public static ParadigmSpec ofType(String type) {
        return new ParadigmSpec(type, null, null, null);
    }

    /**
     * 构建完整规格，未知字段按null传给引擎默认值
     * @param type
     * @param maxSteps
     * @param maxReflections
     * @param maxRefinements
     * @return
     */
    public static ParadigmSpec of(String type, Integer maxSteps, Integer maxReflections, Integer maxRefinements) {
        return new ParadigmSpec(type, maxSteps, maxReflections, maxRefinements);
    }

    /**
     * 获取范式类型
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * 获取带工具的最大执行步数
     * @return
     */
    public Integer getMaxSteps() {
        return maxSteps;
    }

    /**
     * 获取Reflexion最大反思次数
     * @return
     */
    public Integer getMaxReflections() {
        return maxReflections;
    }

    /**
     * 获取Self-Refine最大修订次数
     * @return
     */
    public Integer getMaxRefinements() {
        return maxRefinements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParadigmSpec that = (ParadigmSpec) o;
        return Objects.equals(type, that.type)
                && Objects.equals(maxSteps, that.maxSteps)
                && Objects.equals(maxReflections, that.maxReflections)
                && Objects.equals(maxRefinements, that.maxRefinements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, maxSteps, maxReflections, maxRefinements);
    }

    @Override
    public String toString() {
        return "ParadigmSpec{type=" + type + ", maxSteps=" + maxSteps
                + ", maxReflections=" + maxReflections + ", maxRefinements=" + maxRefinements + "}";
    }
}
