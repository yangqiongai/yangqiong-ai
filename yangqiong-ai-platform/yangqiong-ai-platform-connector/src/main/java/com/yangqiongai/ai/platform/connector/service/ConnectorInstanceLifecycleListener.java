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

import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;

/**
 * 连接器实例生命周期监听器
 * <p>
 * 实例保存/启停/删除时回调，提供商侧资源（如数据库连接池、入站网关缓存）
 * 据此即时失效，避免依赖缓存TTL自然过期。
 * </p>
 * @author yangqiong
 */
public interface ConnectorInstanceLifecycleListener {

    /**
     * 实例保存或状态变更
     * @param instance
     */
    void onChanged(ConnectorInstance instance);

    /**
     * 实例删除
     * @param instanceCode 实例编码
     */
    void onDeleted(String instanceCode);
}
