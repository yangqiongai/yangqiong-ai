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
package com.yangqiongai.ai.workflow.spi;

import org.springframework.context.ApplicationEvent;

/**
 * 工作流实例失联回收事件
 * @author yangqiong
 */
public class WorkflowInstanceReclaimedEvent extends ApplicationEvent {

    /**
     * 实例ID
     */
    private final String instanceId;

    /**
     * 定义名称
     */
    private final String definitionName;

    /**
     * 回收原因
     */
    private final String reason;

    public WorkflowInstanceReclaimedEvent(Object source, String instanceId, String definitionName, String reason) {
        super(source);
        this.instanceId = instanceId;
        this.definitionName = definitionName;
        this.reason = reason;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public String getReason() {
        return reason;
    }
}
