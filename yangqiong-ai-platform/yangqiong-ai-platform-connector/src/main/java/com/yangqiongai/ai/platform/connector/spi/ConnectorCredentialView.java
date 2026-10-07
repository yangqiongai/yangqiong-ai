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
package com.yangqiongai.ai.platform.connector.spi;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;

import java.util.Map;

/**
 * 连接器凭证视图
 * <p>
 * 凭证解密后的一次性只读视图，按提供商声明字段校验必填项，
 * 生命周期仅限于本次工具/网关构造，不得缓存或落日志。
 * </p>
 * @author yangqiong
 */
public class ConnectorCredentialView {

    /**
     * 凭证数据（字段名 -> 明文值）
     */
    private final Map<String, String> values;

    public ConnectorCredentialView(Map<String, String> values) {
        this.values = values == null ? Map.of() : values;
    }

    /**
     * 按提供商声明字段校验必填项，缺失时抛出参数异常
     * @param fields 提供商声明的凭证字段
     */
    public void validate(java.util.List<ConnectorField> fields) {
        if (fields == null) {
            return;
        }
        for (ConnectorField field : fields) {
            if (field.isRequired() && !values.containsKey(field.getName())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "连接器凭证缺少必填字段: " + field.getName());
            }
        }
    }

    /**
     * 读取字段值
     * @param name 字段名
     * @return 值，不存在返回null
     */
    public String get(String name) {
        return values.get(name);
    }

    /**
     * 读取带默认值的字段
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 值，不存在返回默认值
     */
    public String getOrDefault(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    /**
     * 获取全部字段（只读视图）
     * @return
     */
    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
