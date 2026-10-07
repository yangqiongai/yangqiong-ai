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
package com.yangqiongai.ai.common.datasource;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 动态多数据源注册SPI，启动时收集实现并注册到路由数据源
 * @author yangqiong
 */
@FunctionalInterface
public interface RegisterDatasourceSupplier extends Supplier<Map<String, Map<String, Object>>> {

    @Override
    Map<String, Map<String, Object>> get();
}
