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
package com.yangqiongai.ai.rag.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultRetrievalPathResolver 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class DefaultRetrievalPathResolverTest {

    private DefaultRetrievalPathResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultRetrievalPathResolver();
    }

    @Test
    @DisplayName("decide - query为null时返回SKIP")
    void decide_returnsSkip_whenQueryNull() {
        RetrievalRoute result = resolver.decide(null, List.of("kb-1"));

        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("decide - query为空字符串时返回SKIP")
    void decide_returnsSkip_whenQueryEmpty() {
        RetrievalRoute result = resolver.decide("", List.of("kb-1"));

        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("decide - query为纯空白时返回SKIP")
    void decide_returnsSkip_whenQueryBlank() {
        RetrievalRoute result = resolver.decide("   ", List.of("kb-1"));

        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("decide - kbIds为null时返回SKIP")
    void decide_returnsSkip_whenKbIdsNull() {
        RetrievalRoute result = resolver.decide("测试查询", null);

        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("decide - kbIds为空列表时返回SKIP")
    void decide_returnsSkip_whenKbIdsEmpty() {
        RetrievalRoute result = resolver.decide("测试查询", Collections.emptyList());

        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("decide - 有效输入时返回HYBRID")
    void decide_returnsHybrid_whenValidInput() {
        RetrievalRoute result = resolver.decide("测试查询", List.of("kb-1"));

        assertThat(result).isEqualTo(RetrievalRoute.HYBRID);
    }

    @Test
    @DisplayName("decide - 多个kbIds时返回HYBRID")
    void decide_returnsHybrid_whenMultipleKbIds() {
        RetrievalRoute result = resolver.decide("查询", List.of("kb-1", "kb-2"));

        assertThat(result).isEqualTo(RetrievalRoute.HYBRID);
    }
}
