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
package com.yangqiongai.ai.llm.repository;

import com.yangqiongai.ai.llm.model.ModelInfo;

import java.util.List;
import java.util.Optional;

/**
 * 模型信息
 * @author yangqiong
 */
public interface ModelInfoRepository {

    /**
     * 根据模型编码查找
     * @param modelCode
     * @return
     */
    Optional<ModelInfo> findByModelCode(String modelCode);

    /**
     * 查询所有模型
     * @return
     */
    List<ModelInfo> findAll();
}
