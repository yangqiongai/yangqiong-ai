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
package com.yangqiongai.ai.open.capability.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 能力分类存储
 * @author yangqiong
 */
public interface CapabilityCategoryRepository {

    /**
     * 查询全部分类（平铺列表）
     * @return
     */
    List<CapabilityCategory> findAll();

    /**
     * 按编码查询分类
     * @param code
     * @return
     */
    Optional<CapabilityCategory> findByCode(String code);

    /**
     * 新增分类
     * @param category
     */
    void create(CapabilityCategory category);

    /**
     * 更新分类（名称/父节点/排序）
     * @param category
     */
    void update(CapabilityCategory category);

    /**
     * 按主键删除分类
     * @param id
     */
    void deleteById(Long id);
}
