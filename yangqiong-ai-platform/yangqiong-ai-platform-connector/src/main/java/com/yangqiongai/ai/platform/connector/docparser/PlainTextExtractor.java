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

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 纯文本提取
 * <p>
 * 支持txt/md/csv/json等UTF-8文本文件，整体作为单一文本分段。
 * </p>
 * @author yangqiong
 */
public class PlainTextExtractor {

    /**
     * 提取文本分段
     * @param content 文件字节
     * @return 单一分段清单，空文件返回空清单
     */
    public List<DocSegment> extract(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new DocSegment("text", text.trim(), null));
    }
}
