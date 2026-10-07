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
package com.yangqiongai.ai.open.capability.context;

import java.util.List;
import java.util.Optional;

/**
 * 数据上下文存储接口
 * @author yangqiong
 */
public interface DataContextRepository {

    /**
     * 存储数据上下文
     * @param context
     * @return 引用编码
     */
    String save(DataContext context);

    /**
     * 按引用获取数据上下文
     * @param ref
     * @return
     */
    Optional<DataContext> get(String ref);

    /**
     * 按引用列表获取数据上下文
     * @param refs
     * @return
     */
    List<DataContext> getBatch(List<String> refs);

    /**
     * 删除过期数据
     */
    void purgeExpired();
}