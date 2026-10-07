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
package com.yangqiongai.ai.agent.core.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 优雅关闭配置
 * <p>
 * GracefulShutdown中间件由适配器模块提供，本配置类仅保留条件开关和超时配置属性读取。
 * </p>
 *
 * @author yangqiong
 */
@Configuration
@ConditionalOnProperty(name = "ai.agent.shutdown.enabled", havingValue = "true", matchIfMissing = false)
public class GracefulShutdownConfiguration {

    /**
     * 优雅关闭超时时间（秒）
     */
    @Value("${ai.agent.shutdown.timeout-seconds:30}")
    private int shutdownTimeoutSeconds;

}
