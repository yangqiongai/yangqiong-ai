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
package com.yangqiongai.ai.open.capability.engine;

import java.util.List;
import java.util.Map;

/**
 * 能力调用请求
 * <p>
 * 统一的开放API请求格式，业务系统只需传capability/dataContextRefs/arguments。
 * </p>
 * @author yangqiong
 */
public class CapabilityRequest {

    /**
     * 能力编码
     */
    private String capability;

    /**
     * 数据上下文引用列表
     */
    private List<String> dataContextRefs;

    /**
     * 能力参数（符合能力入参Schema）
     */
    private Map<String, Object> arguments;

    /**
     * 去重键（可选，相同键的请求在有效期内返回缓存结果）
     */
    private String dedupKey;

    /**
     * 调用方标识
     */
    private String caller;

    /**
     * 调用方作用域ID
     */
    private String scopeId;

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public List<String> getDataContextRefs() {
        return dataContextRefs;
    }

    public void setDataContextRefs(List<String> dataContextRefs) {
        this.dataContextRefs = dataContextRefs;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
}