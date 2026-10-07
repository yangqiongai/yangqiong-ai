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

import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.KbSliceQuerier;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档切片管理
 * @author yangqiong
 */
@Tag(name = "文档切片管理接口")
@RestController
@RequestMapping("/api/knowledge-base/{kbId}/documents/{docId}/slices")
public class KbSliceController {

    @Autowired
    private KbSliceQuerier kbSliceQuerier;

    /**
     * 查询文档切片列表
     * @param kbId
     * @param docId
     * @return
     */
    @Operation(summary = "查询文档切片列表")
    @GetMapping
    public ApiResult<List<SliceRecord>> listSlices(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "docId", description = "文档ID") @PathVariable String docId) {
        return ApiResult.ok(kbSliceQuerier.listSlices(kbId, docId));
    }

    /**
     * 查询切片详情
     * @param kbId
     * @param docId
     * @param sliceId
     * @return
     */
    @Operation(summary = "查询切片详情")
    @GetMapping("/{sliceId}")
    public ApiResult<SliceRecord> getSlice(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "docId", description = "文档ID") @PathVariable String docId,
            @Parameter(name = "sliceId", description = "切片ID") @PathVariable String sliceId) {
        return ApiResult.ok(kbSliceQuerier.getSlice(kbId, docId, sliceId));
    }
}
