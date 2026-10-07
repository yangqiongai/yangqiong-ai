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
package com.yangqiongai.ai.trigger.listener;

import com.yangqiongai.ai.evaluation.regression.EvalRegressionDegradedEvent;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评测回归退化事件触发
 * <p>
 * 回归评测环比下降超阈值时触发处置Agent（如"退化→回滚灰度"）。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnClass(EvalRegressionDegradedEvent.class)
@ConditionalOnProperty(prefix = "ai.agent.trigger.event-listener", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EvalRegressionTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(EvalRegressionTriggerListener.class);

    /**
     * 事件源标识（触发器event_source配置项）
     */
    public static final String EVENT_SOURCE = "EVAL_REGRESSION";

    @Autowired
    private AgentTriggerService triggerService;

    @EventListener
    public void onEvalRegression(EvalRegressionDegradedEvent event) {
        String dedupKey = AgentTriggerService.sha256(event.getAgentCode() + "|" + event.getRunDate()
                + "|" + event.getEvalRunId());
        String payload = "评测回归退化: agent=" + event.getAgentCode()
                + ", date=" + event.getRunDate()
                + ", passRate=" + event.getPassRate()
                + ", prevPassRate=" + event.getPrevPassRate()
                + ", evalRunId=" + event.getEvalRunId();
        List<String> codes = triggerService.fireEventSource(EVENT_SOURCE, dedupKey, payload);
        if (!codes.isEmpty()) {
            log.info("评测回归退化事件触发处置Agent: agent={}, triggers={}", event.getAgentCode(), codes);
        }
    }
}
