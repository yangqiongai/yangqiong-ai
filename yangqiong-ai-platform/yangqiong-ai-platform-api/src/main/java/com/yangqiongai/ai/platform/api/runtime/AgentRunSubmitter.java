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
package com.yangqiongai.ai.platform.api.runtime;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;

import java.util.Map;

/**
 * Agent异步运行提交器
 * <p>
 * /submit链路的SPI：分布式运行时启用时由企业侧运行状态服务实现（预落库+抢锁+本地执行），
 * 未启用时无实现Bean，控制器回退引擎本地submitTask。
 * </p>
 * @author yangqiong
 */
public interface AgentRunSubmitter {

    /**
     * 提交异步运行
     * @param request
     * @return taskId+runId，锁被其他节点持有时taskId为空
     */
    Map<String, String> submitAsync(AgentRequest request);
}
