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
package com.yangqiongai.ai.security.spi;

/**
 * 护栏规则（与数据库解耦的SPI契约模型，商业规则中心落库时经此转换）
 *
 * @author yangqiong
 */
public class GuardrailRuleEntity {

    /**
     * 主键
     */
    private String id;

    /**
     * 规则名称（唯一标识，同名覆盖内置规则）
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 挂载点：INPUT/SYSTEM_PROMPT/THINKING/TOOL_CALL/OUTPUT
     */
    private String hookPoint;

    /**
     * 规则类型：KEYWORD/REGEX/PII
     */
    private String ruleType;

    /**
     * 正则表达式（rule_type=REGEX时使用）
     */
    private String pattern;

    /**
     * 关键词列表，JSON数组格式（rule_type=KEYWORD时使用）
     */
    private String keywords;

    /**
     * 是否启用
     */
    private Integer isEnabled;

    /**
     * 排序优先级，越小越先执行
     */
    private Integer sortOrder;

    /**
     * 拦截提示信息
     */
    private String blockMessage;

    /**
     * 作用域ID
     */
    private String scopeId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHookPoint() {
        return hookPoint;
    }

    public void setHookPoint(String hookPoint) {
        this.hookPoint = hookPoint;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Integer getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getBlockMessage() {
        return blockMessage;
    }

    public void setBlockMessage(String blockMessage) {
        this.blockMessage = blockMessage;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
}
