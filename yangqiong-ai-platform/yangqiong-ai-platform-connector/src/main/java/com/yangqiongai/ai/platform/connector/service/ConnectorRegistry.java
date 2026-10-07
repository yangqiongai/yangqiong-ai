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
package com.yangqiongai.ai.platform.connector.service;

import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接器提供商注册中心
 * <p>
 * 收集Spring容器内全部ConnectorProvider实现，按providerCode索引，
 * 管理面目录与工具产出均经此路由。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorRegistry {

    /**
     * 提供商索引（providerCode -> 实现）
     */
    private final Map<String, ConnectorProvider> providers;

    public ConnectorRegistry(List<ConnectorProvider> providerList) {
        Map<String, ConnectorProvider> index = new LinkedHashMap<>();
        for (ConnectorProvider provider : providerList) {
            String code = provider.providerCode();
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("连接器提供商标识不能为空");
            }
            ConnectorProvider existing = index.putIfAbsent(code, provider);
            if (existing != null) {
                throw new IllegalStateException("连接器提供商标识重复: " + code);
            }
        }
        // 保留声明顺序（目录展示稳定），Map.copyOf会丢失插入顺序
        this.providers = Collections.unmodifiableMap(index);
    }

    /**
     * 获取全部提供商目录
     * @return
     */
    public List<ConnectorProvider> listProviders() {
        return new ArrayList<>(providers.values());
    }

    /**
     * 按编码获取提供商
     * @param providerCode
     * @return 提供商，不存在返回null
     */
    public ConnectorProvider getProvider(String providerCode) {
        return providers.get(providerCode);
    }

    /**
     * 按编码获取提供商描述
     * @param providerCode
     * @return 描述，提供商不存在返回null
     */
    public ConnectorDescriptor getDescriptor(String providerCode) {
        ConnectorProvider provider = providers.get(providerCode);
        return provider == null ? null : provider.descriptor();
    }
}
