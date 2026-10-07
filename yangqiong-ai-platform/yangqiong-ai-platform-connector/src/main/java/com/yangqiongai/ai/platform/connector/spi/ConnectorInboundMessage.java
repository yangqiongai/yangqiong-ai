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
package com.yangqiongai.ai.platform.connector.spi;

/**
 * 连接器入站标准化消息
 * <p>
 * 网关验签解析后产出的统一消息载体，供异步执行链路使用。
 * </p>
 * @author yangqiong
 */
public class ConnectorInboundMessage {

    /**
     * 渠道消息ID（幂等键）
     */
    private final String messageId;

    /**
     * 发送者标识（渠道userId）
     */
    private final String senderId;

    /**
     * 发送者名称
     */
    private final String senderName;

    /**
     * 会话标识（群聊id/单聊id）
     */
    private final String conversationId;

    /**
     * 消息文本
     */
    private final String text;

    /**
     * 原始报文
     */
    private final String raw;

    public ConnectorInboundMessage(String messageId, String senderId, String senderName,
                                   String conversationId, String text, String raw) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.conversationId = conversationId;
        this.text = text;
        this.raw = raw;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getText() {
        return text;
    }

    public String getRaw() {
        return raw;
    }
}
