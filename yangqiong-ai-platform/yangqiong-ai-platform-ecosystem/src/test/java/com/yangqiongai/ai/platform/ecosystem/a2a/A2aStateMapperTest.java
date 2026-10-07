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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import com.yangqiongai.ai.platform.ecosystem.a2a.A2aStateMapper.A2aTaskState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2A任务状态映射测试
 * @author yangqiong
 */
class A2aStateMapperTest {

    @Test
    void mapShouldCoverAllPlatformStates() {
        assertThat(A2aStateMapper.map("QUEUED")).isEqualTo(A2aTaskState.SUBMITTED);
        assertThat(A2aStateMapper.map("RUNNING")).isEqualTo(A2aTaskState.WORKING);
        assertThat(A2aStateMapper.map("PAUSED")).isEqualTo(A2aTaskState.INPUT_REQUIRED);
        assertThat(A2aStateMapper.map("SUCCEEDED")).isEqualTo(A2aTaskState.COMPLETED);
        assertThat(A2aStateMapper.map("FAILED")).isEqualTo(A2aTaskState.FAILED);
        assertThat(A2aStateMapper.map("CANCELED")).isEqualTo(A2aTaskState.CANCELED);
    }

    @Test
    void mapShouldFallbackToWorkingForUnknown() {
        assertThat(A2aStateMapper.map(null)).isEqualTo(A2aTaskState.WORKING);
        assertThat(A2aStateMapper.map("UNKNOWN")).isEqualTo(A2aTaskState.WORKING);
    }
}
