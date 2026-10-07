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
package com.yangqiongai.ai.rag.retriever;

import com.yangqiongai.ai.rag.model.ChunkCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索结果格式化器
 * @author yangqiong
 */
@Service
public class RetrievalFormatter {

    private static final Logger log = LoggerFactory.getLogger(RetrievalFormatter.class);

    private static final String SECTION_SEPARATOR = "---";

    /**
     * 格式化检索结果为文本
     * @param candidates
     * @return
     */
    public String format(List<ChunkCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            ChunkCandidate candidate = candidates.get(i);
            if (i > 0) {
                sb.append("\n").append(SECTION_SEPARATOR).append("\n");
            }
            sb.append(candidate.getText());
        }

        return sb.toString();
    }

    /**
     * 带引用的格式化
     * @param candidates
     * @return
     */
    public String formatWithCitations(List<ChunkCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            ChunkCandidate candidate = candidates.get(i);
            if (i > 0) {
                sb.append("\n").append(SECTION_SEPARATOR).append("\n");
            }
            sb.append("[来源: 文档ID=").append(candidate.getDocId() != null ? candidate.getDocId() : "未知");
            if (candidate.getSliceId() != null) {
                sb.append(", 切片ID=").append(candidate.getSliceId());
            }
            sb.append(", 相似度=").append(String.format("%.4f", candidate.getScore()));
            sb.append("]\n");
            sb.append(candidate.getText());
        }

        return sb.toString();
    }
}
