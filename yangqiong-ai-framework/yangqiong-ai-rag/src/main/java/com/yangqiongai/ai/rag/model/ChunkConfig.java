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
package com.yangqiongai.ai.rag.model;

/**
 * 切片配置
 * @author yangqiong
 */
public class ChunkConfig {

    /**
     * 最大切片大小
     */
    private int maxChunkSize = 500;

    /**
     * 重叠大小
     */
    private int overlapSize = 50;

    /**
     * 分隔符
     */
    private String separator = "\n\n";

    /**
     * 子块大小
     */
    private int childChunkSize = 100;

    /**
     * 子块重叠
     */
    private int childOverlap = 10;

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        if (maxChunkSize < 1) {
            throw new IllegalArgumentException("maxChunkSize必须大于0, 实际: " + maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
    }

    public int getOverlapSize() {
        return overlapSize;
    }

    public void setOverlapSize(int overlapSize) {
        if (overlapSize < 0) {
            throw new IllegalArgumentException("overlapSize不能为负数, 实际: " + overlapSize);
        }
        if (overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException("overlapSize必须小于maxChunkSize, overlapSize: "
                    + overlapSize + ", maxChunkSize: " + maxChunkSize);
        }
        this.overlapSize = overlapSize;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        if (separator == null || separator.isEmpty()) {
            throw new IllegalArgumentException("separator不能为空");
        }
        this.separator = separator;
    }

    public int getChildChunkSize() {
        return childChunkSize;
    }

    public void setChildChunkSize(int childChunkSize) {
        if (childChunkSize < 1) {
            throw new IllegalArgumentException("childChunkSize必须大于0, 实际: " + childChunkSize);
        }
        if (childChunkSize > maxChunkSize) {
            throw new IllegalArgumentException("childChunkSize不能大于maxChunkSize, childChunkSize: "
                    + childChunkSize + ", maxChunkSize: " + maxChunkSize);
        }
        this.childChunkSize = childChunkSize;
    }

    public int getChildOverlap() {
        return childOverlap;
    }

    public void setChildOverlap(int childOverlap) {
        if (childOverlap < 0) {
            throw new IllegalArgumentException("childOverlap不能为负数, 实际: " + childOverlap);
        }
        if (childOverlap >= childChunkSize) {
            throw new IllegalArgumentException("childOverlap必须小于childChunkSize, childOverlap: "
                    + childOverlap + ", childChunkSize: " + childChunkSize);
        }
        this.childOverlap = childOverlap;
    }

    /**
     * 校验配置参数合法性
     */
    public void validate() {
        setMaxChunkSize(maxChunkSize);
        setSeparator(separator);
        setOverlapSize(overlapSize);
        setChildChunkSize(childChunkSize);
        setChildOverlap(childOverlap);
    }
}
