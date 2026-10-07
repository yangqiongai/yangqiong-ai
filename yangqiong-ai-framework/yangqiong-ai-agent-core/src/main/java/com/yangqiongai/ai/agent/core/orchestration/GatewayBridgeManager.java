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
package com.yangqiongai.ai.agent.core.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 子代理网关桥管理器
 * <p>
 * 原 agentscope SubagentGatewayBridge/Agent/OutboundAddress 依赖已移除，
 * 网关桥创建、销毁、暴露等方法已删除，待框架层类型替代后补充实现。
 * </p>
 * @author yangqiong
 */
@Slf4j
@Service
public class GatewayBridgeManager {

}
