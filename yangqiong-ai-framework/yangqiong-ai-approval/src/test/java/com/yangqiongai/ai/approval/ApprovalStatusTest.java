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
package com.yangqiongai.ai.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApprovalStatus 单元测试")
class ApprovalStatusTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValuesTest {

        @Test
        @DisplayName("枚举恰好有5个值")
        void shouldHaveExactlyFourValues() {
            assertThat(ApprovalStatus.values()).hasSize(5);
        }

        @Test
        @DisplayName("valueOf对每个名称正常工作")
        void shouldValueOfEachName() {
            assertThat(ApprovalStatus.valueOf("APPROVED")).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(ApprovalStatus.valueOf("PENDING")).isEqualTo(ApprovalStatus.PENDING);
            assertThat(ApprovalStatus.valueOf("REJECTED")).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(ApprovalStatus.valueOf("TIMEOUT")).isEqualTo(ApprovalStatus.TIMEOUT);
            assertThat(ApprovalStatus.valueOf("CANCELLED")).isEqualTo(ApprovalStatus.CANCELLED);
        }
    }
}
