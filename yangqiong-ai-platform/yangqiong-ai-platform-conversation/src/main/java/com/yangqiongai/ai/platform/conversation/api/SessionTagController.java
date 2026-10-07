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

import com.yangqiongai.ai.platform.conversation.entity.SessionTag;
import com.yangqiongai.ai.platform.conversation.service.SessionTagService;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话标签管理接口
 * @author yangqiong
 */
@Tag(name = "会话标签管理接口")
@RestController
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
@RequestMapping("/api/conversation/session/tag")
public class SessionTagController {

    @Autowired
    private SessionTagService sessionTagService;

    /**
     * 为会话添加标签
     * @param sessionId
     * @param userId
     * @param tag
     * @return
     */
    @Operation(summary = "添加标签", description = "为指定会话添加标签，幂等操作")
    @PostMapping("/{sessionId}")
    public ApiResult<SessionTag> addTag(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId,
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId,
            @Parameter(name = "tag", description = "标签名称") @RequestParam String tag) {
        return ApiResult.ok(sessionTagService.addTag(sessionId, userId, tag));
    }

    /**
     * 移除会话标签
     * @param sessionId
     * @param tag
     * @return
     */
    @Operation(summary = "移除标签", description = "移除指定会话的指定标签")
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> removeTag(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId,
            @Parameter(name = "tag", description = "标签名称") @RequestParam String tag) {
        sessionTagService.removeTag(sessionId, tag);
        return ApiResult.ok();
    }

    /**
     * 查询会话的所有标签
     * @param sessionId
     * @return
     */
    @Operation(summary = "查询会话标签", description = "查询指定会话的所有标签")
    @GetMapping("/{sessionId}")
    public ApiResult<List<SessionTag>> listBySessionId(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        return ApiResult.ok(sessionTagService.listBySessionId(sessionId));
    }

    /**
     * 查询用户的所有标签（去重）
     * @param userId
     * @return
     */
    @Operation(summary = "查询用户标签列表", description = "查询用户的所有标签（去重）")
    @GetMapping
    public ApiResult<List<String>> listTagsByUserId(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId) {
        return ApiResult.ok(sessionTagService.listTagsByUserId(userId));
    }

    /**
     * 按标签查询会话ID列表
     * @param userId
     * @param tag
     * @return
     */
    @Operation(summary = "按标签查询会话", description = "查询用户指定标签下的所有会话ID")
    @GetMapping("/sessions")
    public ApiResult<List<String>> listSessionIdsByTag(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId,
            @Parameter(name = "tag", description = "标签名称") @RequestParam String tag) {
        return ApiResult.ok(sessionTagService.listSessionIdsByTag(userId, tag));
    }

    /**
     * 删除用户指定标签
     * @param userId
     * @param tag
     * @return
     */
    @Operation(summary = "删除用户标签", description = "删除用户指定标签下的所有会话标签记录")
    @DeleteMapping
    public ApiResult<Void> removeUserTag(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId,
            @Parameter(name = "tag", description = "标签名称") @RequestParam String tag) {
        sessionTagService.removeUserTag(userId, tag);
        return ApiResult.ok();
    }
}
