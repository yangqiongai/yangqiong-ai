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
package com.yangqiongai.ai.agent.local.workspace;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;

/**
 * 工作区删除事件（主表登记已移除，供企业侧级联清理成员/配对等关联数据）
 * @author yangqiong
 */
public class UserWorkspaceDeletedEvent {

    /**
     * 被删除的工作区快照（删除前取出）
     */
    private final WorkspaceInfo workspace;

    /**
     * 操作人
     */
    private final String operatorId;

    public UserWorkspaceDeletedEvent(WorkspaceInfo workspace, String operatorId) {
        this.workspace = workspace;
        this.operatorId = operatorId;
    }

    public WorkspaceInfo getWorkspace() {
        return workspace;
    }

    public String getOperatorId() {
        return operatorId;
    }
}
