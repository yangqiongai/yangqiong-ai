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
 * 默认功能特性
 * @author yangqiong
 */
public class DefaultFeatureGuard implements FeatureGuard {

    /**
     * 默认全部放行
     * @param feature
     * @return
     */
    @Override
    public void checkFeature(String feature) {
    }

    /**
     * 默认全部放行
     * @param modelName
     * @return
     */
    @Override
    public void checkModelAllowed(String modelName) {
    }

    /**
     * 默认全部放行
     * @param skillCode
     * @return
     */
    @Override
    public void checkSkillAllowed(String skillCode) {
    }

    /**
     * 默认全部放行
     * @param toolCode
     * @return
     */
    @Override
    public void checkToolAllowed(String toolCode) {
    }
}
