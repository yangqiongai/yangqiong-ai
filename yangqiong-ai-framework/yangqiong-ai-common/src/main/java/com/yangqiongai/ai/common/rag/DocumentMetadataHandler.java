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
package com.yangqiongai.ai.common.rag;

import java.util.Map;
import java.util.Set;

/**
 * 文档元数据端口
 * @author yangqiong
 */
public interface DocumentMetadataHandler {

    /**
     * 加载文档
     * @param docId
     * @return
     */
    IngestDocument loadDocument(String docId);

    /**
     * 批量加载文档
     * @param docIds
     * @return
     */
    Map<String, IngestDocument> batchLoadDocuments(Set<String> docIds);

    /**
     * 创建文档记录
     * @param document
     * @return
     */
    IngestDocument createDocument(IngestDocument document);

    /**
     * 更新文档状态
     * @param docId
     * @param docStatus
     * @param chunkCount
     * @param errorMessage
     */
    void updateDocumentStatus(String docId, DocumentStatus docStatus, Integer chunkCount, String errorMessage);

    /**
     * 更新文档摘要
     * @param docId
     * @param summary
     */
    void updateDocumentSummary(String docId, String summary);
}
