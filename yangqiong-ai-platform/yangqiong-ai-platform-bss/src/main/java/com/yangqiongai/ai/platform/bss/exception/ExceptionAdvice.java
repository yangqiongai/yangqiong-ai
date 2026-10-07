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
package com.yangqiongai.ai.platform.bss.exception;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一异常处理
 * @author yangqiong
 */
@ControllerAdvice
@ResponseBody
public class ExceptionAdvice {

    protected static final Logger logger = LoggerFactory.getLogger(ExceptionAdvice.class);

    @Autowired
    HttpServletResponse httpServletResponse;

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.error("缺少请求参数", e);
        return createResponseEntity("缺少请求参数:" + e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ApiResult<?> handlePathVariableException(MissingPathVariableException e) {
        logger.error("缺少路径参数", e);
        return createResponseEntity("缺少路径参数:" + e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        logger.error("消息转换错误", e);
        return createResponseEntity("消息转换错误", HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<?> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.error("参数验证错误", e);
        return createResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        logger.error("参数校验异常", e);
        BindingResult result = e.getBindingResult();
        FieldError error = result.getFieldError();
        String field = error.getField();
        String code = error.getDefaultMessage();
        String message = String.format("%s:%s", field, code);
        return createResponseEntity(message, HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(BindException.class)
    public ApiResult<?> handleBindException(BindException e) {
        logger.error("参数绑定异常", e);
        BindingResult result = e.getBindingResult();
        FieldError error = result.getFieldError();
        String field = error.getField();
        String code = error.getDefaultMessage();
        String message = String.format("%s:%s", field, code);
        return createResponseEntity(message, HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(ValidationException.class)
    public ApiResult<?> handleValidationException(ValidationException e) {
        logger.error("校验异常", e);
        return createResponseEntity("校验异常", HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(TokenException.class)
    public ApiResult<?> handleTokenException(TokenException e) {
        logger.error("令牌异常", e);
        return createResponseEntity("登录已过期，请重新登录", HttpStatus.UNAUTHORIZED.value());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ApiResult<?> handleForbiddenException(ForbiddenException e) {
        logger.error("数据无权访问", e);
        // 透传完整原因（含权限码，权限码自带冒号不可截断），便于前端与使用者定位
        return createResponseEntity(e.getMessage(), HttpStatus.FORBIDDEN.value(), false);
    }

    @ExceptionHandler(NotFoundException.class)
    public ApiResult<?> handleNotFoundException(NotFoundException e) {
        logger.error("请求资源不存在", e);
        return createResponseEntity("请求资源不存在", HttpStatus.NOT_FOUND.value());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logger.error("不支持当前请求方法", e);
        return createResponseEntity("不支持当前请求方法", HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<?> handleMaxUploadSizeException(MaxUploadSizeExceededException e) {
        logger.error("上传文件过大", e);
        return createResponseEntity("上传文件过大", HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResult<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        logger.error("不支持当前媒体类型", e);
        return createResponseEntity("不支持当前媒体类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    }

    @ExceptionHandler(LockedException.class)
    public ApiResult<?> handleLockedException(LockedException e) {
        logger.error("当前资源被锁定", e);
        return createResponseEntity("当前资源被锁定", HttpStatus.LOCKED.value());
    }

    @ExceptionHandler(BaseRuntimeException.class)
    public ApiResult<?> handleBaseRuntimeException(BaseRuntimeException e) {
        logger.error("业务错误", e);
        return createResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(BaseException.class)
    public ApiResult<?> handleBaseException(BaseException e) {
        logger.error("业务错误", e);
        return createResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(AiException.class)
    public ApiResult<?> handleAiException(AiException e) {
        logger.error("业务异常: {}", e.getMessage());
        // 直接透传业务错误码，供前端识别转审批等特殊场景
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<?> handleException(Exception e) {
        String msg = "操作失败";
        String message = e.getMessage();
        if (message != null && isContainChinese(message)) {
            msg = message;
        }
        logger.error(msg, e);
        return createResponseEntity(msg, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * 判断消息是否包含中文
     * @param str
     * @return
     */
    protected boolean isContainChinese(String str) {
        if (str == null) {
            return false;
        }
        Pattern p = Pattern.compile("[\u4e00-\u9fa5]");
        Matcher m = p.matcher(str);
        return m.find();
    }

    /**
     * 构建错误响应体
     * @param msg
     * @param status
     * @return
     */
    protected ApiResult<?> createResponseEntity(String msg, int status) {
        return createResponseEntity(msg, status, true);
    }

    /**
     * 构建错误响应体
     * @param msg
     * @param status
     * @param stripMessagePrefix 是否截断最后一个冒号之前的内容（用于剥离异常类名等冗余前缀）
     * @return
     */
    protected ApiResult<?> createResponseEntity(String msg, int status, boolean stripMessagePrefix) {
        if (stripMessagePrefix && msg.contains(":")) {
            msg = msg.substring(msg.lastIndexOf(":") + 1).trim();
        }

        httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpServletResponse.setCharacterEncoding("UTF-8");
        httpServletResponse.setContentType("application/json; charset=UTF-8");

        return ApiResult.fail(msg, status);
    }
}
