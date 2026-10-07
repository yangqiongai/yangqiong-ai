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

import java.util.Map;

/**
 * 文档解析分块
 * <p>
 * 管理面解析API输出的标准分块结构，调用方可据此走既有KbDocument摄取链路。
 * </p>
 * @author yangqiong
 */
public class DocParseChunk {

    /**
     * 分块序号（从0开始）
     */
    private int index;

    /**
     * 分块类型（text/table/sheet）
     */
    private String type;

    /**
     * 分块文本
     */
    private String text;

    /**
     * 位置元数据（page/sheetName/rows等）
     */
    private Map<String, Object> metadata;

    public DocParseChunk() {
    }

    public DocParseChunk(int index, String type, String text, Map<String, Object> metadata) {
        this.index = index;
        this.type = type;
        this.text = text;
        this.metadata = metadata;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
