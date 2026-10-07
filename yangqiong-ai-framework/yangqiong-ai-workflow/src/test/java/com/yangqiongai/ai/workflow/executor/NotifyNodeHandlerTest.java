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
package com.yangqiongai.ai.workflow.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import com.yangqiongai.ai.workflow.spi.NotifySendCommand;
import com.yangqiongai.ai.workflow.spi.NotifySendResult;
import com.yangqiongai.ai.workflow.spi.WorkflowNotifySender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知节点处理测试
 * @author yangqiong
 */
class NotifyNodeHandlerTest {

    private WorkflowStateStore stateStore;

    private ObjectProvider<WorkflowNotifySender> senderProvider;

    private WorkflowNotifySender sender;

    private NotifyNodeHandler handler;

    @BeforeEach
    void setUp() {
        stateStore = mock(WorkflowStateStore.class);
        senderProvider = mock(ObjectProvider.class);
        sender = mock(WorkflowNotifySender.class);
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        handler = new NotifyNodeHandler(senderProvider, new WorkflowStateService(stateStore, new ObjectMapper()),
                new ObjectMapper());
    }

    private WorkflowNode notifyNode() {
        WorkflowNode node = new WorkflowNode();
        node.setId("notify1");
        node.setName("通知运营");
        node.setType(NodeType.NOTIFY);
        node.setConfigValue("channelId", "ch-001");
        node.setConfigValue("channelType", "DINGTALK");
        node.setConfigValue("content", "商品已上架");
        return node;
    }

    @Test
    @DisplayName("发送成功：写notifyOutput变量且输出为透传JSON")
    void sendSuccess() {
        when(sender.send(any(NotifySendCommand.class))).thenReturn(NotifySendResult.ok("msg-1"));
        WorkflowState state = new WorkflowState();
        WorkflowNode node = notifyNode();
        AgentResult result = handler.executeNotifyNode(null, state, node);

        assertTrue(result.isSuccess());
        String variable = (String) state.getVariable("notifyOutput");
        assertTrue(variable.contains("\"success\":true"));
        assertTrue(variable.contains("\"messageId\":\"msg-1\""));
        assertEquals(variable, state.getVariable("notify1.notifyOutput"));
    }

    @Test    @DisplayName("Sender缺失：降级skipped且不阻塞")
    void senderMissing() {
        when(senderProvider.getIfAvailable()).thenReturn(null);
        WorkflowState state = new WorkflowState();
        AgentResult result = handler.executeNotifyNode(null, state, notifyNode());

        assertTrue(result.isSuccess());
        String variable = (String) state.getVariable("notifyOutput");
        assertTrue(variable.contains("\"skipped\":true"));
    }

    @Test
    @DisplayName("发送异常且忽略失败：success结果带error")
    void sendErrorIgnored() {
        when(sender.send(any(NotifySendCommand.class))).thenThrow(new RuntimeException("网络不可达"));
        WorkflowState state = new WorkflowState();
        AgentResult result = handler.executeNotifyNode(null, state, notifyNode());

        assertTrue(result.isSuccess());
        String variable = (String) state.getVariable("notifyOutput");
        assertTrue(variable.contains("\"success\":false"));
        assertTrue(variable.contains("网络不可达"));
    }

    @Test
    @DisplayName("发送异常且不忽略失败：节点失败")
    void sendErrorNotIgnored() {
        when(sender.send(any(NotifySendCommand.class))).thenThrow(new RuntimeException("网络不可达"));
        WorkflowNode node = notifyNode();
        node.setConfigValue("ignoreFailure", false);
        WorkflowState state = new WorkflowState();
        AgentResult result = handler.executeNotifyNode(null, state, node);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("通知失败"));
    }

    @Test
    @DisplayName("内容为空：结果记error且不调用Sender")
    void blankContent() {
        WorkflowNode node = notifyNode();
        node.setConfigValue("content", "  ");
        WorkflowState state = new WorkflowState();
        AgentResult result = handler.executeNotifyNode(null, state, node);

        assertTrue(result.isSuccess());
        String variable = (String) state.getVariable("notifyOutput");
        assertTrue(variable.contains("通知内容为空"));
    }

    @Test
    @DisplayName("异步模式：立即返回且不写notifyOutput")
    void asyncMode() {
        WorkflowNode node = notifyNode();
        node.setConfigValue("async", true);
        WorkflowState state = new WorkflowState();
        AgentResult result = handler.executeNotifyNode(null, state, node);

        assertTrue(result.isSuccess());
        assertNull(state.getVariable("notifyOutput"));
    }

    @Test
    @DisplayName("模板渲染：content与override中的变量被替换")
    void templateRender() {
        when(sender.send(any(NotifySendCommand.class))).thenReturn(NotifySendResult.ok("msg-2"));
        WorkflowNode node = notifyNode();
        node.setConfigValue("content", "商品${goodsName}已上架");
        Map<String, Object> override = new HashMap<>();
        override.put("receivers", "${operatorMobile}");
        node.setConfigValue("override", override);
        WorkflowState state = new WorkflowState();
        state.setVariable("goodsName", "手机");
        state.setVariable("operatorMobile", "13800000000");
        handler.executeNotifyNode(null, state, node);

        ArgumentCaptor<NotifySendCommand> captor = ArgumentCaptor.forClass(NotifySendCommand.class);
        verify(sender).send(captor.capture());
        assertEquals("商品手机已上架", captor.getValue().getContent());
        assertEquals("13800000000", captor.getValue().getOverrides().get("receivers"));
        assertEquals("INFO", captor.getValue().getLevel());
    }
}
