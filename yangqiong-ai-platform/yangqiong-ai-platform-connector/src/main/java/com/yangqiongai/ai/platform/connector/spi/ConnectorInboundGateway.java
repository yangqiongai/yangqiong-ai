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
 * 连接器入站网关
 * <p>
 * 每个支持入站消息的提供商实现：验签并解析渠道回调为统一回调凭证，
 * 网关入口先应答渠道再异步处理标准化消息。
 * </p>
 * @author yangqiong
 */
public interface ConnectorInboundGateway {

    /**
     * 验签并解析渠道回调，返回异步处理凭证
     * @param request 渠道回调原始请求
     * @return 回调凭证（含即时应答报文与标准化消息）
     */
    ConnectorCallbackReceipt handle(ConnectorCallbackRequest request);
}
