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

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * AI平台异常码
 * @author yangqiong
 */
@ControllerAdvice
@ResponseBody
public class GlobalException extends ExceptionAdvice {

    @ExceptionHandler({AiException.class})
    public ApiResult handleAiException(AiException e) {
        logger.error("业务异常", e);
        // 直接透传业务错误码，不走createResponseEntity(冒号截断消息且code固定-1)
        return ApiResult.fail(e.getCode(), e.getMessage());
    }


}
