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
package com.yangqiongai.ai.platform.conversation.service;

/**
 * 会话生命周期管理
 * @author yangqiong
 */
public interface SessionLifecycleService {

    /**
     * 归档不活跃会话
     * @param inactiveDays
     * @return
     */
    int archiveInactiveSessions(int inactiveDays);

    /**
     * 关闭会话并合并长期记忆，将会话摘要合并到用户长期记忆后将会话状态设为已结束
     * @param sessionId
     */
    void closeAndMerge(String sessionId);

    /**
     * 导出会话为指定格式
     * @param sessionId
     * @param format 格式：json 或 markdown
     * @return
     */
    String exportSession(String sessionId, String format);
}
