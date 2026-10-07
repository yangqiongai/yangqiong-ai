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
package com.yangqiongai.ai.platform.knowledge.service;

import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;

import java.util.List;
import java.util.Optional;

/**
 * 知识库管理
 * @author yangqiong
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库
     * @param kb
     * @return
     */
    KnowledgeBase create(KnowledgeBase kb);

    /**
     * 根据kbId查找
     * @param kbId
     * @return
     */
    Optional<KnowledgeBase> findByKbId(String kbId);

    /**
     * 查询所有知识库
     * @return
     */
    List<KnowledgeBase> findAll();

    /**
     * 根据用户ID查询知识库列表
     * @param userId
     * @return
     */
    List<KnowledgeBase> findByUserId(String userId);

    /**
     * 更新知识库
     * @param kb
     * @return
     */
    KnowledgeBase update(KnowledgeBase kb);

    /**
     * 删除知识库
     * @param kbId
     */
    void deleteByKbId(String kbId);

    /**
     * 物理删除知识库及其所有关联数据
     * @param kbId
     */
    void purgeKnowledgeBase(String kbId);
}
