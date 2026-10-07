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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内置治理事件触发监听测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class BuiltinEventTriggerListenersTest {

    @Mock
    private AgentTriggerService triggerService;

    @InjectMocks
    private EvalRegressionTriggerListener evalRegressionListener;

    @Test
    void evalRegressionListenerFiresEvalRegressionSource() {
        when(triggerService.fireEventSource(eq(EvalRegressionTriggerListener.EVENT_SOURCE), anyString(),
                anyString())).thenReturn(List.of("tg-2"));

        evalRegressionListener.onEvalRegression(new EvalRegressionDegradedEvent("default", "agent-a",
                LocalDate.of(2026, 9, 14), 88L, new BigDecimal("0.6"), new BigDecimal("70"),
                new BigDecimal("0.9"), new BigDecimal("85")));

        verify(triggerService).fireEventSource(eq(EvalRegressionTriggerListener.EVENT_SOURCE), anyString(),
                contains("agent-a"));
    }

    @Test
    void eventSourceConstantsAreStable() {
        // 事件源标识是触发器event_source的配置契约，必须稳定
        assertThat(EvalRegressionTriggerListener.EVENT_SOURCE).isEqualTo("EVAL_REGRESSION");
    }
}
