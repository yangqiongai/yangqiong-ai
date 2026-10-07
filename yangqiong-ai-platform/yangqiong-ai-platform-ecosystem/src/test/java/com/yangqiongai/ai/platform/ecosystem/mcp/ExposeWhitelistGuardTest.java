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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 白名单防护测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class ExposeWhitelistGuardTest {

    @Mock
    private McpExposeService exposeService;

    private McpServerExpose expose(String code) {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_TOOL);
        expose.setExposeCode(code);
        expose.setEnabled(1);
        return expose;
    }

    @Test
    void checkShouldPassWhenInWhitelist() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL))
                .thenReturn(List.of(expose("demo_tool")));
        ExposeWhitelistGuard guard = new ExposeWhitelistGuard(exposeService);

        assertThatCode(() -> guard.check(McpServerExpose.TYPE_TOOL, "demo_tool"))
                .doesNotThrowAnyException();
    }

    @Test
    void checkShouldRejectWhenNotInWhitelist() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        ExposeWhitelistGuard guard = new ExposeWhitelistGuard(exposeService);

        assertThatThrownBy(() -> guard.check(McpServerExpose.TYPE_TOOL, "demo_tool"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("资源未开放")
                .extracting(e -> ((AiException) e).getCode())
                .isEqualTo(AiErrorCode.FORBIDDEN.getCode());
    }
}
