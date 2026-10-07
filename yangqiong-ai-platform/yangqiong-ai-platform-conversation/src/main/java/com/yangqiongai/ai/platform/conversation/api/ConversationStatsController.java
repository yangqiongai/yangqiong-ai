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
package com.yangqiongai.ai.platform.conversation.api;

import com.yangqiongai.ai.platform.conversation.dto.ConversationSlaDTO;
import com.yangqiongai.ai.platform.conversation.dto.ConversationStatsDTO;
import com.yangqiongai.ai.platform.conversation.service.ConversationSlaService;
import com.yangqiongai.ai.platform.conversation.service.ConversationStatsService;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话统计与 SLA 接口
 * @author yangqiong
 */
@Tag(name = "会话统计与 SLA 接口")
@RestController
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
@RequestMapping("/api/conversation/session")
public class ConversationStatsController {

    @Autowired
    private ConversationStatsService conversationStatsService;

    @Autowired
    private ConversationSlaService conversationSlaService;

    /**
     * 获取用户会话使用统计
     * @param userId
     * @return
     */
    @Operation(summary = "获取会话使用统计", description = "聚合查询活跃会话数、总会话数、消息数、Token 用量、长期记忆数")
    @GetMapping("/stats")
    public ApiResult<ConversationStatsDTO> stats(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId) {
        return ApiResult.ok(conversationStatsService.getSessionStats(userId));
    }

    /**
     * 获取用户 SLA 统计
     * @param userId
     * @param days 时间范围天数，默认 7
     * @return
     */
    @Operation(summary = "获取 SLA 统计", description = "查询用户指定时间范围的平均响应时间、P95/P99 响应时间、平均 Token 消耗")
    @GetMapping("/sla")
    public ApiResult<ConversationSlaDTO> sla(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId,
            @Parameter(name = "days", description = "时间范围天数") @RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(conversationSlaService.getSlaStats(userId, days));
    }
}
