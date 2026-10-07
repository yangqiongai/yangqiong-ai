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

import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.model.RecallRequest;
import com.yangqiongai.ai.rag.model.RecallResult;
import com.yangqiongai.ai.rag.RagRetrieveService;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG检索与验证
 * @author yangqiong
 */
@Tag(name = "RAG检索与验证接口")
@RestController
@RequestMapping("/api/rag")
public class RagRetrieveController {

    private static final Logger log = LoggerFactory.getLogger(RagRetrieveController.class);

    @Autowired
    private RagRetrieveService ragRetrieveService;

    /**
     * 混合检索
     * @param query
     * @param kbIds
     * @param topK
     * @return
     */
    @Operation(summary = "混合检索")
    @GetMapping("/retrieve")
    public ApiResult<List<RetrievalEvidence>> retrieve(
            @Parameter(name = "query", description = "查询文本") @RequestParam String query,
            @Parameter(name = "kbIds", description = "知识库ID列表") @RequestParam List<String> kbIds,
            @Parameter(name = "topK", description = "返回条数") @RequestParam(defaultValue = "5") int topK) {
        return ApiResult.ok(ragRetrieveService.retrieveHybrid(query, kbIds, null, topK));
    }

    /**
     * 召回验证
     * @param request
     * @return
     */
    @Operation(summary = "召回验证")
    @PostMapping("/recall/verify")
    public ApiResult<List<RecallResult>> verifyRecall(
            @Parameter(name = "request", description = "召回验证请求") @RequestBody RecallRequest request) {
        return ApiResult.ok(ragRetrieveService.verifyRecall(request));
    }

    /**
     * 单文档上下文扩展
     * @param docId
     * @param query
     * @param maxChars
     * @return
     */
    @Operation(summary = "单文档上下文扩展")
    @GetMapping("/context/expand")
    public ApiResult<String> expandContext(
            @Parameter(name = "docId", description = "文档ID") @RequestParam String docId,
            @Parameter(name = "query", description = "查询文本") @RequestParam String query,
            @Parameter(name = "maxChars", description = "最大字符数") @RequestParam(defaultValue = "5000") int maxChars) {
        return ApiResult.ok(ragRetrieveService.expandContext(docId, query, maxChars));
    }
}
