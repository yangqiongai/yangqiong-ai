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
package com.yangqiongai.ai.storage.qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant自动配置
 * @author yangqiong
 */
@Configuration
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantAutoConfiguration {

    /**
     * 创建QdrantClient
     * @param properties
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public QdrantClient qdrantClient(QdrantProperties properties) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                properties.getHost(),
                properties.getPort(),
                properties.isUseTls()
        );
        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
            builder.withApiKey(properties.getApiKey());
        }
        return new QdrantClient(builder.build());
    }

    /**
     * 创建QdrantVectorStorage
     * @param qdrantClient
     * @param properties
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public QdrantVectorStorage qdrantVectorStorage(QdrantClient qdrantClient, QdrantProperties properties) {
        return new QdrantVectorStorage(qdrantClient, properties);
    }
}
