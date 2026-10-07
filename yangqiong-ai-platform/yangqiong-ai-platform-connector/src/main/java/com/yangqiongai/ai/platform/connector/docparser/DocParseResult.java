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
package com.yangqiongai.ai.platform.connector.docparser;

import java.util.List;

/**
 * 文档解析结果
 * @author yangqiong
 */
public class DocParseResult {

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型（pdf/docx/xlsx等扩展名）
     */
    private String fileType;

    /**
     * 分块清单
     */
    private List<DocParseChunk> chunks;

    /**
     * 分块数量
     */
    private int chunkCount;

    /**
     * 总字符数
     */
    private int totalChars;

    /**
     * OCR兜底处理的页数
     */
    private int ocrPageCount;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public List<DocParseChunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<DocParseChunk> chunks) {
        this.chunks = chunks;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getTotalChars() {
        return totalChars;
    }

    public void setTotalChars(int totalChars) {
        this.totalChars = totalChars;
    }

    public int getOcrPageCount() {
        return ocrPageCount;
    }

    public void setOcrPageCount(int ocrPageCount) {
        this.ocrPageCount = ocrPageCount;
    }
}
