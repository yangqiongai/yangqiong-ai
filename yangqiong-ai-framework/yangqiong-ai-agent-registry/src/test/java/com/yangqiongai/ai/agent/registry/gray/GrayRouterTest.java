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
package com.yangqiongai.ai.agent.registry.gray;

import com.yangqiongai.ai.agent.data.registry.entity.AgentGrayRule;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentGrayRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 灰度路由器单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrayRouter 单元测试")
class GrayRouterTest {

    @Mock
    private AgentGrayRuleMapper grayRuleMapper;

    private GrayRouter grayRouter;

    @BeforeEach
    void setUp() {
        grayRouter = new GrayRouter();
        org.springframework.test.util.ReflectionTestUtils.setField(grayRouter, "grayRuleMapper", grayRuleMapper);
    }

    /**
     * 构造灰度规则
     * @param ruleType
     * @param ruleValue
     * @param grayPercent
     * @return
     */
    private AgentGrayRule rule(String ruleType, String ruleValue, Integer grayPercent) {
        AgentGrayRule rule = new AgentGrayRule();
        rule.setId(1L);
        rule.setAgentCode("agent-a");
        rule.setRuleType(ruleType);
        rule.setRuleValue(ruleValue);
        rule.setGrayPercent(grayPercent);
        rule.setStatus("ACTIVE");
        rule.setTargetVersionId(2L);
        return rule;
    }

    @Test
    @DisplayName("无规则时未命中")
    void noRulesMiss() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of());
        GrayDecision decision = grayRouter.route("agent-a", "u1", "scope-1");
        assertThat(decision.isHit()).isFalse();
        assertThat(decision.getTargetVersionId()).isNull();
    }

    @Test
    @DisplayName("USER_HASH：0%永不命中")
    void userHashZeroPercentMiss() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("USER_HASH", null, 0)));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("USER_HASH：100%必命中")
    void userHashFullPercentHit() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("USER_HASH", null, 100)));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isTrue();
    }

    @Test
    @DisplayName("USER_HASH：同一用户结果确定")
    void userHashDeterministic() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("USER_HASH", null, 50)));
        boolean first = grayRouter.route("agent-a", "user-x", null).isHit();
        for (int i = 0; i < 5; i++) {
            assertThat(grayRouter.route("agent-a", "user-x", null).isHit()).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("USER_HASH：userId为空不命中")
    void userHashNullUserIdMiss() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("USER_HASH", null, 100)));
        assertThat(grayRouter.route("agent-a", null, null).isHit()).isFalse();
        assertThat(grayRouter.route("agent-a", "  ", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("USER_WHITELIST：名单内命中")
    void whitelistHit() {
        when(grayRuleMapper.selectList(any()))
                .thenReturn(List.of(rule("USER_WHITELIST", "[\"u1\",\"u2\"]", null)));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isTrue();
    }

    @Test
    @DisplayName("USER_WHITELIST：名单外未命中")
    void whitelistMiss() {
        when(grayRuleMapper.selectList(any()))
                .thenReturn(List.of(rule("USER_WHITELIST", "[\"u1\",\"u2\"]", null)));
        assertThat(grayRouter.route("agent-a", "u9", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("USER_WHITELIST：非法JSON按未命中处理")
    void whitelistInvalidValueMiss() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("USER_WHITELIST", "not-json", null)));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("SCOPE_LIST：作用域命中与scopeId为空未命中")
    void scopeListMatch() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("SCOPE_LIST", "[\"s1\"]", null)));
        assertThat(grayRouter.route("agent-a", "u1", "s1").isHit()).isTrue();
        assertThat(grayRouter.route("agent-a", "u1", "s2").isHit()).isFalse();
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("PAUSED规则被忽略")
    void pausedRuleIgnored() {
        AgentGrayRule paused = rule("USER_WHITELIST", "[\"u1\"]", null);
        paused.setStatus("PAUSED");
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(paused));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("多规则按顺序取首条命中")
    void firstMatchWins() {
        AgentGrayRule first = rule("USER_WHITELIST", "[\"other\"]", null);
        first.setId(10L);
        AgentGrayRule second = rule("USER_WHITELIST", "[\"u1\"]", null);
        second.setId(11L);
        second.setTargetVersionId(22L);
        List<AgentGrayRule> rules = new ArrayList<>();
        rules.add(first);
        rules.add(second);
        when(grayRuleMapper.selectList(any())).thenReturn(rules);
        GrayDecision decision = grayRouter.route("agent-a", "u1", null);
        assertThat(decision.isHit()).isTrue();
        assertThat(decision.getTargetVersionId()).isEqualTo(22L);
    }

    @Test
    @DisplayName("时间窗口：未开始与已结束的规则被跳过")
    void timeWindowFilter() {
        AgentGrayRule notStarted = rule("USER_WHITELIST", "[\"u1\"]", null);
        notStarted.setStartTime(LocalDateTime.now().plusHours(1));
        AgentGrayRule ended = rule("USER_WHITELIST", "[\"u1\"]", null);
        ended.setEndTime(LocalDateTime.now().minusHours(1));
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(notStarted, ended));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isFalse();
    }

    @Test
    @DisplayName("时间窗口：窗口内规则正常命中")
    void timeWindowInsideHit() {
        AgentGrayRule active = rule("USER_WHITELIST", "[\"u1\"]", null);
        active.setStartTime(LocalDateTime.now().minusSeconds(1));
        active.setEndTime(LocalDateTime.now().plusSeconds(1));
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(active));
        assertThat(grayRouter.route("agent-a", "u1", null).isHit()).isTrue();
    }

    @Test
    @DisplayName("未知规则类型按未命中处理")
    void unknownRuleTypeMiss() {
        when(grayRuleMapper.selectList(any())).thenReturn(List.of(rule("UNKNOWN_TYPE", "x", null)));
        assertThat(grayRouter.route("agent-a", "u1", "s1").isHit()).isFalse();
    }
}
