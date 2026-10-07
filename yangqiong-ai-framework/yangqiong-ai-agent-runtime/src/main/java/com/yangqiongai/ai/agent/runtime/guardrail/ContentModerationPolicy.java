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
package com.yangqiongai.ai.agent.runtime.guardrail;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

/**
 * 内容审查策略
 * <p>
 * 对进入模型的消息执行内容审查，裁决放行、脱敏或中止。
 * </p>
 * @author yangqiong
 */
public interface ContentModerationPolicy {

    /**
     * 审查消息内容
     * @param message 待审查消息
     * @return 裁决结果
     */
    ModerationVerdict moderate(AgentMessage message);
}
