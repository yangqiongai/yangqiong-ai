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
package com.yangqiongai.ai.agent.runtime.web;

/**
 * 网页抓取提供方
 * <p>
 * web_fetch工具的后端抓取SPI。
 * </p>
 * @author yangqiong
 */
public interface WebFetcher {

    /**
     * 抓取网页内容
     * @param url 目标地址
     * @param maxChars 返回内容最大字符数
     * @return 网页文本内容
     */
    String fetch(String url, int maxChars);
}
