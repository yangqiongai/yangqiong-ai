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
package com.yangqiongai.ai.agent.core;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.common.sse.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent引擎
 * @author yangqiong
 */
public interface AgentEngine {

    /**
     * 同步运行
     * @param request
     * @return
     */
    AgentResult run(AgentRequest request);

    /**
     * 流式执行，返回结构化事件流
     * @param request
     * @return
     */
    Flux<StreamEvent> stream(AgentRequest request);

    /**
     * 恢复澄清续跑（用户提交澄清答案后调用，返回续跑事件流）
     * @param sessionId
     * @param toolCallId
     * @param answer
     * @return
     */
    Flux<StreamEvent> resumeClarification(String sessionId, String toolCallId, String answer);

    /**
     * 恢复引擎确认续跑（用户批准或拒绝后调用，返回续跑事件流）
     * @param sessionId
     * @param approved
     * @param operator
     * @param reason
     * @return
     */
    Flux<StreamEvent> resumeConfirm(String sessionId, boolean approved, String operator, String reason);

    /**
     * 提交异步任务
     * @param request
     * @return
     */
    String submitTask(AgentRequest request);

    /**
     * 执行队列调度器抢占的任务（复用既有执行链路，不重复创建DB任务记录）
     * @param taskId
     * @param request
     * @return
     */
    String executeClaimed(String taskId, AgentRequest request);

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    Map<String, Object> queryTask(String taskId);

    /**
     * 取消异步任务
     * @param taskId
     * @return 是否成功发起取消
     */
    boolean cancelTask(String taskId);
}
