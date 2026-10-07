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
package com.yangqiongai.ai.platform.bss.scope.auth;

import com.yangqiongai.ai.platform.bss.exception.ForbiddenException;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 权限点校验切面
 * <p>
 * 拦截 @RequirePermission 注解并校验当前用户权限集；
 * 仅在 ai.system.auth.enabled=true 时装配（社区版默认关闭，不拦截任何请求）。
 * </p>
 * @author yangqiong
 */
@Aspect
@Component
@ConditionalOnProperty(name = "ai.system.auth.enabled", havingValue = "true")
public class PermissionCheckAspect {

    /**
     * 方法级注解优先，其次类级注解，校验权限点
     * @param joinPoint
     * @param requirePermission
     */
    @Before("@annotation(requirePermission) || @within(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        AuthContext context = AuthContextHolder.get();
        if (context.isPlatformAdmin()) {
            return;
        }
        RequirePermission annotation = resolveAnnotation(joinPoint, requirePermission);
        if (annotation == null) {
            return;
        }
        String[] permCodes = annotation.value();
        if (permCodes == null || permCodes.length == 0) {
            return;
        }
        boolean pass;
        if (annotation.mode() == RequirePermission.MatchMode.ALL) {
            pass = Arrays.stream(permCodes).allMatch(context::hasPermission);
        } else {
            pass = Arrays.stream(permCodes).anyMatch(context::hasPermission);
        }
        if (!pass) {
            // 消息避免使用冒号，全局异常处理器会截断最后一个冒号之前的内容
            throw new ForbiddenException("无访问权限，缺少权限码 " + String.join(",", permCodes));
        }
    }

    /**
     * 解析权限注解：绑定值为空时从方法与类上兜底读取
     * @param joinPoint
     * @param requirePermission
     * @return
     */
    private RequirePermission resolveAnnotation(JoinPoint joinPoint, RequirePermission requirePermission) {
        if (requirePermission != null) {
            return requirePermission;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequirePermission annotation = signature.getMethod().getAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    signature.getDeclaringType(), RequirePermission.class);
        }
        return annotation;
    }
}
