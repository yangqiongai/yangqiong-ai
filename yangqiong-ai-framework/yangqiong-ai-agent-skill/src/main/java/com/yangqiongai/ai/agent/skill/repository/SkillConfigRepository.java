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
package com.yangqiongai.ai.agent.skill.repository;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillDefinitionPage;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;

import java.util.List;

/**
 * 技能配置仓库
 * @author yangqiong
 */
public interface SkillConfigRepository {

    /**
     * 根据skillId查询
     * @param skillId
     * @return
     */
    SkillDefinition getBySkillId(String skillId);

    /**
     * 查询所有启用的技能
     * @return
     */
    List<SkillDefinition> listEnabled();

    /**
     * 查询全部技能(含禁用，用于同名技能合并等全量比对场景)
     * @return
     */
    List<SkillDefinition> listAll();

    /**
     * 分页查询技能配置(含禁用，实时查库不走缓存，用于管理端列表)
     * @param keyword
     * @param categoryCode 分类节点编码（限制返回范围为该节点子树，__ungrouped__表示未分类）
     * @param pageNum
     * @param pageSize
     * @return
     */
    SkillDefinitionPage pageSkills(String keyword, String categoryCode, int pageNum, int pageSize);

    /**
     * 按信任等级查询启用的技能
     * @param trustLevel
     * @return
     */
    List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel);

    /**
     * 保存技能（新增或更新）
     * @param definition
     * @param operator
     * @param remark
     */
    void saveSkill(SkillDefinition definition, String operator, String remark);

    /**
     * 分页查询版本快照（版本号倒序）
     * @param skillId
     * @param pageNum
     * @param pageSize
     * @return
     */
    List<SkillVersionInfo> listVersions(String skillId, int pageNum, int pageSize);

    /**
     * 统计版本快照总数
     * @param skillId
     * @return
     */
    long countVersions(String skillId);

    /**
     * 查询单个版本快照
     * @param skillId
     * @param version
     * @return
     */
    SkillVersionInfo getVersion(String skillId, int version);

    /**
     * 保存版本质量评分（当前版本无快照行时自动补建，有则仅更新评分列）
     * @param skillId
     * @param version
     * @param skillContent
     * @param qualityScore
     * @param evalDimensionsJson
     * @param evalModel
     */
    void saveEvaluation(String skillId, int version, String skillContent, java.math.BigDecimal qualityScore,
                        String evalDimensionsJson, String evalModel);

    /**
     * 查询各技能最新版本的评分信息（skillId → 最新版本快照）
     * @return
     */
    java.util.Map<String, SkillVersionInfo> listLatestEvaluations();

    /**
     * 切换技能状态
     * @param skillId
     * @param status
     * @return
     */
    boolean toggleStatus(String skillId, int status);

    /**
     * 删除技能
     * @param skillId
     * @return
     */
    boolean deleteBySkillId(String skillId);

    /**
     * 更新技能信任等级
     * @param skillId
     * @param trustLevel
     * @return
     */
    boolean updateTrustLevel(String skillId, String trustLevel);
}
