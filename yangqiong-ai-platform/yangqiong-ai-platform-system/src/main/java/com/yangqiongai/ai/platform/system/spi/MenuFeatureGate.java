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

/**
 * 菜单特性开关
 * <p>
 * 菜单拼装管线中 feature_key 过滤步骤的扩展点：社区默认按 Spring Environment 配置判定，
 * 企业版覆盖为 License 功能集判定（未授权菜单服务端直接不下发）。
 * </p>
 * @author yangqiong
 */
public interface MenuFeatureGate {

    /**
     * 特性键对应的菜单是否开放
     * @param featureKey 菜单绑定的特性键（ai_system_menu.feature_key）
     * @return
     */
    boolean enabled(String featureKey);
}
