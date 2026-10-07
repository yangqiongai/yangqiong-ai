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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.platform.conversation.dto.ConversationRequestDTO;
import com.yangqiongai.ai.platform.conversation.dto.ConversationSearchDTO;
import com.yangqiongai.ai.platform.conversation.governance.SessionQuotaService;
import com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.memory.DialogMemoryAdapter;
import com.yangqiongai.ai.memory.SessionManager;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话会话管理
 * @author yangqiong
 */
@Tag(name = "对话会话管理接口")
@RestController
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
@RequestMapping("/api/conversation/session")
public class ConversationController {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private DialogMemoryAdapter dialogMemoryAdapter;

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

    @Autowired
    private SessionQuotaService sessionQuotaService;

    /**
     * 创建会话
     * @param session
     * @return
     */
    @Operation(summary = "创建会话")
    @PostMapping
    public ApiResult<ConversationSessionInfo> create(
            @Parameter(name = "session", description = "会话信息") @RequestBody ConversationSessionInfo session) {
        sessionQuotaService.checkQuota(session.getUserId());
        return ApiResult.ok(sessionManager.create(session));
    }

    /**
     * 根据会话ID查询
     * @param sessionId
     * @return
     */
    @Operation(summary = "根据会话ID查询")
    @GetMapping("/{sessionId}")
    public ApiResult<ConversationSessionInfo> getBySessionId(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        return sessionManager.findBySessionId(sessionId)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.CONVERSATION_NOT_FOUND.getCode(), "会话不存在: " + sessionId));
    }

    /**
     * 根据用户ID查询会话列表
     * @param userId
     * @return
     */
    @Operation(summary = "根据用户ID查询会话列表")
    @GetMapping("/user/{userId}")
    public ApiResult<List<ConversationSessionInfo>> listByUserId(
            @Parameter(name = "userId", description = "用户ID") @PathVariable String userId) {
        return ApiResult.ok(sessionManager.findByUserId(userId));
    }

    /**
     * 更新会话
     * @param session
     */
    @Operation(summary = "更新会话")
    @PutMapping
    public ApiResult<Void> update(
            @Parameter(name = "session", description = "会话信息") @RequestBody ConversationSessionInfo session) {
        sessionManager.update(session);
        return ApiResult.ok();
    }

    /**
     * 删除会话
     * @param sessionId
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> delete(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        sessionManager.deleteBySessionId(sessionId);
        return ApiResult.ok();
    }

    /**
     * 关闭会话，将会话摘要合并到用户长期记忆后将会话状态设为已结束
     * @param sessionId
     * @return
     */
    @Operation(summary = "关闭会话", description = "将会话摘要合并到长期记忆并将会话状态设为已结束(0)")
    @PutMapping("/{sessionId}/close")
    public ApiResult<Void> close(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        sessionLifecycleService.closeAndMerge(sessionId);
        return ApiResult.ok();
    }

    /**
     * 多条件分页搜索会话
     * @param searchDTO
     * @return
     */
    @Operation(summary = "分页搜索会话", description = "按关键词、状态、Agent、时间范围分页搜索会话")
    @PostMapping("/search")
    public ApiResult<List<ConversationSessionInfo>> search(
            @Parameter(name = "searchDTO", description = "搜索条件") @RequestBody ConversationSearchDTO searchDTO) {
        List<ConversationSessionInfo> page = sessionManager.search(
                searchDTO.getUserId(),
                searchDTO.getKeyword(),
                searchDTO.getStatus(),
                searchDTO.getAgentCode(),
                searchDTO.getStartTime(),
                searchDTO.getEndTime(),
                searchDTO.getPage(),
                searchDTO.getSize());
        return ApiResult.ok(page);
    }

    /**
     * 导出会话
     * @param sessionId
     * @param format 格式：json 或 markdown
     * @return
     */
    @Operation(summary = "导出会话", description = "导出会话元信息、消息列表和摘要，支持 json 与 markdown 格式")
    @GetMapping("/{sessionId}/export")
    public ApiResult<String> export(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId,
            @Parameter(name = "format", description = "导出格式：json|markdown") @RequestParam(defaultValue = "json") String format) {
        return ApiResult.ok(sessionLifecycleService.exportSession(sessionId, format));
    }

    /**
     * 准备对话上下文，支持通过DTO控制是否启用跨会话记忆
     * @param request
     * @return
     */
    @Operation(summary = "准备对话上下文", description = "确保会话存在并恢复状态，可选启用跨会话长期记忆")
    @PostMapping("/context")
    public ApiResult<ConversationSessionInfo> prepareContext(
            @Parameter(name = "request", description = "对话请求参数") @RequestBody ConversationRequestDTO request) {
        ConversationSessionInfo session = dialogMemoryAdapter.prepareContext(
                request.getSessionId(),
                request.getUserId(),
                request.isEnableCrossSessionMemory());
        return ApiResult.ok(session);
    }
}
