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

/**
 * 扫描件OCR引擎SPI
 * <p>
 * PDF文本层为空时对页面渲染图执行文字识别；平台默认不提供实现，
 * 接入Tess4J等引擎时实现本接口并注册为Spring Bean即可生效。
 * </p>
 * @author yangqiong
 */
public interface OcrEngine {

    /**
     * 识别图片中的文字
     * @param imageBytes PNG格式图片字节
     * @return 识别出的文本
     */
    String recognize(byte[] imageBytes);
}
