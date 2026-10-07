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
package com.yangqiongai.ai.trust.audit;

import com.yangqiongai.ai.trust.audit.entity.AgentActionLog;
import com.yangqiongai.ai.trust.audit.mapper.AgentActionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动作审计基础版
 * <p>
 * 平铺落库ai_agent_action_log，审计失败仅告警不阻断主流程；企业版叠加哈希链字段。
 * </p>
 * @author yangqiong
 */
public class ActionAuditServiceImpl implements ActionAuditService {

    private static final Logger log = LoggerFactory.getLogger(ActionAuditServiceImpl.class);

    /**
     * 动作审计配置
     */
    private final ActionAuditProperties properties;

    /**
     * 审计日志Mapper
     */
    private final AgentActionLogMapper actionLogMapper;

    public ActionAuditServiceImpl(ActionAuditProperties properties, AgentActionLogMapper actionLogMapper) {
        this.properties = properties;
        this.actionLogMapper = actionLogMapper;
    }

    @Override
    public void record(AgentActionLog actionLog) {
        if (!isEnabled() || actionLog == null) {
            return;
        }
        try {
            actionLogMapper.insert(actionLog);
        } catch (Exception e) {
            log.warn("动作审计落库失败: actionType={}, resource={}",
                    actionLog.getActionType(), actionLog.getResource(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
