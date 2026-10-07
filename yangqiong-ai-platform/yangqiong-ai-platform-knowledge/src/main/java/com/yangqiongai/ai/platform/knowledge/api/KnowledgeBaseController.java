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
package com.yangqiongai.ai.platform.knowledge.api;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.scope.PlanLimitGuard;
import com.yangqiongai.ai.common.util.ImageUtils;
import com.yangqiongai.ai.platform.knowledge.service.KnowledgeBaseService;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理
 * @author yangqiong
 */
@Tag(name = "知识库管理接口")
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 套餐数量限制守卫
     */
    @Autowired
    private PlanLimitGuard planLimitGuard;

    /**
     * 创建知识库
     * @param kb
     * @return
     */
    @Operation(summary = "创建知识库")
    @PostMapping
    public ApiResult<KnowledgeBase> create(
            @Parameter(name = "kb", description = "知识库信息") @RequestBody KnowledgeBase kb) {
        // 套餐知识库数量上限校验
        planLimitGuard.checkKnowledgeBaseLimit(knowledgeBaseService.findAll().size());
        // 上传的base64图标统一压缩到100px内，控制存储体积
        kb.setKbIcon(ImageUtils.compressIconDataUrl(kb.getKbIcon(), 100));
        return ApiResult.ok(knowledgeBaseService.create(kb));
    }

    /**
     * 查询所有知识库
     * @return
     */
    @Operation(summary = "查询所有知识库")
    @GetMapping
    public ApiResult<List<KnowledgeBase>> list(
            @Parameter(name = "userId", description = "用户ID（可选，租户隔离由租户上下文自动完成）") @RequestParam(required = false) String userId) {
        if (userId != null && !userId.isBlank()) {
            return ApiResult.ok(knowledgeBaseService.findByUserId(userId));
        }
        return ApiResult.ok(knowledgeBaseService.findAll());
    }

    /**
     * 根据kbId查询知识库
     * @param kbId
     * @return
     */
    @Operation(summary = "根据kbId查询知识库")
    @GetMapping("/{kbId}")
    public ApiResult<KnowledgeBase> getByKbId(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId) {
        return knowledgeBaseService.findByKbId(kbId)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.RAG_KB_NOT_FOUND.getCode(), "知识库不存在: " + kbId));
    }

    /**
     * 更新知识库
     * @param kbId
     * @param kb
     * @return
     */
    @Operation(summary = "更新知识库")
    @PutMapping("/{kbId}")
    public ApiResult<KnowledgeBase> update(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "kb", description = "知识库信息") @RequestBody KnowledgeBase kb) {
        // 上传的base64图标统一压缩到100px内，控制存储体积
        kb.setKbIcon(ImageUtils.compressIconDataUrl(kb.getKbIcon(), 100));
        kb.setKbId(kbId);
        return ApiResult.ok(knowledgeBaseService.update(kb));
    }

    /**
     * 删除知识库
     * @param kbId
     */
    @Operation(summary = "删除知识库")
    @DeleteMapping("/{kbId}")
    public ApiResult<Void> delete(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId) {
        knowledgeBaseService.deleteByKbId(kbId);
        return ApiResult.ok();
    }

    /**
     * 物理删除知识库及关联数据
     * @param kbId
     * @param confirm
     */
    @Operation(summary = "物理删除知识库及关联数据")
    @DeleteMapping("/{kbId}/purge")
    public ApiResult<Void> purge(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "confirm", description = "确认标识, 必须传true") @RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) {
            return ApiResult.fail("请传入confirm=true确认物理删除操作，此操作不可恢复");
        }
        knowledgeBaseService.purgeKnowledgeBase(kbId);
        return ApiResult.ok();
    }
}
