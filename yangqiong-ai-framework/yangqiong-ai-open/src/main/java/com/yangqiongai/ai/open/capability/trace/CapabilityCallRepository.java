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
package com.yangqiongai.ai.open.capability.trace;

import java.util.List;

/**
 * 能力调用仓储
 * <p>
 * 存储能力调用记录。
 * </p>
 * @author yangqiong
 */
public interface CapabilityCallRepository {

    /**
     * 保存调用记录
     * @param record
     */
    void save(CapabilityCallRecord record);

    /**
     * 按ID查询调用记录
     * @param id
     * @return
     */
    CapabilityCallRecord findById(String id);

    /**
     * 按能力编码查询调用记录列表
     * @param capability
     * @return
     */
    List<CapabilityCallRecord> findByCapability(String capability);

    /**
     * 查询所有调用记录
     * @return
     */
    List<CapabilityCallRecord> findAll();
}