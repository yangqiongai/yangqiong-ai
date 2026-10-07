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
package com.yangqiongai.ai.common.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一响应体
 * @author yangqiong
 */
public class ApiResult<T> {

    private boolean success;

    /**
     * HTTP状态码
     */
    private int status;

    /**
     * 业务错误码
     */
    private int code = 0;

    private String message;

    private T data;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private MetaData metaData;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public MetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(MetaData metaData) {
        this.metaData = metaData;
    }

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> ok(T data) {
        return ok("", data);
    }

    public static <T> ApiResult<T> ok(String msg, T data) {
        return buildSuccessResult(data, msg);
    }

    public static <T> ApiResult<T> okPage(List<?> data, long total) {
        if (data == null) {
            data = new ArrayList<>();
        }
        ApiResult<T> apiResult = buildSuccessResult((T) data, null);
        apiResult.setMetaData(new MetaData(0, 0, total));
        return apiResult;
    }

    public static <T> ApiResult<T> okPage(List<?> data, long total, long pageNum, long pageSize) {
        if (data == null) {
            data = new ArrayList<>();
        }
        ApiResult<T> apiResult = buildSuccessResult((T) data, null);
        apiResult.setMetaData(new MetaData(pageNum, pageSize, total));
        return apiResult;
    }

    public static <T> ApiResult<T> fail(String msg) {
        return fail(msg, -1, -1);
    }

    public static <T> ApiResult<T> fail(int code, String errorMsg) {
        return fail(errorMsg, code, -1);
    }

    public static <T> ApiResult<T> fail(String msg, int status) {
        return fail(msg, -1, status);
    }

    public static <T> ApiResult<T> fail(String msg, int code, int status) {
        ApiResult<T> response = new ApiResult<>();
        response.setSuccess(false);
        response.setMessage(msg);
        response.setCode(code);
        response.setStatus(status);
        return response;
    }

    private static <T> ApiResult<T> buildSuccessResult(T data, String msg) {
        ApiResult<T> response = new ApiResult<>();
        response.setData(data);
        response.setSuccess(true);
        response.setMessage(msg == null ? "成功" : msg);
        response.setStatus(200);
        return response;
    }

    /**
     * 分页元数据
     */
    public static class MetaData {

        private long pageNum;

        private long pageSize;

        private long total;

        private Map<String, Object> body;

        public MetaData() {
        }

        public MetaData(long pageNum, long pageSize, long total) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            this.total = total;
        }

        public long getPageNum() {
            return pageNum;
        }

        public void setPageNum(long pageNum) {
            this.pageNum = pageNum;
        }

        public long getPageSize() {
            return pageSize;
        }

        public void setPageSize(long pageSize) {
            this.pageSize = pageSize;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public Map<String, Object> getBody() {
            return body;
        }

        public void setBody(Map<String, Object> body) {
            this.body = body;
        }
    }
}
