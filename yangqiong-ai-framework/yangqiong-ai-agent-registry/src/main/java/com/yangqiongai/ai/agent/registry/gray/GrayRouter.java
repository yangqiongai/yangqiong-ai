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
package com.yangqiongai.ai.agent.registry.gray;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.registry.entity.AgentGrayRule;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentGrayRuleMapper;
import com.yangqiongai.ai.agent.registry.model.GrayRuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灰度路由器
 * <p>
 * 仅匹配ACTIVE且在时间窗口内的规则，按create_time,id升序取首条命中规则。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class GrayRouter {

    private static final Logger log = LoggerFactory.getLogger(GrayRouter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AgentGrayRuleMapper grayRuleMapper;

    /**
     * 灰度路由决策
     * @param agentCode
     * @param userId
     * @param scopeId
     * @return
     */
    public GrayDecision route(String agentCode, String userId, String scopeId) {
        List<AgentGrayRule> rules = grayRuleMapper.selectList(new LambdaQueryWrapper<AgentGrayRule>()
                .eq(AgentGrayRule::getAgentCode, agentCode)
                .eq(AgentGrayRule::getStatus, "ACTIVE")
                .orderByAsc(AgentGrayRule::getCreateTime)
                .orderByAsc(AgentGrayRule::getId));
        if (rules == null || rules.isEmpty()) {
            return GrayDecision.miss();
        }
        LocalDateTime now = LocalDateTime.now();
        for (AgentGrayRule rule : rules) {
            // 仅ACTIVE规则参与路由
            if (!"ACTIVE".equals(rule.getStatus())) {
                continue;
            }
            // 时间窗口过滤（start/end为null视为不限）
            if (rule.getStartTime() != null && now.isBefore(rule.getStartTime())) {
                continue;
            }
            if (rule.getEndTime() != null && now.isAfter(rule.getEndTime())) {
                continue;
            }
            if (matches(rule, userId, scopeId)) {
                return GrayDecision.hit(rule);
            }
        }
        return GrayDecision.miss();
    }

    /**
     * 判断规则是否命中
     * @param rule
     * @param userId
     * @param scopeId
     * @return
     */
    private boolean matches(AgentGrayRule rule, String userId, String scopeId) {
        String type = rule.getRuleType();
        if (GrayRuleType.USER_HASH.name().equals(type)) {
            return matchesUserHash(rule, userId);
        }
        if (GrayRuleType.USER_WHITELIST.name().equals(type)) {
            return matchesList(rule.getRuleValue(), userId);
        }
        if (GrayRuleType.SCOPE_LIST.name().equals(type)) {
            return matchesList(rule.getRuleValue(), scopeId);
        }
        log.warn("未知灰度规则类型: {}", type);
        return false;
    }

    /**
     * 用户哈希百分比匹配：Math.floorMod((agentCode#userId).hashCode(), 100) < percent
     * @param rule
     * @param userId
     * @return
     */
    private boolean matchesUserHash(AgentGrayRule rule, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        int percent = rule.getGrayPercent() != null ? rule.getGrayPercent() : 0;
        return Math.floorMod((rule.getAgentCode() + "#" + userId).hashCode(), 100) < percent;
    }

    /**
     * JSON数组名单匹配
     * @param ruleValue
     * @param value
     * @return
     */
    private boolean matchesList(String ruleValue, String value) {
        if (ruleValue == null || ruleValue.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        try {
            for (var item : MAPPER.readTree(ruleValue)) {
                if (item.isTextual() && value.equals(item.asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("灰度规则值解析失败: {}", ruleValue, e);
            return false;
        }
    }
}
