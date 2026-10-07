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
package com.yangqiongai.ai.rag.chunk;

import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.rag.model.ChunkConfig;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.parser.ParsedDocument;

import java.util.List;

/**
 * 文档切片
 * @author yangqiong
 */
public interface DocumentChunker {

    /**
     * 通用切片入库
     * @param doc
     * @param ingestDoc
     * @param config
     * @return
     */
    List<SliceRecord> chunkAndSave(ParsedDocument doc, IngestDocument ingestDoc, ChunkConfig config);

    /**
     * Docling语义切片入库
     * @param doc
     * @param ingestDoc
     * @param config
     * @return
     */
    List<SliceRecord> chunkDoclingAndSave(ParsedDocument doc, IngestDocument ingestDoc, ChunkConfig config);
}
