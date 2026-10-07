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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限点校验注解
 * <p>
 * 标注在 Controller 方法或类上，由 PermissionCheckAspect 校验当前用户权限集；
 * 类级与方法级同时存在时以方法级为准。
 * </p>
 * @author yangqiong
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限点编码，格式 {product}:{resource}:{action}
     * @return
     */
    String[] value();

    /**
     * 多权限点校验模式：ANY 任一满足 / ALL 全部满足
     * @return
     */
    MatchMode mode() default MatchMode.ANY;

    /**
     * 多权限点校验模式
     */
    enum MatchMode {

        /**
         * 任一满足
         */
        ANY,

        /**
         * 全部满足
         */
        ALL
    }
}
