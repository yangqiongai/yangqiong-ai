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
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.docparser.DocParseResult;
import com.yangqiongai.ai.platform.connector.docparser.DocParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("文档解析管理面接口单元测试")
class DocParserControllerTest {

    /**
     * 被测控制器
     */
    private DocParserController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ConnectorProperties properties = new ConnectorProperties();
        ObjectProvider<com.yangqiongai.ai.platform.connector.docparser.OcrEngine> provider =
                mock(ObjectProvider.class);
        controller = new DocParserController(new DocParserService(properties, provider));
    }

    @Test
    @DisplayName("上传文本文件返回分块结果")
    void shouldParseUploadedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "第一行内容\n第二行内容".getBytes());

        ApiResult<DocParseResult> result = controller.parse(file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getFileType()).isEqualTo("txt");
        assertThat(result.getData().getChunkCount()).isPositive();
    }

    @Test
    @DisplayName("空文件返回失败提示")
    void shouldFailOnEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        ApiResult<DocParseResult> result = controller.parse(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("文件不能为空");
    }

    @Test
    @DisplayName("不支持类型返回失败提示")
    void shouldFailOnUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "setup.exe", "application/octet-stream", "MZ".getBytes());

        ApiResult<DocParseResult> result = controller.parse(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("不支持的文件类型");
    }
}
