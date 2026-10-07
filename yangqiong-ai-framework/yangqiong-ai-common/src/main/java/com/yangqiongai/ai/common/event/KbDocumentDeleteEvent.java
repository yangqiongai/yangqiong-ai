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
package com.yangqiongai.ai.common.event;

/**
 * 文档删除事件
 * @author yangqiong
 */
public class KbDocumentDeleteEvent {

    /**
     * 文档ID
     */
    private final String docId;

    /**
     * 知识库ID
     */
    private final String kbId;

    public KbDocumentDeleteEvent(String docId, String kbId) {
        this.docId = docId;
        this.kbId = kbId;
    }

    public String getDocId() {
        return docId;
    }

    public String getKbId() {
        return kbId;
    }
}
