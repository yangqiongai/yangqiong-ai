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
package com.yangqiongai.ai.common.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文档状态
 * @author yangqiong
 */
public enum DocumentStatus {

    PENDING("PENDING"),

    PARSING("PARSING"),

    CHUNKING("CHUNKING"),

    EMBEDDING("EMBEDDING"),

    READY("READY"),

    FAILED("FAILED"),

    PROCESSING("PROCESSING"),

    COMPLETED("COMPLETED");

    /**
     * 状态值
     */
    @EnumValue
    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
