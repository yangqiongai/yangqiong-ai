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
package com.yangqiongai.ai.platform.api.skill;

import com.yangqiongai.ai.agent.skill.generation.SkillGenerationService;
import com.yangqiongai.ai.agent.skill.generation.SkillGenerationService.SkillDraft;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillDefinitionPage;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.model.SkillCategory;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import com.yangqiongai.ai.agent.skill.repository.BuiltinSkillRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillCategoryRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能配置管理
 * @author yangqiong
 */
@Tag(name = "技能配置管理接口")
@RestController
@RequestMapping("/api/agent/skill")
public class SkillConfigController {

    /**
     * 技能启用状态
     */
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillConfigRepository skillConfigRepository;

    /**
     * 内置技能仓库
     */
    @Autowired
    private BuiltinSkillRepository builtinSkillRepository;

    @Autowired
    private SkillGenerationService skillGenerationService;

    /**
     * 技能版本管理
     */
    @Autowired
    private SkillVersionService skillVersionService;

    /**
     * 技能分类管理
     */
    @Autowired
    private SkillCategoryService skillCategoryService;

    /**
     * 技能内容质量评测
     */
    @Autowired
    private SkillQualityEvaluationService skillQualityEvaluationService;

    /**
     * 套餐能力校验器
     */
    @Autowired
    private FeatureGuard featureGuard;

    /**
     * 查询技能列表
     * <p>
     * 技能为独立管理资源，与Agent的挂载关系由agentConfig.skills正向持有。
     * </p>
     * @param categoryCode 分类节点编码（限制返回范围为该节点子树，__ungrouped__表示未分类）
     * @return
     */
    @Operation(summary = "查询技能列表")
    @GetMapping
    public ApiResult<List<SkillDefinition>> list(
            @Parameter(name = "categoryCode", description = "分类节点编码(限制返回范围为该节点子树，__ungrouped__表示未分类)")
            @RequestParam(required = false) String categoryCode) {
        List<SkillDefinition> skills = skillRepository.findAll();
        skills = filterByCategory(skills, categoryCode);
        fillQualityScore(skills);
        return ApiResult.ok(skills);
    }

    /**
     * 分页查询技能
     * <p>
     * source=db时实时查库返回数据库技能(含禁用)；source=builtin时返回classpath内置技能。
     * </p>
     * @param source
     * @param keyword
     * @param categoryCode
     * @param pageNum
     * @param pageSize
     * @return
     */
    @Operation(summary = "分页查询技能")
    @GetMapping("/page")
    public ApiResult<List<SkillDefinition>> page(
            @Parameter(name = "source", description = "技能来源：db=数据库技能，builtin=内置技能") @RequestParam(defaultValue = "db") String source,
            @Parameter(name = "keyword", description = "关键字(匹配ID/名称)") @RequestParam(required = false) String keyword,
            @Parameter(name = "categoryCode", description = "分类节点编码(限制返回范围为该节点子树，__ungrouped__表示未分类)") @RequestParam(required = false) String categoryCode,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        List<SkillDefinition> records;
        long total;
        if ("builtin".equalsIgnoreCase(source)) {
            List<SkillDefinition> all = builtinSkillRepository.findAll();
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase();
                all = all.stream()
                        .filter(s -> (s.getSkillId() != null && s.getSkillId().toLowerCase().contains(kw))
                                || (s.getSkillName() != null && s.getSkillName().toLowerCase().contains(kw)))
                        .toList();
            }
            all = filterByCategory(all, categoryCode);
            total = all.size();
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(all.size(), from + pageSize);
            records = from >= to ? List.of() : all.subList(from, to);
        } else {
            SkillDefinitionPage result = skillConfigRepository.pageSkills(keyword, categoryCode, pageNum, pageSize);
            records = result.getRecords();
            total = result.getTotal();
        }
        fillQualityScore(records);
        return ApiResult.okPage(records, total, pageNum, pageSize);
    }

    /**
     * 按分类子树过滤技能列表（照抄能力模块同名逻辑）
     * @param skills
     * @param categoryCode 分类节点编码，__ungrouped__表示未分类
     * @return
     */
    private List<SkillDefinition> filterByCategory(List<SkillDefinition> skills, String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return skills;
        }
        Set<String> subtreeCodes = skillCategoryService.resolveSubtreeCodes(categoryCode);
        return skills.stream()
                .filter(skill -> {
                    String category = skill.getCategory();
                    boolean ungrouped = category == null || category.isBlank();
                    if (SkillCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
                        return ungrouped;
                    }
                    return !ungrouped && subtreeCodes.contains(category);
                })
                .toList();
    }

    /**
     * 回填当前版本质量评分(取版本快照最新行的评分，版本号一致才回填)
     * @param skills
     */
    private void fillQualityScore(List<SkillDefinition> skills) {
        Map<String, SkillVersionInfo> latestEvaluations = skillConfigRepository.listLatestEvaluations();
        for (SkillDefinition skill : skills) {
            SkillVersionInfo info = latestEvaluations.get(skill.getSkillId());
            if (info != null && info.getVersion() != null
                    && info.getVersion().equals(skill.getSkillVersion())) {
                skill.setQualityScore(info.getQualityScore());
                skill.setEvaluatedTime(info.getEvaluatedTime());
            }
        }
    }

    /**
     * 查询技能详情
     * @param skillId
     * @return
     */
    @Operation(summary = "查询技能详情")
    @GetMapping("/{skillId}")
    public ApiResult<SkillDefinition> getById(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId) {
        return skillRepository.findById(skillId)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillId));
    }

    /**
     * 创建技能
     * @param skill
     * @return
     */
    @Operation(summary = "创建技能")
    @PostMapping
    public ApiResult<SkillDefinition> createSkill(
            @Parameter(name = "skill", description = "技能定义") @RequestBody SkillDefinition skill) {
        // 内置信任等级为classpath内置技能专属，新增技能不允许指定
        if (TrustLevel.BUILTIN.equals(skill.getTrustLevel())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "新增技能不能设置为内置信任等级");
        }
        // 未传skillId时由skillName自动生成
        if (skill.getSkillId() == null || skill.getSkillId().isBlank()) {
            if (skill.getSkillName() == null || skill.getSkillName().isBlank()) {
                return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "skillId与skillName不能同时为空");
            }
            String generatedId = skill.getSkillName().trim();
            if (generatedId.length() > 128) {
                generatedId = generatedId.substring(0, 128);
            }
            skill.setSkillId(generatedId);
        }
        // skill_type列为NOT NULL无默认值，手动创建的技能归入UPLOADED类型（纳入安全扫描范围）
        if (skill.getSkillType() == null || skill.getSkillType().isBlank()) {
            skill.setSkillType("UPLOADED");
        }
        if (skillRepository.findById(skill.getSkillId()).isPresent()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "技能已存在: " + skill.getSkillId());
        }
        skillCategoryService.validateCategory(skill.getCategory());
        skillRepository.save(skill);
        skillVersionService.publishChanged(SkillChangedEvent.ACTION_CREATE, skill.getSkillId(), null);
        return ApiResult.ok(skill);
    }

    /**
     * 更新技能
     * @param skillId
     * @param skill
     * @return
     */
    @Operation(summary = "更新技能")
    @PutMapping("/{skillId}")
    public ApiResult<SkillDefinition> updateSkill(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @Parameter(name = "skill", description = "技能定义") @RequestBody SkillDefinition skill) {
        SkillDefinition existing = skillRepository.findById(skillId).orElse(null);
        if (existing == null) {
            return ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillId);
        }
        // 内置信任等级为classpath内置技能专属，非内置技能不允许升级为内置
        if (TrustLevel.BUILTIN.equals(skill.getTrustLevel())
                && !TrustLevel.BUILTIN.equals(existing.getTrustLevel())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "非内置技能不能设置为内置信任等级");
        }
        skill.setSkillId(skillId);
        skillCategoryService.validateCategory(skill.getCategory());
        skillRepository.save(skill);
        skillVersionService.publishChanged(SkillChangedEvent.ACTION_UPDATE, skillId, null);
        return ApiResult.ok(skill);
    }

    /**
     * 生成技能
     * @param request
     * @return
     */
    @Operation(summary = "生成技能")
    @PostMapping("/generation/generate")
    public ApiResult<SkillDraft> generate(
            @Parameter(name = "request", description = "技能生成请求，包含skillName、description") @RequestBody Map<String, String> request) {
        String skillName = request.get("skillName");
        String description = request.get("description");
        if (skillName == null || skillName.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "skillName不能为空");
        }
        if (description == null || description.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "description不能为空");
        }
        return ApiResult.ok(skillGenerationService.generate(skillName, description));
    }

    /**
     * 获取生成草稿
     * @param draftId
     * @return
     */
    @Operation(summary = "获取生成草稿")
    @GetMapping("/draft/{draftId}")
    public ApiResult<SkillDraft> getDraft(
            @Parameter(name = "draftId", description = "草稿ID") @PathVariable String draftId) {
        return skillGenerationService.getDraft(draftId)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "草稿不存在: " + draftId));
    }

    /**
     * 确认草稿并保存为正式技能
     * @param draftId
     * @return
     */
    @Operation(summary = "确认草稿并保存为正式技能")
    @PostMapping("/draft/{draftId}/confirm")
    public ApiResult<SkillDefinition> confirmDraft(
            @Parameter(name = "draftId", description = "草稿ID") @PathVariable String draftId) {
        return ApiResult.ok(skillGenerationService.confirmDraft(draftId));
    }

    /**
     * 拒绝草稿
     * @param draftId
     * @return
     */
    @Operation(summary = "拒绝草稿")
    @PostMapping("/draft/{draftId}/reject")
    public ApiResult<Void> rejectDraft(
            @Parameter(name = "draftId", description = "草稿ID") @PathVariable String draftId) {
        skillGenerationService.rejectDraft(draftId);
        return ApiResult.ok();
    }

    /**
     * 删除技能
     * @param skillId
     * @return
     */
    @Operation(summary = "删除技能")
    @DeleteMapping("/{skillId}")
    public ApiResult<Void> deleteSkill(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId) {
        SkillDefinition skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null) {
            return ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillId);
        }
        // 内置技能随服务分发，不允许删除
        if (TrustLevel.BUILTIN.equals(skill.getTrustLevel())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "内置技能不允许删除");
        }
        skillRepository.deleteById(skillId);
        return ApiResult.ok();
    }

    /**
     * 技能内容质量评测（对当前版本单次LLM评分，同步返回结果并落库）
     * @param skillId
     * @return
     */
    @Operation(summary = "技能内容质量评测")
    @PostMapping("/{skillId}/evaluate")
    public ApiResult<SkillEvalResultDto> evaluate(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId) {
        try {
            return ApiResult.ok(skillQualityEvaluationService.evaluate(skillId));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 切换技能状态
     * @param skillId
     * @param body
     * @return
     */
    @Operation(summary = "切换技能状态")
    @PutMapping("/{skillId}/status")
    public ApiResult<Void> toggleStatus(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "status不能为空");
        }
        if (status != 0 && status != 1) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "status只能为0或1");
        }
        if (status == STATUS_ENABLED) {
            featureGuard.checkSkillAllowed(skillId);
        }
        if (!skillConfigRepository.toggleStatus(skillId, status)) {
            return ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillId);
        }
        return ApiResult.ok();
    }

    /**
     * 设置技能信任等级
     * @param skillId
     * @param body
     * @return
     */
    @Operation(summary = "设置技能信任等级")
    @PutMapping("/{skillId}/trust-level")
    public ApiResult<Void> setTrustLevel(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @RequestBody Map<String, String> body) {
        String trustLevel = body.get("trustLevel");
        if (trustLevel == null || trustLevel.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "trustLevel不能为空");
        }
        List<String> validLevels = List.of("BUILTIN", "TRUSTED", "COMMUNITY", "AGENT_CREATED");
        if (!validLevels.contains(trustLevel)) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(),
                    "trustLevel只能为: " + String.join(", ", validLevels));
        }
        // 内置信任等级为classpath内置技能专属，非内置技能不允许设置为内置
        if ("BUILTIN".equals(trustLevel)) {
            SkillDefinition skill = skillRepository.findById(skillId).orElse(null);
            if (skill != null && !TrustLevel.BUILTIN.equals(skill.getTrustLevel())) {
                return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "非内置技能不能设置为内置信任等级");
            }
        }
        if (!skillConfigRepository.updateTrustLevel(skillId, trustLevel)) {
            return ApiResult.fail(AiErrorCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillId);
        }
        return ApiResult.ok();
    }

    /**
     * 查询版本历史（版本号倒序分页，不返回内容全文）
     * @param skillId
     * @param pageNum
     * @param pageSize
     * @return
     */
    @Operation(summary = "查询版本历史")
    @GetMapping("/{skillId}/versions")
    public ApiResult<List<SkillVersionDto>> listVersions(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        SkillVersionService.VersionPage page = skillVersionService.pageVersions(skillId, pageNum, pageSize);
        return ApiResult.okPage(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询单版本快照详情（含内容全文）
     * @param skillId
     * @param version
     * @return
     */
    @Operation(summary = "查询版本快照详情")
    @GetMapping("/{skillId}/versions/{version}")
    public ApiResult<SkillVersionDetailDto> getVersionDetail(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @Parameter(name = "version", description = "版本号") @PathVariable int version) {
        return ApiResult.ok(skillVersionService.getVersionDetail(skillId, version));
    }

    /**
     * 回滚技能内容至指定版本（产生新版本号，不删历史）
     * @param skillId
     * @param body
     * @return
     */
    @Operation(summary = "回滚技能内容至指定版本")
    @PostMapping("/{skillId}/rollback")
    public ApiResult<SkillDefinition> rollback(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @Parameter(name = "body", description = "回滚请求，包含targetVersion、可选remark") @RequestBody Map<String, Object> body) {
        Integer targetVersion = parseInteger(body.get("targetVersion"));
        if (targetVersion == null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "targetVersion不能为空且必须为整数");
        }
        String remark = body.get("remark") instanceof String s && !s.isBlank() ? s : null;
        try {
            return ApiResult.ok(skillVersionService.rollback(skillId, targetVersion, remark, null));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 安全解析整型参数
     * @param value
     * @return
     */
    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 查询技能分类树（各节点含挂载技能数）
     * @return
     */
    @Operation(summary = "查询技能分类树")
    @GetMapping("/category/tree")
    public ApiResult<Map<String, Object>> categoryTree() {
        return ApiResult.ok(skillCategoryService.tree());
    }

    /**
     * 新增技能分类节点
     * @param category
     * @return
     */
    @Operation(summary = "新增技能分类节点")
    @PostMapping("/category")
    public ApiResult<SkillCategory> createCategory(@RequestBody SkillCategory category) {
        try {
            return ApiResult.ok(skillCategoryService.create(category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 更新技能分类节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param category
     * @return
     */
    @Operation(summary = "更新技能分类节点")
    @PutMapping("/category/{id}")
    public ApiResult<SkillCategory> updateCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id,
            @RequestBody SkillCategory category) {
        try {
            return ApiResult.ok(skillCategoryService.update(id, category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 删除技能分类节点（存在子节点或挂载技能时禁止删除）
     * @param id
     * @return
     */
    @Operation(summary = "删除技能分类节点")
    @DeleteMapping("/category/{id}")
    public ApiResult<Void> deleteCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id) {
        try {
            skillCategoryService.delete(id);
            return ApiResult.ok();
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }
}
