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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("默认解析器 单元测试")
class DefaultResolverTest {

    @Nested
    @DisplayName("DefaultCollectionNameResolver 测试")
    class CollectionNameResolverTest {

        @Test
        @DisplayName("resolve 直接返回原始 kbId")
        void shouldReturnOriginalKbId() {
            DefaultCollectionNameResolver resolver = new DefaultCollectionNameResolver();

            assertThat(resolver.resolve("kb-001")).isEqualTo("kb-001");
        }

        @Test
        @DisplayName("resolve null 时返回 null")
        void shouldReturnNullWhenInputIsNull() {
            DefaultCollectionNameResolver resolver = new DefaultCollectionNameResolver();

            assertThat(resolver.resolve(null)).isNull();
        }

        @Test
        @DisplayName("resolve 空字符串时返回空字符串")
        void shouldReturnEmptyWhenInputIsEmpty() {
            DefaultCollectionNameResolver resolver = new DefaultCollectionNameResolver();

            assertThat(resolver.resolve("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("DefaultObjectKeyResolver 测试")
    class ObjectKeyResolverTest {

        @Test
        @DisplayName("resolve 直接返回原始 objectKey")
        void shouldReturnOriginalObjectKey() {
            DefaultObjectKeyResolver resolver = new DefaultObjectKeyResolver();

            assertThat(resolver.resolve("obj/001/file.txt")).isEqualTo("obj/001/file.txt");
        }

        @Test
        @DisplayName("resolve null 时返回 null")
        void shouldReturnNullWhenInputIsNull() {
            DefaultObjectKeyResolver resolver = new DefaultObjectKeyResolver();

            assertThat(resolver.resolve(null)).isNull();
        }
    }

    @Nested
    @DisplayName("DefaultFeatureGuard 测试")
    class FeatureGuardTest {

        @Test
        @DisplayName("checkFeature 不抛出异常")
        void shouldNotThrowWhenCheckFeature() {
            DefaultFeatureGuard checker = new DefaultFeatureGuard();

            assertThatCode(() -> checker.checkFeature("any-feature")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkModelAllowed 不抛出异常")
        void shouldNotThrowWhenCheckModelAllowed() {
            DefaultFeatureGuard checker = new DefaultFeatureGuard();

            assertThatCode(() -> checker.checkModelAllowed("gpt-4")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkSkillAllowed 不抛出异常")
        void shouldNotThrowWhenCheckSkillAllowed() {
            DefaultFeatureGuard checker = new DefaultFeatureGuard();

            assertThatCode(() -> checker.checkSkillAllowed("skill-001")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkToolAllowed 不抛出异常")
        void shouldNotThrowWhenCheckToolAllowed() {
            DefaultFeatureGuard checker = new DefaultFeatureGuard();

            assertThatCode(() -> checker.checkToolAllowed("tool-001")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("传入 null 参数不抛出异常")
        void shouldNotThrowWhenParamIsNull() {
            DefaultFeatureGuard checker = new DefaultFeatureGuard();

            assertThatCode(() -> checker.checkFeature(null)).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkModelAllowed(null)).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkSkillAllowed(null)).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkToolAllowed(null)).doesNotThrowAnyException();
        }
    }

}
