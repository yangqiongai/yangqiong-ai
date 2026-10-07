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
package com.yangqiongai.ai.platform.system.spi;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 菜单特性开关环境配置判定
 * <p>
 * 按键读取 Spring Environment 配置，仅当显式配置为 false 时关闭（缺失视为开启），
 * 与 yml 静态开关语义一致。
 * </p>
 * @author yangqiong
 */
public class EnvironmentMenuFeatureGate implements MenuFeatureGate {

    /**
     * 环境配置
     */
    private final Environment environment;

    public EnvironmentMenuFeatureGate(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean enabled(String featureKey) {
        if (!StringUtils.hasText(featureKey)) {
            return true;
        }
        String value = environment.getProperty(featureKey.trim());
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }
}
