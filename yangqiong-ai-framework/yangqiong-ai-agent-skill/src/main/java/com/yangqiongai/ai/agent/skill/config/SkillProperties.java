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
package com.yangqiongai.ai.agent.skill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 技能模块配置属性
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.skill")
public class SkillProperties {

    /**
     * 是否启用技能模块
     */
    private boolean enabled = true;

    /**
     * 安全扫描配置
     */
    private Security security = new Security();

    /**
     * 技能生成配置
     */
    private Generation generation = new Generation();

    /**
     * 使用量追踪配置
     */
    private UsageTracking usageTracking = new UsageTracking();

    /**
     * 条件技能映射（key=agentCode或通配符*，value=逗号分隔的skillId列表）
     */
    private Map<String, String> conditionalMapping;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public UsageTracking getUsageTracking() {
        return usageTracking;
    }

    public void setUsageTracking(UsageTracking usageTracking) {
        this.usageTracking = usageTracking;
    }

    public Map<String, String> getConditionalMapping() {
        return conditionalMapping;
    }

    public void setConditionalMapping(Map<String, String> conditionalMapping) {
        this.conditionalMapping = conditionalMapping;
    }

    /**
     * 安全扫描配置
     * @author yangqiong
     */
    public static class Security {

        /**
         * 是否启用安全扫描
         */
        private boolean enabled = true;

        /**
         * 需要扫描的技能来源类型
         */
        private List<String> scanSources = List.of("UPLOADED", "GENERATED");

        /**
         * 默认信任等级（未配置trustLevel时使用）
         */
        private String defaultTrustLevel = "COMMUNITY";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getScanSources() {
            return scanSources;
        }

        public void setScanSources(List<String> scanSources) {
            this.scanSources = scanSources;
        }

        public String getDefaultTrustLevel() {
            return defaultTrustLevel;
        }

        public void setDefaultTrustLevel(String defaultTrustLevel) {
            this.defaultTrustLevel = defaultTrustLevel;
        }
    }

    /**
     * 技能生成配置
     * @author yangqiong
     */
    public static class Generation {

        /**
         * 生成技能使用的模型编码
         */
        private String modelCode = "deepseek";

        /**
         * Markdown正文必须包含的章节标题
         */
        private List<String> requiredSections = List.of("## 目标", "## 工作流", "## 输入", "## 输出");

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }

        public List<String> getRequiredSections() {
            return requiredSections;
        }

        public void setRequiredSections(List<String> requiredSections) {
            this.requiredSections = requiredSections;
        }
    }

    /**
     * 使用量追踪配置
     * @author yangqiong
     */
    public static class UsageTracking {

        /**
         * 是否启用使用量追踪
         */
        private boolean enabled = true;

        /**
         * 排行榜默认返回数量
         */
        private int topLimit = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopLimit() {
            return topLimit;
        }

        public void setTopLimit(int topLimit) {
            this.topLimit = topLimit;
        }
    }
}
