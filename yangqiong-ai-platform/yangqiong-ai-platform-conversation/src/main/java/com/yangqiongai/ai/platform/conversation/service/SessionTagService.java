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

import com.yangqiongai.ai.platform.conversation.entity.SessionTag;

import java.util.List;

/**
 * 会话标签管理
 * @author yangqiong
 */
public interface SessionTagService {

    /**
     * 为会话添加标签
     * @param sessionId
     * @param userId
     * @param tag
     * @return
     */
    SessionTag addTag(String sessionId, String userId, String tag);

    /**
     * 移除会话标签
     * @param sessionId
     * @param tag
     */
    void removeTag(String sessionId, String tag);

    /**
     * 查询会话的所有标签
     * @param sessionId
     * @return
     */
    List<SessionTag> listBySessionId(String sessionId);

    /**
     * 查询用户的所有标签（去重）
     * @param userId
     * @return
     */
    List<String> listTagsByUserId(String userId);

    /**
     * 按标签查询会话ID列表
     * @param userId
     * @param tag
     * @return
     */
    List<String> listSessionIdsByTag(String userId, String tag);

    /**
     * 删除用户指定标签的所有记录
     * @param userId
     * @param tag
     */
    void removeUserTag(String userId, String tag);
}
