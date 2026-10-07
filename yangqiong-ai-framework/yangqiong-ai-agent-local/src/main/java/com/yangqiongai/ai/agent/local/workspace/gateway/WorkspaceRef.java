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
package com.yangqiongai.ai.agent.local.workspace.gateway;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;

import java.util.Locale;

/**
 * 工作区路由引用
 * @author yangqiong
 */
public class WorkspaceRef {

    /**
     * 服务器工作区类型（现状默认，文件落服务器磁盘）
     */
    public static final String TYPE_SERVER = "SERVER";

    /**
     * 连接器工作区类型（预留，文件落用户电脑连接器）
     */
    public static final String TYPE_CONNECTOR = "CONNECTOR";

    /**
     * 浏览器桥工作区类型（预留，文件落用户浏览器）
     */
    public static final String TYPE_BROWSER = "BROWSER";

    /**
     * 工作区ID
     */
    private Long workspaceId;

    /**
     * 工作区类型
     */
    private String type;

    /**
     * 工作区名称
     */
    private String name;

    /**
     * 根目录绝对路径（仅SERVER类型有值，其余类型真实根路径由对应执行端保管）
     */
    private String rootPath;

    /**
     * 归属作用域
     */
    private String scopeId;

    /**
     * 归属用户
     */
    private String userId;

    /**
     * 绑定设备ID（CONNECTOR类型预留）
     */
    private String deviceId;

    /**
     * 登记来源（预留：USER/SHARED/SYSTEM_DEFAULT）
     */
    private String source;

    /**
     * Agent运行ID（Agent工具调用链路透传，供执行端审计关联运行；非Agent调用为空）
     */
    private String runId;

    /**
     * 由工作区登记信息构建路由引用
     * @param info
     * @return
     */
    public static WorkspaceRef of(WorkspaceInfo info) {
        WorkspaceRef ref = new WorkspaceRef();
        ref.workspaceId = info.getId();
        ref.type = info.getType() == null || info.getType().isBlank()
                ? TYPE_SERVER : info.getType().trim().toUpperCase(Locale.ROOT);
        ref.name = info.getName();
        // 仅SERVER类型携带服务器磁盘根路径，其余类型服务端仅持不透明标识
        ref.rootPath = TYPE_SERVER.equals(ref.type) ? info.getRootPath() : null;
        ref.scopeId = info.getScopeId();
        ref.userId = info.getUserId();
        return ref;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}
