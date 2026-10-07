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
package com.yangqiongai.ai.rag.parser;

import java.io.InputStream;

/**
 * 文档解析器
 * @author yangqiong
 */
public interface DocumentParser {

    /**
     * 解析器名称
     * @return
     */
    String getParserName();

    /**
     * 是否支持指定文件类型
     * @param contentType
     * @param fileName
     * @return
     */
    boolean supports(String contentType, String fileName);

    /**
     * 解析文档
     * @param inputStream
     * @param fileName
     * @param fileUrl
     * @return
     */
    ParsedDocument parse(InputStream inputStream, String fileName, String fileUrl);
}
