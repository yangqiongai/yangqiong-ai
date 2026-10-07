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
package com.yangqiongai.ai.common.scope;

/**
 * 功能特性
 * @author yangqiong
 */
public interface FeatureGuard {

    /**
     * 校验当前作用域是否具备指定功能
     * @param feature
     * @return
     */
    void checkFeature(String feature);

    /**
     * 校验当前作用域是否允许使用指定模型
     * @param modelName
     * @return
     */
    void checkModelAllowed(String modelName);

    /**
     * 校验当前作用域是否允许使用指定技能
     * @param skillCode
     * @return
     */
    void checkSkillAllowed(String skillCode);

    /**
     * 校验当前作用域是否允许使用指定工具
     * @param toolCode
     * @return
     */
    void checkToolAllowed(String toolCode);
}
