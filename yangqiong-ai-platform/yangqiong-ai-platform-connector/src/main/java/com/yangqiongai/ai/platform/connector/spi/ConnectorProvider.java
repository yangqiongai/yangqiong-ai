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

import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;

import java.util.List;

/**
 * 连接器提供商
 * <p>
 * 每渠道一个实现，代码内置目录：声明静态能力描述、按实例配置产出Agent工具集、
 * 按需创建入站网关。实现用纯HTTP客户端，不引三方SDK。
 * </p>
 * @author yangqiong
 */
public interface ConnectorProvider {

    /**
     * 提供商标识（dingtalk/wecom/feishu/database/docparser）
     * @return
     */
    String providerCode();

    /**
     * 提供商描述（名称/分类/凭证字段Schema/配置字段Schema/工具清单声明）
     * @return
     */
    ConnectorDescriptor descriptor();

    /**
     * 是否支持适配指定实例（版本兼容判断，默认true）
     * @param instance
     * @return
     */
    default boolean supports(ConnectorInstance instance) {
        return true;
    }

    /**
     * 按实例配置+凭证产出Agent工具集
     * @param instance
     * @param credential 解密后的凭证视图
     * @return
     */
    List<AgentTool> createTools(ConnectorInstance instance, ConnectorCredentialView credential);

    /**
     * 创建入站网关（不支持入站的提供商返回null）
     * @param instance
     * @param credential 解密后的凭证视图
     * @return
     */
    default ConnectorInboundGateway createGateway(ConnectorInstance instance, ConnectorCredentialView credential) {
        return null;
    }

    /**
     * 凭证连通性测试（调渠道轻量接口验证凭证有效性）
     * @param credential 解密后的凭证视图
     * @return 失败原因，通过时返回null
     */
    default String testCredential(ConnectorCredentialView credential) {
        return "该提供商暂不支持连通性测试";
    }

    /**
     * 回复入站消息（异步处理完成后经渠道回传给发送者所在会话）
     * @param instance
     * @param credential 解密后的凭证视图
     * @param message 入站标准化消息
     * @param replyText 回复文本
     * @return 渠道回执标识，不支持时返回null
     */
    default String reply(ConnectorInstance instance, ConnectorCredentialView credential,
                         ConnectorInboundMessage message, String replyText) {
        return null;
    }
}
