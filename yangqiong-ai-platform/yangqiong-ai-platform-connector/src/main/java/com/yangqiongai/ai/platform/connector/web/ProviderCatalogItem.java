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
package com.yangqiongai.ai.platform.connector.web;

import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;

/**
 * 提供商目录项
 * @author yangqiong
 */
public class ProviderCatalogItem {

    /**
     * 提供商编码
     */
    private final String providerCode;

    /**
     * 提供商描述
     */
    private final ConnectorDescriptor descriptor;

    public ProviderCatalogItem(String providerCode, ConnectorDescriptor descriptor) {
        this.providerCode = providerCode;
        this.descriptor = descriptor;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public ConnectorDescriptor getDescriptor() {
        return descriptor;
    }
}
