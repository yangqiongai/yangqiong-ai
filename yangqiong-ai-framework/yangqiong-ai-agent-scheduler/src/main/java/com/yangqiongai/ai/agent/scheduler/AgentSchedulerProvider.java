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
package com.yangqiongai.ai.agent.scheduler;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;

/**
 * Agent调度器提供者
 * <p>
 * 可选接入点：存在实现（如平台集群模块）时用户级调度使用外部调度器，
 * 否则自建内存调度器。社区版零感知。
 * </p>
 * @author yangqiong
 */
public interface AgentSchedulerProvider {

    /**
     * 提供调度器实例
     * @return
     * @throws SchedulerException
     */
    Scheduler getScheduler() throws SchedulerException;
}
