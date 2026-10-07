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
package com.yangqiongai.ai.workflow.spi;

/**
 * 通知发送结果
 * @author yangqiong
 */
public class NotifySendResult {

    /**
     * 是否发送成功
     */
    private boolean success;

    /**
     * 渠道返回的消息ID
     */
    private String messageId;

    /**
     * 失败原因摘要
     */
    private String error;

    public static NotifySendResult ok(String messageId) {
        NotifySendResult result = new NotifySendResult();
        result.success = true;
        result.messageId = messageId;
        return result;
    }

    public static NotifySendResult fail(String error) {
        NotifySendResult result = new NotifySendResult();
        result.success = false;
        result.error = error;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
