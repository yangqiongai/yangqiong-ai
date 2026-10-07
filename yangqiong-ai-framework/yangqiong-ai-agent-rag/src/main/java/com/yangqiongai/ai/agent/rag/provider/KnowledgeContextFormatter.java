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
package com.yangqiongai.ai.agent.rag.provider;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识上下文格式化
 * @author yangqiong
 */
public final class KnowledgeContextFormatter {

    private KnowledgeContextFormatter() {
    }

    /**
     * 格式化知识上下文为knowledge标签块，标签圈起避免片段内容被误读为指令
     * @param results
     * @return
     */
    public static String format(List<RetrievalEvidence> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        String chunks = results.stream()
                .map(KnowledgeContextFormatter::formatChunk)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (chunks.isEmpty()) {
            return "";
        }
        return "<knowledge>\n" + chunks + "\n</knowledge>";
    }

    /**
     * 单个检索片段渲染为带来源的chunk标签
     * @param evidence
     * @return
     */
    public static String formatChunk(RetrievalEvidence evidence) {
        String content = evidence.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        String source = evidence.getSourceDocName() != null && !evidence.getSourceDocName().isBlank()
                ? evidence.getSourceDocName() : evidence.getKbName();
        String sourceAttr = source != null && !source.isBlank() ? " source=\"" + source + "\"" : "";
        return "<chunk" + sourceAttr + ">\n" + content + "\n</chunk>";
    }
}
