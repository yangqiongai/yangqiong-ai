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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档分段
 * <p>
 * 提取器产出的原始分段，类型：text（普通文本）/table（表格线性化）/sheet（Excel工作表），
 * 携带页码、sheet名等位置元数据，清洗与分块前使用。
 * </p>
 * @author yangqiong
 */
public class DocSegment {

    /**
     * 分段类型（text/table/sheet）
     */
    private final String type;

    /**
     * 分段文本
     */
    private final String text;

    /**
     * 位置元数据（page/sheetName/rows等）
     */
    private final Map<String, Object> metadata;

    public DocSegment(String type, String text, Map<String, Object> metadata) {
        this.type = type;
        this.text = text;
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
