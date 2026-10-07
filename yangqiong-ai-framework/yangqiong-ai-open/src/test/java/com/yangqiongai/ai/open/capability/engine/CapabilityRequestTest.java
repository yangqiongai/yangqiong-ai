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
package com.yangqiongai.ai.open.capability.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力调用请求
 * @author yangqiong
 */
class CapabilityRequestTest {

    @Test
    void testGettersAndSetters() {
        CapabilityRequest request = new CapabilityRequest();

        request.setCapability("project-overview");
        request.setDataContextRefs(List.of("ref-1", "ref-2"));
        request.setArguments(Map.of("key1", "value1", "key2", 123));
        request.setDedupKey("dedup-key-001");
        request.setCaller("user-001");
        request.setScopeId("scope-001");

        assertThat(request.getCapability()).isEqualTo("project-overview");
        assertThat(request.getDataContextRefs()).containsExactly("ref-1", "ref-2");
        assertThat(request.getArguments()).hasSize(2)
                .containsEntry("key1", "value1")
                .containsEntry("key2", 123);
        assertThat(request.getDedupKey()).isEqualTo("dedup-key-001");
        assertThat(request.getCaller()).isEqualTo("user-001");
        assertThat(request.getScopeId()).isEqualTo("scope-001");
    }

    @Test
    void testDefaultValues() {
        CapabilityRequest request = new CapabilityRequest();

        assertThat(request.getCapability()).isNull();
        assertThat(request.getDataContextRefs()).isNull();
        assertThat(request.getArguments()).isNull();
        assertThat(request.getDedupKey()).isNull();
        assertThat(request.getCaller()).isNull();
        assertThat(request.getScopeId()).isNull();
    }

    @Test
    void testSetNullValues() {
        CapabilityRequest request = new CapabilityRequest();
        request.setCapability("test");
        request.setDataContextRefs(List.of("ref-1"));
        request.setArguments(Map.of("k", "v"));
        request.setDedupKey("key");
        request.setCaller("user");
        request.setScopeId("scope");

        request.setCapability(null);
        request.setDataContextRefs(null);
        request.setArguments(null);
        request.setDedupKey(null);
        request.setCaller(null);
        request.setScopeId(null);

        assertThat(request.getCapability()).isNull();
        assertThat(request.getDataContextRefs()).isNull();
        assertThat(request.getArguments()).isNull();
        assertThat(request.getDedupKey()).isNull();
        assertThat(request.getCaller()).isNull();
        assertThat(request.getScopeId()).isNull();
    }
}