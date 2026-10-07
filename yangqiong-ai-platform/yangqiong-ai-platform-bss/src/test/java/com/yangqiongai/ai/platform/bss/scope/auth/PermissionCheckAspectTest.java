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
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权限点校验切面单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckAspectTest {

    @Mock
    private JoinPoint joinPoint;

    private PermissionCheckAspect permissionCheckAspect;

    @BeforeEach
    void setUp() {
        permissionCheckAspect = new PermissionCheckAspect();
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    /**
     * 构建权限注解桩
     * @param permCodes
     * @param mode
     * @return
     */
    private RequirePermission annotation(String[] permCodes, RequirePermission.MatchMode mode) {
        RequirePermission requirePermission = mock(RequirePermission.class);
        lenient().when(requirePermission.value()).thenReturn(permCodes);
        lenient().when(requirePermission.mode()).thenReturn(mode);
        return requirePermission;
    }

    @Test
    void platformAdminPassesAnyCheck() {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(), Set.of(), "SELF", null, true));

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:user:manage"}, RequirePermission.MatchMode.ALL)))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyAnnotationPasses() {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(), Set.of(), "SELF", null, false));

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[0], RequirePermission.MatchMode.ALL)))
                .doesNotThrowAnyException();
    }

    @Test
    void allModeRequiresEveryPermission() {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(),
                Set.of("system:org:manage", "system:role:manage"), "SELF", null, false));

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:org:manage", "system:role:manage"}, RequirePermission.MatchMode.ALL)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:org:manage", "system:user:manage"}, RequirePermission.MatchMode.ALL)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无访问权限");
    }

    @Test
    void anyModePassesWithSingleMatch() {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(),
                Set.of("system:org:manage"), "SELF", null, false));

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:org:manage", "system:user:manage"}, RequirePermission.MatchMode.ANY)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:user:manage"}, RequirePermission.MatchMode.ANY)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void wildcardPermissionPassesAllChecks() {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(),
                Set.of(AuthContext.ALL_PERMISSIONS), "SELF", null, false));

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"any:resource:action"}, RequirePermission.MatchMode.ALL)))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyContextFailsPermissionCheck() {
        assertThatThrownBy(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:user:manage"}, RequirePermission.MatchMode.ANY)))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * 权限拒绝异常必须是非受检异常：经AOP代理抛出时受检形态会被包装为UndeclaredThrowableException丢失403语义
     */
    @Test
    void forbiddenExceptionMustBeUnchecked() {
        assertThatThrownBy(() -> permissionCheckAspect.checkPermission(joinPoint,
                annotation(new String[]{"system:user:manage"}, RequirePermission.MatchMode.ANY)))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * 方法打桩类：方法级注解
     */
    static class MethodAnnotatedFixture {

        @RequirePermission("system:org:manage")
        void annotatedMethod() {
        }
    }

    /**
     * 类打桩类：类级注解
     */
    @RequirePermission("system:org:manage")
    static class TypeAnnotatedFixture {

        void plainMethod() {
        }
    }

    /**
     * 无注解打桩类
     */
    static class UnAnnotatedFixture {

        void plainMethod() {
        }
    }

    /**
     * 绑定值为空时从方法级注解兜底解析
     * @throws NoSuchMethodException
     */
    @Test
    void nullBindingFallsBackToMethodAnnotation() throws NoSuchMethodException {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(),
                Set.of("system:org:manage"), "SELF", null, false));
        Method method = MethodAnnotatedFixture.class.getDeclaredMethod("annotatedMethod");
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint, null))
                .doesNotThrowAnyException();
    }

    /**
     * 绑定值为空且方法无注解时从类级注解兜底解析
     * @throws NoSuchMethodException
     */
    @Test
    void nullBindingFallsBackToTypeAnnotation() throws NoSuchMethodException {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(),
                Set.of("system:org:manage"), "SELF", null, false));
        Method method = TypeAnnotatedFixture.class.getDeclaredMethod("plainMethod");
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(TypeAnnotatedFixture.class);

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint, null))
                .doesNotThrowAnyException();

        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(), Set.of(), "SELF", null, false));
        assertThatThrownBy(() -> permissionCheckAspect.checkPermission(joinPoint, null))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * 绑定值为空且方法与类均无注解时放行
     * @throws NoSuchMethodException
     */
    @Test
    void nullBindingWithoutAnyAnnotationPasses() throws NoSuchMethodException {
        AuthContextHolder.set(new AuthContext("1", "s1", Set.of(), Set.of(), "SELF", null, false));
        Method method = UnAnnotatedFixture.class.getDeclaredMethod("plainMethod");
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(UnAnnotatedFixture.class);

        assertThatCode(() -> permissionCheckAspect.checkPermission(joinPoint, null))
                .doesNotThrowAnyException();
    }
}
