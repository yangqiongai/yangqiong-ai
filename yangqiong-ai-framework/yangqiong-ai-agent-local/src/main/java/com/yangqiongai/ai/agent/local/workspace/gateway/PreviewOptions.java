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
package com.yangqiongai.ai.agent.local.workspace.gateway;

/**
 * 文件读取预览选项
 * <p>
 * 字段与服务器网关当前固定执行的预览能力上限对应，未指定时由网关按自身默认上限执行。
 * </p>
 * @author yangqiong
 */
public class PreviewOptions {

    /**
     * 文本预览字节上限（超出截断）
     */
    private Long maxTextBytes;

    /**
     * 图片内联预览字节上限
     */
    private Long maxImageBytes;

    /**
     * PDF内联预览字节上限
     */
    private Long maxPdfBytes;

    /**
     * zip条目清单读回上限
     */
    private Integer maxZipEntries;

    /**
     * 默认预览选项（全部按网关默认上限执行）
     * @return
     */
    public static PreviewOptions empty() {
        return new PreviewOptions();
    }

    public Long getMaxTextBytes() {
        return maxTextBytes;
    }

    public void setMaxTextBytes(Long maxTextBytes) {
        this.maxTextBytes = maxTextBytes;
    }

    public Long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(Long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public Long getMaxPdfBytes() {
        return maxPdfBytes;
    }

    public void setMaxPdfBytes(Long maxPdfBytes) {
        this.maxPdfBytes = maxPdfBytes;
    }

    public Integer getMaxZipEntries() {
        return maxZipEntries;
    }

    public void setMaxZipEntries(Integer maxZipEntries) {
        this.maxZipEntries = maxZipEntries;
    }
}
