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
package com.yangqiongai.ai.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * AI平台配置属性
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * Agent模块配置
     */
    private Agent agent = new Agent();

    /**
     * RAG模块配置
     */
    private Rag rag = new Rag();

    /**
     * Wiki模块配置
     */
    private Wiki wiki = new Wiki();

    /**
     * MCP模块配置
     */
    private Mcp mcp = new Mcp();

    /**
     * Guardrails模块配置
     */
    private Guardrails guardrails = new Guardrails();

    /**
     * Skill模块配置
     */
    private Skill skill = new Skill();

    /**
     * Text2SQL模块配置
     */
    private Text2Sql text2sql = new Text2Sql();

    /**
     * 默认模型提供商（如dashscope、openai、ollama）
     */
    private String defaultProvider;

    /**
     * 技能条件映射
     */
    private Map<String, String> skillConditionalMapping;

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public Wiki getWiki() {
        return wiki;
    }

    public void setWiki(Wiki wiki) {
        this.wiki = wiki;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp;
    }

    public Guardrails getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(Guardrails guardrails) {
        this.guardrails = guardrails;
    }

    public Skill getSkill() {
        return skill;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public Text2Sql getText2sql() {
        return text2sql;
    }

    public void setText2sql(Text2Sql text2sql) {
        this.text2sql = text2sql;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, String> getSkillConditionalMapping() {
        return skillConditionalMapping;
    }

    public void setSkillConditionalMapping(Map<String, String> skillConditionalMapping) {
        this.skillConditionalMapping = skillConditionalMapping;
    }

    /**
     * Agent模块配置
     */
    public static class Agent {

        /**
         * 是否启用Agent运行时
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * RAG模块配置
     */
    public static class Rag {

        /**
         * 是否启用RAG
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Wiki模块配置
     */
    public static class Wiki {

        /**
         * 是否启用Wiki
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * MCP模块配置
     */
    public static class Mcp {

        /**
         * 是否启用MCP
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Guardrails模块配置
     */
    public static class Guardrails {

        /**
         * 是否启用Guardrails
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Skill模块配置
     */
    public static class Skill {

        /**
         * 是否启用Skill
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Text2SQL模块配置
     */
    public static class Text2Sql {

        /**
         * 是否启用Text2SQL
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
