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
package com.yangqiongai.ai.agent.runtime.tool;

/**
 * 文档解析SPI
 * <p>
 * 文件工具箱读取非纯文本文档（PDF/Office等）时的解析器，平台侧可注入Tika实现。
 * </p>
 * @author yangqiong
 */
public interface DocumentParser {

    /**
     * 解析文档内容
     * @param content 文档字节内容
     * @param filename 文件名（含扩展名，用于类型识别）
     * @return 解析后的纯文本内容
     */
    String parse(byte[] content, String filename);
}
