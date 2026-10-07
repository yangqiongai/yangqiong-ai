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
package com.yangqiongai.ai.agent.core.provider;

/**
 * 用户显式声明的偏好（指令式或请求参数式）
 * @author yangqiong
 */
public class DeclaredPreference {

    /**
     * 偏好键（如"编程语言"、"回复风格"），可为空（按向量去重）
     */
    private String key;

    /**
     * 偏好内容（如"Python"、"简洁"）
     */
    private String content;

    /**
     * 来源渠道（COMMAND/REQUEST_BODY），用于日志追踪
     */
    private String channel;

    public DeclaredPreference() {
    }

    public DeclaredPreference(String key, String content, String channel) {
        this.key = key;
        this.content = content;
        this.channel = channel;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
