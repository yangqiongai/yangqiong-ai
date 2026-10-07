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
 * 连接器入站回调处理凭证
 * <p>
 * 携带即时应答报文（ackPayload，渠道要求秒级响应时由网关先应答）
 * 与标准化消息（message，为空表示无需异步处理，如url验证请求已直接应答）。
 * </p>
 * @author yangqiong
 */
public class ConnectorCallbackReceipt {

    /**
     * 即时应答报文（JSON），为空时返回默认ack
     */
    private final String ackPayload;

    /**
     * 标准化入站消息，为空表示本次回调无需异步处理
     */
    private final ConnectorInboundMessage message;

    private ConnectorCallbackReceipt(String ackPayload, ConnectorInboundMessage message) {
        this.ackPayload = ackPayload;
        this.message = message;
    }

    /**
     * 仅应答（url验证等同步场景）
     * @param ackPayload 即时应答报文
     * @return
     */
    public static ConnectorCallbackReceipt ackOnly(String ackPayload) {
        return new ConnectorCallbackReceipt(ackPayload, null);
    }

    /**
     * 应答并转异步处理
     * @param ackPayload 即时应答报文，可空
     * @param message 标准化消息
     * @return
     */
    public static ConnectorCallbackReceipt async(String ackPayload, ConnectorInboundMessage message) {
        return new ConnectorCallbackReceipt(ackPayload, message);
    }

    public String getAckPayload() {
        return ackPayload;
    }

    public ConnectorInboundMessage getMessage() {
        return message;
    }
}
