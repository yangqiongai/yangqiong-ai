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
package com.yangqiongai.ai.agent.registry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 注册中心配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.registry")
public class AgentRegistryProperties {

    /**
     * 是否启用注册中心
     */
    private boolean enabled = true;

    /**
     * 审批超时时间(分钟)
     */
    private int approvalTimeoutMinutes = 30;

    /**
     * 灰度解析缓存过期时间(秒)
     */
    private int grayCacheTtlSeconds = 30;

    /**
     * 灰度解析缓存最大条目数
     */
    private long grayCacheMaxSize = 500;

    /**
     * 是否启用发布串行锁
     */
    private boolean publishLockEnabled = true;

    /**
     * 当前运行环境档编码(default/dev/test/prod)
     */
    private String profile = "default";

    /**
     * 是否启用配置漂移检测
     */
    private boolean driftEnabled = false;

    /**
     * 漂移比对的治理字段白名单
     */
    private List<String> driftGovernedFields = Arrays.asList(
            "model", "temperature", "maxIterations", "sysPrompt", "prompt",
            "tools", "mcpServers", "skills", "knowledgeBase", "knowledge");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getApprovalTimeoutMinutes() {
        return approvalTimeoutMinutes;
    }

    public void setApprovalTimeoutMinutes(int approvalTimeoutMinutes) {
        this.approvalTimeoutMinutes = approvalTimeoutMinutes;
    }

    public int getGrayCacheTtlSeconds() {
        return grayCacheTtlSeconds;
    }

    public void setGrayCacheTtlSeconds(int grayCacheTtlSeconds) {
        this.grayCacheTtlSeconds = grayCacheTtlSeconds;
    }

    public long getGrayCacheMaxSize() {
        return grayCacheMaxSize;
    }

    public void setGrayCacheMaxSize(long grayCacheMaxSize) {
        this.grayCacheMaxSize = grayCacheMaxSize;
    }

    public boolean isPublishLockEnabled() {
        return publishLockEnabled;
    }

    public void setPublishLockEnabled(boolean publishLockEnabled) {
        this.publishLockEnabled = publishLockEnabled;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public boolean isDriftEnabled() {
        return driftEnabled;
    }

    public void setDriftEnabled(boolean driftEnabled) {
        this.driftEnabled = driftEnabled;
    }

    public List<String> getDriftGovernedFields() {
        return driftGovernedFields;
    }

    public void setDriftGovernedFields(List<String> driftGovernedFields) {
        this.driftGovernedFields = driftGovernedFields;
    }
}
