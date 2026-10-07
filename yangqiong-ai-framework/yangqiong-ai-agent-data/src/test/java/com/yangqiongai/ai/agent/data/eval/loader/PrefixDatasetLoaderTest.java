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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 前缀路由评测数据集加载测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class PrefixDatasetLoaderTest {

    @Mock
    private DbDatasetLoader dbDatasetLoader;

    @Mock
    private DatasetLoader fileDatasetLoader;

    private PrefixDatasetLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PrefixDatasetLoader(dbDatasetLoader, fileDatasetLoader);
    }

    private GoldenDataset golden() {
        return new GoldenDataset("ds1", "n", "d", java.util.List.of());
    }

    @Test
    void 数据库前缀路由到数据库加载器() {
        when(dbDatasetLoader.load("recruit")).thenReturn(golden());

        GoldenDataset result = loader.load("db:recruit");

        assertThat(result.getDatasetId()).isEqualTo("ds1");
        verify(dbDatasetLoader).load("recruit");
    }

    @Test
    void classpath前缀路由到文件加载器() {
        when(fileDatasetLoader.load("classpath:datasets/a.json")).thenReturn(golden());

        GoldenDataset result = loader.load("classpath:datasets/a.json");

        assertThat(result).isNotNull();
        verify(fileDatasetLoader).load("classpath:datasets/a.json");
    }

    @Test
    void 纯文件路径路由到文件加载器() {
        when(fileDatasetLoader.load("/data/a.json")).thenReturn(golden());

        loader.load("/data/a.json");

        verify(fileDatasetLoader).load("/data/a.json");
    }

    @Test
    void JSON字符串加载委托文件加载器() {
        when(fileDatasetLoader.loadFromJson("{}")).thenReturn(golden());

        GoldenDataset result = loader.loadFromJson("{}");

        assertThat(result.getDatasetId()).isEqualTo("ds1");
    }
}
