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
package com.yangqiongai.ai.memory.preference;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.DeclaredPreference;
import com.yangqiongai.ai.agent.core.provider.PreferenceRecorderProvider;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户偏好记录器实现
 * <p>
 * 整合两种偏好识别渠道：
 * 1. 请求参数式：从 AgentRequest.body.declaredPreferences 读取结构化偏好
 * 2. 口令式：从 AgentRequest.input 文本中通过 PreferenceCommandParser 解析
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(prefix = "ai.memory.preference.recorder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PreferenceRecorderProviderImpl implements PreferenceRecorderProvider {

    private static final Logger log = LoggerFactory.getLogger(PreferenceRecorderProviderImpl.class);

    private static final String SOURCE_USER_DECLARED = "USER_DECLARED";

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    @Autowired
    private PreferenceCommandParser preferenceCommandParser;

    /**
     * 从Agent请求中识别并持久化用户偏好
     * @param request
     * @return
     */
    @Override
    public int recordFromRequest(AgentRequest request) {
        if (request == null) {
            return 0;
        }
        String userId = request.getUserId();
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        String sessionId = request.getSessionId();

        // 方式2：请求参数式偏好
        List<DeclaredPreference> declared = request.getDeclaredPreferences();
        int recorded = record(userId, sessionId, declared);

        // 方式1：口令式偏好（从输入文本解析）
        String inputText = request.getInputAsText();
        if (inputText != null && !inputText.isBlank()) {
            List<DeclaredPreference> fromCommand = preferenceCommandParser.parse(inputText);
            recorded += record(userId, sessionId, fromCommand);
        }

        if (recorded > 0) {
            log.info("用户偏好记录完成: userId={}, sessionId={}, recorded={}", userId, sessionId, recorded);
        }
        return recorded;
    }

    /**
     * 直接记录一组显式偏好
     * @param userId
     * @param sessionId
     * @param preferences
     * @return
     */
    @Override
    public int record(String userId, String sessionId, List<DeclaredPreference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return 0;
        }
        int recorded = 0;
        for (DeclaredPreference pref : preferences) {
            try {
                Long id = longTermMemoryManager.recordPreference(
                        userId, pref.getKey(), pref.getContent(), SOURCE_USER_DECLARED, sessionId);
                if (id != null) {
                    recorded++;
                }
            } catch (Exception e) {
                log.warn("偏好记录失败: userId={}, key={}", userId, pref.getKey(), e);
            }
        }
        return recorded;
    }
}
