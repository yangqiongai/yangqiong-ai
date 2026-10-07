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
package com.yangqiongai.ai.agent.data.eval.loader;

import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;

/**
 * 前缀路由评测数据集加载
 * <p>
 * db:前缀走数据库加载，其余(classpath:/文件路径)回退文件加载。
 * </p>
 * @author yangqiong
 */
public class PrefixDatasetLoader implements DatasetLoader {

    /**
     * 数据库数据集前缀
     */
    public static final String DB_PREFIX = "db:";

    private final DbDatasetLoader dbDatasetLoader;

    private final DatasetLoader fileDatasetLoader;

    /**
     * 按前缀路由到数据库或文件数据集加载器
     * @param dbDatasetLoader 数据库加载器
     * @param fileDatasetLoader 文件加载器
     */
    public PrefixDatasetLoader(DbDatasetLoader dbDatasetLoader, DatasetLoader fileDatasetLoader) {
        this.dbDatasetLoader = dbDatasetLoader;
        this.fileDatasetLoader = fileDatasetLoader;
    }

    /**
     * 按前缀加载数据集(db:数据集编码/classpath:路径/文件路径)
     * @param location
     * @return
     */
    @Override
    public GoldenDataset load(String location) {
        if (location != null && location.startsWith(DB_PREFIX)) {
            return dbDatasetLoader.load(location.substring(DB_PREFIX.length()));
        }
        return fileDatasetLoader.load(location);
    }

    /**
     * 从JSON字符串加载数据集
     * @param json
     * @return
     */
    @Override
    public GoldenDataset loadFromJson(String json) {
        return fileDatasetLoader.loadFromJson(json);
    }
}
