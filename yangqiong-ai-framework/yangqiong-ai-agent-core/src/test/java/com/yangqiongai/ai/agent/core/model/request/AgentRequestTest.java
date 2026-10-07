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
package com.yangqiongai.ai.agent.core.model.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent请求测试
 * @author yangqiong
 */
class AgentRequestTest {

    @Test
    void seedMessages缺省时返回空列表() {
        AgentRequest request = new AgentRequest();

        assertThat(request.getSeedMessages()).isEmpty();
    }

    @Test
    void seedMessages格式不符时返回空列表() {
        AgentRequest request = new AgentRequest();
        request.addBody(AgentRequest.BodyKeys.SEED_MESSAGES, "不是列表");

        assertThat(request.getSeedMessages()).isEmpty();
    }

    @Test
    void seedMessages元素非映射时被过滤() {
        AgentRequest request = new AgentRequest();
        request.addBody(AgentRequest.BodyKeys.SEED_MESSAGES,
                List.of("非法元素", Map.of("role", "user", "content", "你好")));

        assertThat(request.getSeedMessages()).hasSize(1);
        assertThat(request.getSeedMessages().get(0))
                .containsEntry("role", "user")
                .containsEntry("content", "你好");
    }

    @Test
    void seedMessages正常读取() {
        AgentRequest request = new AgentRequest();
        request.addBody(AgentRequest.BodyKeys.SEED_MESSAGES, List.of(
                Map.of("role", "user", "content", "问"),
                Map.of("role", "assistant", "content", "答")));

        assertThat(request.getSeedMessages()).hasSize(2);
        assertThat(request.getSeedMessages().get(0)).containsEntry("role", "user");
        assertThat(request.getSeedMessages().get(1)).containsEntry("role", "assistant");
    }
}
