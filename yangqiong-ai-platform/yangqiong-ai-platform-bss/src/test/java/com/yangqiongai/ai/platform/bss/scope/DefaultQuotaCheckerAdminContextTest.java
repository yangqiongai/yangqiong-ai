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
package com.yangqiongai.ai.platform.bss.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DefaultQuotaChecker与DefaultAdminContext单元测试
 * @author yangqiong
 */
@DisplayName("DefaultQuotaChecker与DefaultAdminContext单元测试")
class DefaultQuotaCheckerAdminContextTest {

    @Test
    @DisplayName("checkQuota 不抛出异常")
    void shouldNotThrowWhenCheckQuota() {
        DefaultQuotaChecker checker = new DefaultQuotaChecker();

        assertThatCode(() -> checker.checkQuota("conversation", 100L, 1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("超过配额也不抛出异常（默认全部放行）")
    void shouldNotThrowWhenExceedQuota() {
        DefaultQuotaChecker checker = new DefaultQuotaChecker();

        assertThatCode(() -> checker.checkQuota("token", Long.MAX_VALUE, Long.MAX_VALUE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("传入 null resourceType 不抛出异常")
    void shouldNotThrowWhenResourceTypeIsNull() {
        DefaultQuotaChecker checker = new DefaultQuotaChecker();

        assertThatCode(() -> checker.checkQuota(null, 0L, 1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isAdmin 始终返回 false")
    void shouldReturnFalseForIsAdmin() {
        DefaultAdminContext context = new DefaultAdminContext();

        assertThat(context.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("setAdmin 不影响 isAdmin 结果")
    void shouldNotAffectResultAfterSet() {
        DefaultAdminContext context = new DefaultAdminContext();
        context.setAdmin(true);

        assertThat(context.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("clear 不抛出异常")
    void shouldNotThrowWhenClear() {
        DefaultAdminContext context = new DefaultAdminContext();

        assertThatCode(context::clear).doesNotThrowAnyException();
    }
}
