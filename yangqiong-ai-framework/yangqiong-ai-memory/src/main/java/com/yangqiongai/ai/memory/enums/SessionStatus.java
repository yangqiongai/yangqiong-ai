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
package com.yangqiongai.ai.memory.enums;

/**
 * 会话状态
 * @author yangqiong
 */
public enum SessionStatus {

    CLOSED(0, "已关闭"),
    ACTIVE(1, "活跃"),
    ARCHIVED(2, "已归档");

    private final int code;

    private final String description;

    SessionStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     * @param code
     * @return
     */
    public static SessionStatus fromCode(int code) {
        for (SessionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return ACTIVE;
    }
}
