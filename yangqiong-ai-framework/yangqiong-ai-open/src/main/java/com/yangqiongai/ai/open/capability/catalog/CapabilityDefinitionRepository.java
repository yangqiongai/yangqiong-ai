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
import java.util.Map;
import java.util.Optional;

/**
 * 能力定义存储
 * <p>
 * 从数据库 ai_open_capability 表读取能力定义，支持三种模式：
 * 新增独立能力、覆盖YAML定义、禁用YAML定义。
 * </p>
 * @author yangqiong
 */
public interface CapabilityDefinitionRepository {

    /**
     * 查询所有数据库能力定义
     * @return
     */
    List<CapabilityDefinition> findAll();

    /**
     * 按编码查询数据库能力定义
     * @param code
     * @return
     */
    Optional<CapabilityDefinition> findByCode(String code);

    /**
     * 新增独立能力
     * @param entity
     */
    void create(CapabilityDefinition entity);

    /**
     * 覆盖YAML定义（仅覆盖部分字段）
     * @param code 能力编码
     * @param overrideFields 覆盖字段Map
     */
    void override(String code, Map<String, Object> overrideFields);

    /**
     * 禁用能力
     * @param code
     */
    void disable(String code);

    /**
     * 启用能力（解除禁用）
     * @param code
     */
    void enable(String code);

    /**
     * 删除数据库能力定义
     * @param code
     */
    void delete(String code);
}