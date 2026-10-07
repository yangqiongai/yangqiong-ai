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
package com.yangqiongai.ai.storage;

import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import com.yangqiongai.ai.storage.local.LocalDocumentStorage;
import com.yangqiongai.ai.storage.minio.MinioDocumentStorage;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储类型条件装配测试
 * @author yangqiong
 */
@DisplayName("ai.storage.type 条件装配测试")
class StorageTypeConditionalTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class))
            .withBean(ObjectKeyResolver.class, DefaultObjectKeyResolver::new);

    @Test
    @DisplayName("默认（未配置 ai.storage.type）装配 MinIO")
    void defaultsToMinio_whenTypeMissing() {
        runner.withPropertyValues(
                "ai.storage.minio.access-key=test",
                "ai.storage.minio.secret-key=test"
        ).run(context -> {
            assertThat(context).hasSingleBean(MinioDocumentStorage.class);
            assertThat(context).hasSingleBean(MinioClient.class);
            assertThat(context).doesNotHaveBean(LocalDocumentStorage.class);
        });
    }

    @Test
    @DisplayName("ai.storage.type=minio 装配 MinIO")
    void usesMinio_whenTypeMinio() {
        runner.withPropertyValues(
                "ai.storage.type=minio",
                "ai.storage.minio.access-key=test",
                "ai.storage.minio.secret-key=test"
        ).run(context -> {
            assertThat(context).hasSingleBean(MinioDocumentStorage.class);
            assertThat(context).doesNotHaveBean(LocalDocumentStorage.class);
        });
    }

    @Test
    @DisplayName("ai.storage.type=local 装配本地存储，不装配 MinIO")
    void usesLocal_whenTypeLocal() {
        runner.withPropertyValues("ai.storage.type=local").run(context -> {
            assertThat(context).hasSingleBean(LocalDocumentStorage.class);
            assertThat(context).doesNotHaveBean(MinioDocumentStorage.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
        });
    }
}
