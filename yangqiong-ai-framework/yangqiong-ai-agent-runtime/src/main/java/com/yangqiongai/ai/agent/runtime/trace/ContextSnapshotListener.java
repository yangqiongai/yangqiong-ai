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
package com.yangqiongai.ai.agent.runtime.trace;

/**
 * 上下文快照监听SPI
 * <p>
 * 每次模型调用前引擎组装好上下文消息后回调，平台侧实现批量落库等处理。
 * 未注入监听器时引擎完全跳过采集，回调异常由引擎吞掉不影响主流程。
 * </p>
 * @author yangqiong
 */
public interface ContextSnapshotListener {

    /**
     * 模型调用前的上下文快照回调
     * @param snapshot
     */
    void onSnapshot(ContextSnapshot snapshot);
}
