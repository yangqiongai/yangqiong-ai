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
package com.yangqiongai.ai.platform.connector.web;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.docparser.DocParseResult;
import com.yangqiongai.ai.platform.connector.docparser.DocParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档解析连接器管理面
 * <p>
 * 文件上传→结构化分块预览，调用方据分块结果走既有KbDocument摄取链路。
 * </p>
 * @author yangqiong
 */
@RestController
@RequestMapping("/api/agent/connector/docparser")
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class DocParserController {

    private static final Logger log = LoggerFactory.getLogger(DocParserController.class);

    /**
     * 文档解析服务
     */
    private final DocParserService docParserService;

    public DocParserController(DocParserService docParserService) {
        this.docParserService = docParserService;
    }

    /**
     * 解析文件为结构化分块
     * @param file 上传文件
     * @return 解析结果
     */
    @PostMapping("/parse")
    public ApiResult<DocParseResult> parse(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("文件不能为空");
        }
        try {
            return ApiResult.ok(docParserService.parse(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        } catch (AiException e) {
            return ApiResult.fail(e.getMessage());
        } catch (IOException e) {
            log.error("文件读取失败: {}", file.getOriginalFilename(), e);
            return ApiResult.fail("文件读取失败: " + e.getMessage());
        }
    }
}
