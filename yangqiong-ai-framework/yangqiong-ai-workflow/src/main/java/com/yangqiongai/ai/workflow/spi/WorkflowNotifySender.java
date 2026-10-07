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

/**
 * 工作流通知发送扩展点（企业版提供实现，社区版缺失时通知节点降级跳过）
 * @author yangqiong
 */
public interface WorkflowNotifySender {

    /**
     * 按渠道配置发送通知
     * @param command
     * @return
     */
    NotifySendResult send(NotifySendCommand command);
}
