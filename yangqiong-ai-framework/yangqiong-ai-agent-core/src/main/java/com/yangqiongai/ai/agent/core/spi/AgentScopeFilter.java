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
package com.yangqiongai.ai.agent.core.spi;

import com.yangqiongai.ai.agent.core.model.Agent;

import java.util.List;

/**
 * Agent数据范围过滤
 * <p>
 * 社区版不装配（全放行）；企业版实现租户隔离（复制模式）：租户仅可见/可编辑
 * 本租户域（scopeId=租户ID）的 Agent（含创建租户时从平台域复制的副本），
 * 平台域模板（scopeId=default）对租户不可见，仅平台管理员可见可编辑。
 * </p>
 * @author yangqiong
 */
public interface AgentScopeFilter {

    /**
     * 平台模板作用域
     */
    String PLATFORM_SCOPE = "default";

    /**
     * 过滤Agent列表（列表/启用列表查询用）
     * @param agents
     * @return
     */
    List<Agent> filterList(List<Agent> agents);

    /**
     * 过滤单个Agent（越权时返回null，按不存在处理）
     * @param agent
     * @return
     */
    Agent filterOne(Agent agent);

    /**
     * 校验编辑权限（更新/状态切换/排序用，无权限时抛出禁止访问异常）
     * @param agent
     */
    void checkEditable(Agent agent);
}
