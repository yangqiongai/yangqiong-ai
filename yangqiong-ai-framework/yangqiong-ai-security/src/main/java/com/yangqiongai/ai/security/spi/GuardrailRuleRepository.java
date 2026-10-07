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
package com.yangqiongai.ai.security.spi;

import java.util.List;

/**
 * 护栏规则仓库SPI（社区执行引擎与商业规则中心的挂载契约，社区默认内存实现）
 *
 * @author yangqiong
 */
public interface GuardrailRuleRepository {

    /**
     * 查询所有启用的规则
     * @return
     */
    List<GuardrailRuleEntity> findAllEnabled();

    /**
     * 查询指定挂载点的启用规则
     * @param hookPoint
     * @return
     */
    List<GuardrailRuleEntity> findEnabledByHookPoint(String hookPoint);

    /**
     * 查询所有规则（含禁用）
     * @return
     */
    List<GuardrailRuleEntity> findAll();

    /**
     * 按规则名称查询
     * @param name
     * @return
     */
    GuardrailRuleEntity findByName(String name);

    /**
     * 保存规则（新增或更新，按name去重）
     * @param rule
     */
    void saveRule(GuardrailRuleEntity rule);

    /**
     * 切换规则启用/禁用状态
     * @param ruleId
     * @param isEnabled
     * @return
     */
    boolean toggleStatus(String ruleId, int isEnabled);

    /**
     * 删除规则
     * @param ruleId
     * @return
     */
    boolean deleteByRuleId(String ruleId);
}
