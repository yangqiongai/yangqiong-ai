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
package com.yangqiongai.ai.platform.knowledge.service;

import com.yangqiongai.ai.open.capability.ingest.CapabilityDocumentIngestPort;
import com.yangqiongai.ai.platform.knowledge.entity.KbDocument;
import org.springframework.stereotype.Component;

/**
 * 能力文件入库适配
 * <p>
 * 桥接开放能力层的文件入库端口与知识库文档摄取服务。
 * </p>
 * @author yangqiong
 */
@Component
public class CapabilityDocumentIngestAdapter implements CapabilityDocumentIngestPort {

    /**
     * 文档入库服务
     */
    private final DocumentIngestService documentIngestService;

    public CapabilityDocumentIngestAdapter(DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
    }

    /**
     * 文件入库
     * @param kbId 知识库ID
     * @param fileName 文件名
     * @param content 文件内容
     * @param contentType 文件类型
     * @return 入库结果
     */
    @Override
    public IngestResult ingest(String kbId, String fileName, byte[] content, String contentType) {
        KbDocument doc = documentIngestService.ingestDocument(kbId, fileName, content, contentType);
        IngestResult result = new IngestResult();
        result.setDocId(doc.getDocId());
        result.setKbId(doc.getKbId());
        result.setDocStatus(doc.getDocStatus() != null ? doc.getDocStatus().name() : null);
        return result;
    }
}
