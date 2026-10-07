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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.platform.knowledge.entity.DataSourceIngestLog;
import com.yangqiongai.ai.platform.knowledge.mapper.DataSourceIngestLogMapper;
import com.yangqiongai.ai.platform.knowledge.service.DataSourceIngestService;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 多数据源接入
 * @author yangqiong
 */
@Tag(name = "多数据源接入接口")
@RestController
@RequestMapping("/api/datasource")
public class DataSourceIngestController {

    @Autowired
    private DataSourceIngestService dataSourceIngestService;

    @Autowired
    private DataSourceIngestLogMapper dataSourceIngestLogMapper;

    /**
     * 分页查询数据源接入记录
     * @param pageNum
     * @param pageSize
     * @param kbId
     * @param ingestType
     * @return
     */
    @Operation(summary = "分页查询数据源接入记录")
    @GetMapping("/ingest/logs")
    public ApiResult<IngestLogPage> listIngestLogs(
            @Parameter(name = "pageNum", description = "页码, 从1开始") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(name = "kbId", description = "知识库ID, 可选") @RequestParam(required = false) String kbId,
            @Parameter(name = "ingestType", description = "接入方式(TEXT/WEBPAGE/API/DATABASE), 可选") @RequestParam(required = false) String ingestType) {
        LambdaQueryWrapper<DataSourceIngestLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(kbId != null && !kbId.isBlank(), DataSourceIngestLog::getKbId, kbId);
        wrapper.eq(ingestType != null && !ingestType.isBlank(), DataSourceIngestLog::getIngestType, ingestType);
        long total = dataSourceIngestLogMapper.selectCount(wrapper);
        int offset = Math.max(pageNum - 1, 0) * Math.max(pageSize, 1);
        wrapper.orderByDesc(DataSourceIngestLog::getIngestTime)
                .last("LIMIT " + offset + "," + Math.max(pageSize, 1));
        List<DataSourceIngestLog> list = dataSourceIngestLogMapper.selectList(wrapper);
        IngestLogPage page = new IngestLogPage();
        page.setList(list);
        page.setTotal(total);
        return ApiResult.ok(page);
    }

    /**
     * 从直接文本导入
     * @param request
     * @return
     */
    @Operation(summary = "从直接文本导入")
    @PostMapping("/text")
    public ApiResult<IngestDocument> ingestFromText(@RequestBody TextIngestRequest request) {
        IngestDocument doc = dataSourceIngestService.ingestFromText(
                request.getKbId(), request.getTitle(), request.getContent());
        return ApiResult.ok(doc);
    }

    /**
     * 从网页内容导入
     * @param request
     * @return
     */
    @Operation(summary = "从网页内容导入")
    @PostMapping("/webpage")
    public ApiResult<IngestDocument> ingestFromWebpage(@RequestBody WebpageIngestRequest request) {
        IngestDocument doc = dataSourceIngestService.ingestFromWebpage(
                request.getKbId(), request.getUrl(), request.getTitle(), request.getContent());
        return ApiResult.ok(doc);
    }

    /**
     * 从接口数据导入
     * @param request
     * @return
     */
    @Operation(summary = "从接口数据导入")
    @PostMapping("/controller")
    public ApiResult<IngestDocument> ingestFromApi(@RequestBody ApiIngestRequest request) {
        IngestDocument doc = dataSourceIngestService.ingestFromApi(
                request.getKbId(), request.getTitle(), request.getContent(), request.getSourceUrl());
        return ApiResult.ok(doc);
    }

    /**
     * 从数据库查询结果导入
     * @param request
     * @return
     */
    @Operation(summary = "从数据库查询结果导入")
    @PostMapping("/database")
    public ApiResult<IngestDocument> ingestFromDatabase(@RequestBody DatabaseIngestRequest request) {
        IngestDocument doc = dataSourceIngestService.ingestFromDatabase(
                request.getKbId(), request.getTitle(), request.getContent());
        return ApiResult.ok(doc);
    }

    /**
     * 文本导入请求
     */
    public static class TextIngestRequest {

        @Parameter(name = "kbId", description = "知识库ID")
        private String kbId;

        @Parameter(name = "title", description = "文档标题")
        private String title;

        @Parameter(name = "content", description = "文本内容")
        private String content;

        public String getKbId() {
            return kbId;
        }

        public void setKbId(String kbId) {
            this.kbId = kbId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 网页导入请求
     */
    public static class WebpageIngestRequest {

        @Parameter(name = "kbId", description = "知识库ID")
        private String kbId;

        @Parameter(name = "url", description = "网页URL")
        private String url;

        @Parameter(name = "title", description = "文档标题")
        private String title;

        @Parameter(name = "content", description = "网页文本内容")
        private String content;

        public String getKbId() {
            return kbId;
        }

        public void setKbId(String kbId) {
            this.kbId = kbId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 接口导入请求
     */
    public static class ApiIngestRequest {

        @Parameter(name = "kbId", description = "知识库ID")
        private String kbId;

        @Parameter(name = "title", description = "文档标题")
        private String title;

        @Parameter(name = "content", description = "接口返回的文本内容")
        private String content;

        @Parameter(name = "sourceUrl", description = "接口URL")
        private String sourceUrl;

        public String getKbId() {
            return kbId;
        }

        public void setKbId(String kbId) {
            this.kbId = kbId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }
    }

    /**
     * 数据库导入请求
     */
    public static class DatabaseIngestRequest {

        @Parameter(name = "kbId", description = "知识库ID")
        private String kbId;

        @Parameter(name = "title", description = "文档标题")
        private String title;

        @Parameter(name = "content", description = "数据库查询结果的文本内容")
        private String content;

        public String getKbId() {
            return kbId;
        }

        public void setKbId(String kbId) {
            this.kbId = kbId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 接入记录分页结果
     */
    public static class IngestLogPage {

        @Parameter(name = "list", description = "记录列表")
        private List<DataSourceIngestLog> list;

        @Parameter(name = "total", description = "总条数")
        private long total;

        public List<DataSourceIngestLog> getList() {
            return list;
        }

        public void setList(List<DataSourceIngestLog> list) {
            this.list = list;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }
}
