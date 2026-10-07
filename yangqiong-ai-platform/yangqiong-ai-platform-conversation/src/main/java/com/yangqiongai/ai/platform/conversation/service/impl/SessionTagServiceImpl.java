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
package com.yangqiongai.ai.platform.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.platform.conversation.entity.SessionTag;
import com.yangqiongai.ai.platform.conversation.mapper.SessionTagMapper;
import com.yangqiongai.ai.platform.conversation.service.SessionTagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话标签管理
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class SessionTagServiceImpl implements SessionTagService {

    private static final Logger log = LoggerFactory.getLogger(SessionTagServiceImpl.class);

    @Autowired
    private SessionTagMapper sessionTagMapper;

    @Override
    public SessionTag addTag(String sessionId, String userId, String tag) {
        // 幂等：若已存在则直接返回
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getSessionId, sessionId).eq(SessionTag::getTag, tag);
        SessionTag existing = sessionTagMapper.selectOne(wrapper);
        if (existing != null) {
            return existing;
        }
        SessionTag sessionTag = new SessionTag();
        sessionTag.setSessionId(sessionId);
        sessionTag.setUserId(userId);
        sessionTag.setTag(tag);
        sessionTagMapper.insert(sessionTag);
        log.debug("会话标签已添加: sessionId={}, tag={}", sessionId, tag);
        return sessionTag;
    }

    @Override
    public void removeTag(String sessionId, String tag) {
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getSessionId, sessionId).eq(SessionTag::getTag, tag);
        sessionTagMapper.delete(wrapper);
    }

    @Override
    public List<SessionTag> listBySessionId(String sessionId) {
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getSessionId, sessionId)
                .orderByDesc(SessionTag::getCreateTime);
        return sessionTagMapper.selectList(wrapper);
    }

    @Override
    public List<String> listTagsByUserId(String userId) {
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getUserId, userId)
                .select(SessionTag::getTag)
                .groupBy(SessionTag::getTag)
                .orderByDesc(SessionTag::getTag);
        return sessionTagMapper.selectList(wrapper).stream()
                .map(SessionTag::getTag)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listSessionIdsByTag(String userId, String tag) {
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getUserId, userId).eq(SessionTag::getTag, tag);
        return sessionTagMapper.selectList(wrapper).stream()
                .map(SessionTag::getSessionId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void removeUserTag(String userId, String tag) {
        LambdaQueryWrapper<SessionTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionTag::getUserId, userId).eq(SessionTag::getTag, tag);
        sessionTagMapper.delete(wrapper);
    }
}
