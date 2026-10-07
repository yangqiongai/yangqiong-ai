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
package com.yangqiongai.ai.open.capability.spec;

import java.util.List;

/**
 * Agent参数覆盖配置
 * <p>
 * 可选配置，用于覆盖Agent数据库表（ai_agent）的默认参数。
 * 未配置的字段使用Agent默认值，配置的字段覆盖默认值。
 * 设计原则：只做"覆盖增强"，不重复管理Agent的skill/tool/知识库绑定关系。
 * </p>
 * @author yangqiong
 */
public class AgentOverrides {

    /**
     * 覆盖模型编码（未配置则使用Agent默认模型）
     */
    private String model;

    /**
     * 覆盖温度（未配置则使用Agent默认温度）
     */
    private Double temperature;

    /**
     * 覆盖最大Token数
     */
    private Integer maxTokens;

    /**
     * 覆盖最大迭代次数（ReAct场景）
     */
    private Integer maxIterations;

    /**
     * 工具白名单（从Agent绑定的工具中筛选子集，未配置则全部可用）
     */
    private List<String> tools;

    /**
     * 技能列表（追加到Agent默认绑定的技能，未配置则使用默认绑定）
     */
    private List<String> skills;

    /**
     * 是否替换默认技能（true时仅使用skills指定的技能，false时追加到默认技能）
     */
    private boolean replaceSkills = false;

    /**
     * 知识库覆盖配置
     */
    private KnowledgeBaseOverride knowledgeBase;

    /**
     * 知识库覆盖配置
     * @author yangqiong
     */
    public static class KnowledgeBaseOverride {

        /**
         * 知识库编码列表（支持多个库同时检索）
         */
        private List<String> kbCodes;

        /**
         * 检索 topK（每个库的检索数量）
         */
        private Integer topK;

        /**
         * 检索相似度阈值（0-1）
         */
        private Double scoreThreshold;

        /**
         * 文件上传库（单值，必须属于kbCodes；未配置则该能力不支持文件上传）
         */
        private String uploadKbCode;

        public List<String> getKbCodes() {
            return kbCodes;
        }

        public void setKbCodes(List<String> kbCodes) {
            this.kbCodes = kbCodes;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        public Double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(Double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }

        public String getUploadKbCode() {
            return uploadKbCode;
        }

        public void setUploadKbCode(String uploadKbCode) {
            this.uploadKbCode = uploadKbCode;
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public boolean isReplaceSkills() {
        return replaceSkills;
    }

    public void setReplaceSkills(boolean replaceSkills) {
        this.replaceSkills = replaceSkills;
    }

    public KnowledgeBaseOverride getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(KnowledgeBaseOverride knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }
}