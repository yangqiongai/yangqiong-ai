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
package com.yangqiongai.ai.open.capability.ingest;

/**
 * 能力文件入库端口
 * <p>
 * 解耦开放能力层与知识库实现，由platform-knowledge模块提供适配实现。
 * </p>
 * @author yangqiong
 */
public interface CapabilityDocumentIngestPort {

    /**
     * 文件入库（解析切片入向量库）
     * @param kbId 知识库ID
     * @param fileName 文件名
     * @param content 文件内容
     * @param contentType 文件类型
     * @return 入库结果
     */
    IngestResult ingest(String kbId, String fileName, byte[] content, String contentType);

    /**
     * 文件入库结果
     * @author yangqiong
     */
    class IngestResult {

        /**
         * 文档ID
         */
        private String docId;

        /**
         * 知识库ID
         */
        private String kbId;

        /**
         * 文档状态（索引时序参考）
         */
        private String docStatus;

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getKbId() {
            return kbId;
        }

        public void setKbId(String kbId) {
            this.kbId = kbId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }
    }
}
