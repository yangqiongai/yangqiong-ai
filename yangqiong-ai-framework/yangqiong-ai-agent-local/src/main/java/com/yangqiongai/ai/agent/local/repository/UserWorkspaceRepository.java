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
package com.yangqiongai.ai.agent.local.repository;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;

import java.util.List;

/**
 * 用户工作区仓库
 * @author yangqiong
 */
public interface UserWorkspaceRepository {

    /**
     * 保存工作区
     * @param info
     */
    void save(WorkspaceInfo info);

    /**
     * 按ID更新
     * @param info
     */
    void updateById(WorkspaceInfo info);

    /**
     * 按ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 按ID查询（不校验归属）
     * @param id
     * @return
     */
    WorkspaceInfo findById(Long id);

    /**
     * 按ID与用户查询（归属校验）
     * @param id
     * @param userId
     * @return
     */
    WorkspaceInfo findByIdAndUserId(Long id, String userId);

    /**
     * 按用户查询工作区列表
     * @param userId
     * @return
     */
    List<WorkspaceInfo> listByUserId(String userId);

    /**
     * 根路径是否已登记
     * @param rootPath
     * @return
     */
    boolean existsByRootPath(String rootPath);

    /**
     * 统计用户工作区数量
     * @param userId
     * @return
     */
    long countByUserId(String userId);
}
