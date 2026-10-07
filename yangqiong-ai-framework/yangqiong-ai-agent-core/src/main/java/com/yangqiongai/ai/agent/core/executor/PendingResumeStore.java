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
package com.yangqiongai.ai.agent.core.executor;

/**
 * 暂停恢复登记存储
 * <p>
 * 引擎确认与澄清暂停现场的登记存储SPI，内存实现为单机默认行为，
 * JDBC实现支持多节点部署下跨节点恢复，pop为原子抢占防重复恢复。
 * </p>
 * @author yangqiong
 */
public interface PendingResumeStore {

    /**
     * 登记暂停恢复现场（同requestId重复登记覆盖）
     * @param entry
     */
    void register(PendingResumeEntry entry);

    /**
     * 弹出登记（取后即删，原子抢占防重复恢复）
     * @param requestId
     * @return 不存在、已恢复或已过期返回null
     */
    PendingResumeEntry pop(String requestId);

    /**
     * 检查登记是否存在且待恢复
     * @param requestId
     * @return
     */
    boolean exists(String requestId);
}
