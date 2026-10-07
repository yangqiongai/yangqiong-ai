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
package com.yangqiongai.ai.trust.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行实例标识解析测试
 * @author yangqiong
 */
class RunnerIdParserTest {

    @Test
    void isLegacyFormatShouldMatchHostnamePortUuid() {
        assertThat(RunnerIdParser.isLegacyFormat("host-1:8082:abcd1234")).isTrue();
    }

    @Test
    void isLegacyFormatShouldRejectNewFormat() {
        assertThat(RunnerIdParser.isLegacyFormat("aid-0a1b2c3d:instance-1")).isFalse();
    }

    @Test
    void isLegacyFormatShouldRejectBlankAndMalformed() {
        assertThat(RunnerIdParser.isLegacyFormat(null)).isFalse();
        assertThat(RunnerIdParser.isLegacyFormat("")).isFalse();
        assertThat(RunnerIdParser.isLegacyFormat("only-one")).isFalse();
        assertThat(RunnerIdParser.isLegacyFormat("host:port:uuid:extra")).isFalse();
        assertThat(RunnerIdParser.isLegacyFormat("host:notport:uuid")).isFalse();
    }

    @Test
    void resolveIdentityUidShouldParseNewFormat() {
        assertThat(RunnerIdParser.resolveIdentityUid("aid-0a1b2c3d:instance-1")).isEqualTo("aid-0a1b2c3d");
    }

    @Test
    void resolveIdentityUidShouldReturnNullForLegacyAndBlank() {
        assertThat(RunnerIdParser.resolveIdentityUid("host-1:8082:abcd1234")).isNull();
        assertThat(RunnerIdParser.resolveIdentityUid(null)).isNull();
        assertThat(RunnerIdParser.resolveIdentityUid("  ")).isNull();
    }

    @Test
    void resolveInstanceShouldReturnRawLegacyId() {
        assertThat(RunnerIdParser.resolveInstance("host-1:8082:abcd1234")).isEqualTo("host-1:8082:abcd1234");
    }

    @Test
    void resolveInstanceShouldParseNewFormat() {
        assertThat(RunnerIdParser.resolveInstance("aid-0a1b2c3d:instance-1")).isEqualTo("instance-1");
    }

    @Test
    void resolveInstanceShouldReturnNullForBlank() {
        assertThat(RunnerIdParser.resolveInstance(null)).isNull();
    }
}
