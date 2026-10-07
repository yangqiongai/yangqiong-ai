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

import java.util.Collections;
import java.util.List;

/**
 * 校验结果
 * @author yangqiong
 */
public class ValidationResult {

    /**
     * 是否通过校验
     */
    private final boolean valid;

    /**
     * 校验错误信息列表
     */
    private final List<String> errors;

    /**
     * 原始数据（可能已被修复）
     */
    private final Object data;

    public ValidationResult(boolean valid, List<String> errors, Object data) {
        this.valid = valid;
        this.errors = errors != null ? errors : Collections.emptyList();
        this.data = data;
    }

    /**
     * 创建通过结果
     * @param data
     * @return
     */
    public static ValidationResult pass(Object data) {
        return new ValidationResult(true, Collections.emptyList(), data);
    }

    /**
     * 创建失败结果
     * @param errors
     * @param data
     * @return
     */
    public static ValidationResult fail(List<String> errors, Object data) {
        return new ValidationResult(false, errors, data);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Object getData() {
        return data;
    }
}