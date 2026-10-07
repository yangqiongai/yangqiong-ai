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
package com.yangqiongai.ai.open.capability.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 能力定义版本历史存储
 * <p>
 * 记录能力定义每次创建/更新/回滚的完整快照（ai_open_capability_history 表），
 * 支持查看历史版本与回滚。
 * </p>
 * @author yangqiong
 */
public interface CapabilityDefinitionHistoryRepository {

    /**
     * 保存版本快照
     * @param history
     */
    void save(CapabilityDefinitionHistory history);

    /**
     * 按能力编码查询版本历史（按快照时间倒序）
     * @param code
     * @return
     */
    List<CapabilityDefinitionHistory> findByCode(String code);

    /**
     * 按主键查询版本快照
     * @param id
     * @return
     */
    Optional<CapabilityDefinitionHistory> findById(Long id);

    /**
     * 删除能力编码下的全部版本历史（能力删除时同步清理）
     * @param code
     */
    void deleteByCode(String code);
}
