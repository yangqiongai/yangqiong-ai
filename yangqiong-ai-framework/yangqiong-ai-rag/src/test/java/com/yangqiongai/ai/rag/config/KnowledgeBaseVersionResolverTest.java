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
package com.yangqiongai.ai.rag.config;

import com.yangqiongai.ai.common.rag.KnowledgeBaseMetadataHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeBaseVersionResolver 单元测试
 * @author yangqiong
 */
@DisplayName("KnowledgeBaseVersionResolver 单元测试")
class KnowledgeBaseVersionResolverTest {

    /**
     * 创建返回固定版本号的端口桩
     */
    private KnowledgeBaseMetadataHandler stubPort(String returnVersion) {
        return (KnowledgeBaseMetadataHandler) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{KnowledgeBaseMetadataHandler.class},
                (proxy, method, args) -> {
                    if ("resolveActiveVersion".equals(method.getName())) {
                        return returnVersion;
                    }
                    return null;
                });
    }

    private KnowledgeBaseVersionResolver newResolver(String returnVersion) {
        KnowledgeBaseVersionResolver resolver = new KnowledgeBaseVersionResolver();
        ReflectionTestUtils.setField(resolver, "knowledgeBaseMetadataHandler", stubPort(returnVersion));
        return resolver;
    }

    @Test
    @DisplayName("kbId为null时返回null")
    void resolveActiveVersion_returnsNull_whenKbIdNull() {
        KnowledgeBaseVersionResolver resolver = newResolver("v-20260101");
        String result = resolver.resolveActiveVersion(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("kbId为空白时返回null")
    void resolveActiveVersion_returnsNull_whenKbIdBlank() {
        KnowledgeBaseVersionResolver resolver = newResolver("v-20260101");
        String result = resolver.resolveActiveVersion("   ");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("端口返回有效版本号时返回该版本号")
    void resolveActiveVersion_returnsVersion_whenKbExists() {
        KnowledgeBaseVersionResolver resolver = newResolver("v-20260101");
        String result = resolver.resolveActiveVersion("kb-1");
        assertThat(result).isEqualTo("v-20260101");
    }

    @Test
    @DisplayName("端口返回null时返回null")
    void resolveActiveVersion_returnsNull_whenKbNotFound() {
        KnowledgeBaseVersionResolver resolver = newResolver(null);
        String result = resolver.resolveActiveVersion("nonexistent");
        assertThat(result).isNull();
    }
}
