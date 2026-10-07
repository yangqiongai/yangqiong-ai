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
package com.yangqiongai.ai.agent.skill.resolver;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.skill.config.SkillConditionalProperties;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认条件技能匹配器
 * @author yangqiong
 */
@Service
public class DefaultConditionalSkillMatcher implements ConditionalSkillMatcher {

    private static final String TASK_ALLOW_LIST_KEY = "agentCodes";
    private static final String META_CONSTRAINTS_KEY = "body";

    @Autowired
    private SkillConditionalProperties skillConditionalProperties;

    /**
     * 判断请求是否满足条件匹配能力
     * @param request
     * @return
     */
    @Override
    public boolean matches(AgentRequest request) {
        if (request == null) {
            return false;
        }
        String agentCode = request.getAgentCode();
        Map<String, Object> meta = request.getBody();
        return agentCode != null && !agentCode.isBlank()
                && meta != null && !meta.isEmpty();
    }

    /**
     * 判断技能是否适用于当前请求
     * @param skill
     * @param request
     * @return
     */
    @Override
    public boolean isSkillApplicable(SkillDefinition skill, AgentRequest request) {
        if (skill == null || request == null) {
            return false;
        }

        Map<String, Object> constraints = skill.getConditions();
        if (constraints == null || constraints.isEmpty()) {
            return true;
        }

        if (!checkTaskAllowList(constraints, request.getAgentCode())) {
            return false;
        }

        return checkMetaConstraints(constraints, request.getBody());
    }

    /**
     * 校验任务白名单（conditions中agentCodes键为技能元数据契约，保留历史兼容）
     * @param constraints
     * @param agentCode
     * @return
     */
    private boolean checkTaskAllowList(Map<String, Object> constraints, String agentCode) {
        Object allowListObj = constraints.get(TASK_ALLOW_LIST_KEY);
        if (allowListObj == null) {
            return true;
        }

        List<String> allowedAgentEntitysCodes = extractStringList(allowListObj);
        if (allowedAgentEntitysCodes.isEmpty()) {
            return true;
        }

        if (agentCode == null || agentCode.isBlank()) {
            return false;
        }

        return allowedAgentEntitysCodes.contains(agentCode);
    }

    /**
     * 校验请求数据体约束
     * @param constraints
     * @param requestMeta
     * @return
     */
    @SuppressWarnings("unchecked")
    private boolean checkMetaConstraints(Map<String, Object> constraints, Map<String, Object> requestMeta) {
        Object metaConstraintsObj = constraints.get(META_CONSTRAINTS_KEY);
        if (metaConstraintsObj == null) {
            return true;
        }

        if (!(metaConstraintsObj instanceof Map)) {
            return true;
        }

        Map<String, Object> metaConstraints = (Map<String, Object>) metaConstraintsObj;
        if (metaConstraints.isEmpty()) {
            return true;
        }

        if (requestMeta == null || requestMeta.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> constraint : metaConstraints.entrySet()) {
            String constraintKey = constraint.getKey();
            Object expectedVal = constraint.getValue();
            Object actualVal = requestMeta.get(constraintKey);

            if (!matchValue(expectedVal, actualVal)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 比对期望值与实际值
     * @param expected
     * @param actual
     * @return
     */
    private boolean matchValue(Object expected, Object actual) {
        if (expected == null) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }

        if (expected instanceof List) {
            List<?> expectedList = (List<?>) expected;
            String actualStr = actual.toString();
            return expectedList.stream().anyMatch(e -> e != null && e.toString().equals(actualStr));
        }

        return expected.toString().equals(actual.toString());
    }

    /**
     * 从对象中提取字符串列表
     * @param obj
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object obj) {
        if (obj instanceof List) {
            return ((List<?>) obj).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (obj instanceof String) {
            return Arrays.stream(((String) obj).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
