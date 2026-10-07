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
package com.yangqiongai.ai.evaluation.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 评测数据集加载
 * @author yangqiong
 */
public class DatasetLoaderImpl implements DatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从文件路径或classpath加载评测数据集
     * @param location
     * @return
     */
    @Override
    public GoldenDataset load(String location) {
        try {
            String json;
            if (location.startsWith("classpath:")) {
                String resourcePath = location.substring("classpath:".length());
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (inputStream == null) {
                    throw new RuntimeException("Classpath resource not found: " + resourcePath);
                }
                json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                Path path = Paths.get(location);
                json = Files.readString(path, StandardCharsets.UTF_8);
            }
            return objectMapper.readValue(json, GoldenDataset.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dataset from: " + location, e);
        }
    }

    /**
     * 从JSON字符串加载评测数据集
     * @param json
     * @return
     */
    @Override
    public GoldenDataset loadFromJson(String json) {
        try {
            return objectMapper.readValue(json, GoldenDataset.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse dataset from JSON", e);
        }
    }
}
