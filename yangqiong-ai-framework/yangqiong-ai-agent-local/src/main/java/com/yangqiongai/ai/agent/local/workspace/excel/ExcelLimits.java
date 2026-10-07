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
package com.yangqiongai.ai.agent.local.workspace.excel;

/**
 * Excel生成资源上限
 * @author yangqiong
 */
public class ExcelLimits {

    /**
     * 单文件单元格总数上限
     */
    private final int maxCells;

    /**
     * 单文件大小上限（字节）
     */
    private final long maxFileSize;

    /**
     * sheet数量上限
     */
    private final int maxSheets;

    private ExcelLimits(int maxCells, long maxFileSize, int maxSheets) {
        this.maxCells = maxCells;
        this.maxFileSize = maxFileSize;
        this.maxSheets = maxSheets;
    }

    /**
     * 构建上限对象（非法或未配置时使用默认值）
     * @param maxCells
     * @param maxFileSize
     * @param maxSheets
     * @return
     */
    public static ExcelLimits of(Integer maxCells, Long maxFileSize, Integer maxSheets) {
        return new ExcelLimits(
                maxCells == null || maxCells <= 0 ? 100000 : maxCells,
                maxFileSize == null || maxFileSize <= 0 ? 5L * 1024 * 1024 : maxFileSize,
                maxSheets == null || maxSheets <= 0 ? 20 : maxSheets);
    }

    public int getMaxCells() {
        return maxCells;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public int getMaxSheets() {
        return maxSheets;
    }
}
