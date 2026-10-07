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
package com.yangqiongai.ai.common.event;

/**
 * 作用域存储用量事件
 * @author yangqiong
 */
public class ScopeStorageUsageEvent {

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 本次存储字节数
     */
    private final long storageBytes;

    /**
     * 构造
     * @param scopeId 作用域ID
     * @param storageBytes 本次存储字节数
     */
    public ScopeStorageUsageEvent(String scopeId, long storageBytes) {
        this.scopeId = scopeId;
        this.storageBytes = storageBytes;
    }

    /**
     * 获取作用域ID
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取本次存储字节数
     * @return
     */
    public long getStorageBytes() {
        return storageBytes;
    }
}
