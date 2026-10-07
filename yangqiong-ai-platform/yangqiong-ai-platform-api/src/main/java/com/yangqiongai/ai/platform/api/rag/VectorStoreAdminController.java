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
package com.yangqiongai.ai.platform.api.rag;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.storage.qdrant.QdrantAdminManager;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 向量存储管理
 * @author yangqiong
 */
@Tag(name = "向量存储管理接口")
@RestController
@RequestMapping("/api/admin/vector")
public class VectorStoreAdminController {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreAdminController.class);

    @Autowired
    private QdrantAdminManager qdrantAdminService;

    @Value("${ai.rag.admin-token:}")
    private String adminToken;

    /**
     * 查询集合列表
     * @return
     */
    @Operation(summary = "查询集合列表")
    @GetMapping("/collections")
    public ApiResult<List<Map<String, Object>>> listCollections() {
        return ApiResult.ok(qdrantAdminService.listCollections());
    }

    /**
     * 查询集合详情
     * @param collectionName
     * @return
     */
    @Operation(summary = "查询集合详情")
    @GetMapping("/collections/{collectionName}")
    public ApiResult<Map<String, Object>> getCollectionDetail(
            @Parameter(name = "collectionName", description = "集合名称") @PathVariable String collectionName) {
        return ApiResult.ok(qdrantAdminService.getCollectionDetail(collectionName));
    }

    /**
     * 删除集合
     * @param collectionName
     * @param confirm
     * @param token
     */
    @Operation(summary = "删除集合")
    @DeleteMapping("/collections/{collectionName}")
    public ApiResult<Void> deleteCollection(
            @Parameter(name = "collectionName", description = "集合名称") @PathVariable String collectionName,
            @Parameter(name = "confirm", description = "确认标识, 必须传true") @RequestParam(defaultValue = "false") boolean confirm,
            @Parameter(name = "token", description = "管理令牌") @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!confirm) {
            return ApiResult.fail("请传入confirm=true确认删除操作");
        }
        verifyAdminToken(token);
        qdrantAdminService.deleteCollection(collectionName);
        log.warn("集合已删除: {}", collectionName);
        return ApiResult.ok();
    }

    /**
     * 校验管理令牌
     * @param token
     */
    private void verifyAdminToken(String token) {
        if (adminToken == null || adminToken.isBlank()) {
            log.warn("管理令牌未配置, 拒绝访问");
            throw new AiException(AiErrorCode.RAG_ADMIN_TOKEN_INVALID);
        }
        if (token == null || !MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("管理令牌校验失败");
            throw new AiException(AiErrorCode.RAG_ADMIN_TOKEN_INVALID);
        }
    }
}
