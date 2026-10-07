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
package com.yangqiongai.ai.agent.core.provider;

import java.util.List;

/**
 * 图谱检索提供者SPI（框架定义回调契约，由图谱模块装配实现，未装配时图谱检索配置自动跳过）
 * @author yangqiong
 */
public interface GraphRetrievalProvider {

    /**
     * 执行图谱检索并返回格式化上下文文本
     * @param query 检索语句
     * @param kbIds 知识库ID列表
     * @param mode 检索模式（LOCAL/GLOBAL/HYBRID/MULTI_HOP/IRCOT，空或AUTO=自动路由）
     * @param topK 各类命中结果条数上限
     * @return
     */
    String retrieve(String query, List<String> kbIds, String mode, int topK);
}
