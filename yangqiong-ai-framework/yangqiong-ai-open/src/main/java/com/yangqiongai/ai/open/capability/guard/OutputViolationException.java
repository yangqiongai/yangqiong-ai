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
package com.yangqiongai.ai.open.capability.guard;

import java.util.List;

/**
 * 输出违反契约异常
 * @author yangqiong
 */
public class OutputViolationException extends RuntimeException {

    /**
     * 校验错误列表
     */
    private final List<String> errors;

    public OutputViolationException(List<String> errors) {
        super("输出契约校验失败: " + String.join("; ", errors));
        this.errors = errors;
    }

    public OutputViolationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public List<String> getErrors() {
        return errors;
    }
}