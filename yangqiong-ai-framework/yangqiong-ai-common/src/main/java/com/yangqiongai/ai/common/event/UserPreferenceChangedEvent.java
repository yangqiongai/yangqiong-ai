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
 * 用户偏好变化事件
 * @author yangqiong
 */
public class UserPreferenceChangedEvent {

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 偏好键
     */
    private final String preferenceKey;

    /**
     * 偏好值
     */
    private final String preferenceValue;

    /**
     * 变化类型（ADD/UPDATE/DELETE）
     */
    private final String changeType;

    public UserPreferenceChangedEvent(String userId, String preferenceKey, String preferenceValue, String changeType) {
        this.userId = userId;
        this.preferenceKey = preferenceKey;
        this.preferenceValue = preferenceValue;
        this.changeType = changeType;
    }

    public String getUserId() {
        return userId;
    }

    public String getPreferenceKey() {
        return preferenceKey;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public String getChangeType() {
        return changeType;
    }
}
