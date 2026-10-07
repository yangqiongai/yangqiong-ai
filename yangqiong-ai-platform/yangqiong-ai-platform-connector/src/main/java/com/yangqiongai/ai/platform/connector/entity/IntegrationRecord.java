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
package com.yangqiongai.ai.platform.connector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * 发送记录
 * @author yangqiong
 */
@TableName("ai_integration_record")
public class IntegrationRecord extends ScopeEntity {

    /**
     * 发送状态：发送中
     */
    public static final String STATUS_SENDING = "SENDING";

    /**
     * 发送状态：成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 发送状态：失败
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 发送状态：跳过
     */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 渠道
     */
    private String channel;

    /**
     * 发送状态
     */
    private String status;

    /**
     * 响应内容
     */
    private String response;

    /**
     * 外发载荷(JSON,用于手工重推)
     */
    private String payload;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    public String getId() {
        return id == null ? null : String.valueOf(id);
    }

    public void setId(String id) {
        this.id = id == null ? null : Long.valueOf(id);
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getSendTime() {
        return sendTime;
    }

    public void setSendTime(LocalDateTime sendTime) {
        this.sendTime = sendTime;
    }
}
