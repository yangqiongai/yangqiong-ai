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
package com.yangqiongai.ai.rag.route;

/**
 * 查询意图
 * @author yangqiong
 */
public enum QueryIntention {

    /**
     * 事实型查询（如：xxx是什么、xxx的定义）
     */
    FACTUAL,

    /**
     * 关系型查询（如：A和B的关系、xxx包含哪些）
     */
    RELATIONAL,

    /**
     * 分析型查询（如：为什么、如何、对比分析）
     */
    ANALYTICAL,

    /**
     * 复杂查询（多条件、多实体组合）
     */
    COMPLEX,

    /**
     * 未知类型
     */
    UNKNOWN
}
