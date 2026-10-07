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
package com.yangqiongai.ai.agent.core.spi;

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent平台域默认过滤
 * @author yangqiong
 */
public class DefaultAgentScopeFilter implements AgentScopeFilter {

    @Override
    public List<Agent> filterList(List<Agent> agents) {
        List<Agent> result = new ArrayList<>();
        for (Agent agent : agents) {
            if (visible(agent)) {
                result.add(agent);
            }
        }
        return result;
    }

    @Override
    public Agent filterOne(Agent agent) {
        return visible(agent) ? agent : null;
    }

    @Override
    public void checkEditable(Agent agent) {
        if (!visible(agent)) {
            throw new AiException(AiErrorCode.FORBIDDEN.getCode(), "仅可编辑平台域Agent");
        }
    }

    /**
     * 判断Agent是否属于平台域（scopeId为空按平台域处理，兼容存量数据）
     * @param agent
     * @return
     */
    private boolean visible(Agent agent) {
        return agent == null
                || !StringUtils.hasText(agent.getScopeId())
                || PLATFORM_SCOPE.equals(agent.getScopeId());
    }
}
