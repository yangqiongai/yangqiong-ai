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
package com.yangqiongai.ai.open.capability.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinition;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityLoader;
import com.yangqiongai.ai.open.capability.spec.AgentOverrides;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRecord;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开放能力目录管理
 * <p>
 * 面向管理后台的能力定义维护接口，提供目录查询与数据库定义的新增/覆盖/启停/删除，
 * 以及能力分类树的管理（节点下挂能力，能力列表支持按分类子树过滤）。
 * </p>
 * @author yangqiong
 */
@RestController
@RequestMapping("/api/open-capability")
public class OpenCapabilityAdminController {

    private static final Logger log = LoggerFactory.getLogger(OpenCapabilityAdminController.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 未分类能力的虚拟分类编码（列表过滤与树计数使用）
     */
    private static final String UNGROUPED_CODE = "__ungrouped__";

    private final CapabilityCatalog catalog;
    private final CapabilityDefinitionRepository definitionRepository;
    private final CapabilityCategoryRepository categoryRepository;
    private final CapabilityCallRepository callRepository;
    private final CapabilityDefinitionHistoryRepository historyRepository;

    public OpenCapabilityAdminController(CapabilityCatalog catalog,
                                          CapabilityDefinitionRepository definitionRepository,
                                          CapabilityCategoryRepository categoryRepository,
                                          CapabilityCallRepository callRepository,
                                          CapabilityDefinitionHistoryRepository historyRepository) {
        this.catalog = catalog;
        this.definitionRepository = definitionRepository;
        this.categoryRepository = categoryRepository;
        this.callRepository = callRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * 分页查询能力目录
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @param keyword 关键字（匹配编码/名称/分类）
     * @param categoryCode 分类节点编码（限制返回范围为该节点子树，__ungrouped__表示未分类）
     * @return
     */
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode) {
        List<CapabilitySpec> items = catalog.list();
        if (categoryCode != null && !categoryCode.isBlank()) {
            Set<String> subtreeCodes = UNGROUPED_CODE.equals(categoryCode)
                    ? Set.of(UNGROUPED_CODE)
                    : resolveSubtreeCodes(categoryCode);
            items = items.stream()
                    .filter(spec -> {
                        String category = spec.getCategory();
                        boolean ungrouped = category == null || category.isBlank();
                        if (UNGROUPED_CODE.equals(categoryCode)) {
                            return ungrouped;
                        }
                        return !ungrouped && subtreeCodes.contains(category);
                    })
                    .collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(spec -> contains(spec.getCode(), kw)
                            || contains(spec.getName(), kw)
                            || contains(spec.getCategory(), kw))
                    .collect(Collectors.toList());
        }
        items = items.stream()
                .sorted(Comparator.comparing(CapabilitySpec::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        int total = items.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        List<CapabilitySpec> pageItems = fromIndex <= toIndex ? items.subList(fromIndex, toIndex) : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", pageItems);
        result.put("total", total);
        return ApiResult.ok(result);
    }

    /**
     * 查询能力详情
     * @param code
     * @return
     */
    @GetMapping("/{code}")
    public ApiResult<CapabilitySpec> get(@PathVariable String code) {
        CapabilitySpec spec = catalog.get(code);
        if (spec == null) {
            return ApiResult.fail("能力不存在或已禁用: " + code);
        }
        return ApiResult.ok(spec);
    }

    /**
     * 新增数据库能力定义
     * @param spec
     * @return
     */
    @PostMapping
    public ApiResult<CapabilitySpec> create(@RequestBody CapabilitySpec spec) {
        if (spec == null || spec.getCode() == null || spec.getCode().isBlank()) {
            return ApiResult.fail("能力编码不能为空");
        }
        String code = spec.getCode().trim();
        if (definitionRepository.findByCode(code).isPresent()) {
            return ApiResult.fail("能力编码已存在: " + code);
        }
        String categoryError = validateCategory(spec.getCategory());
        if (categoryError != null) {
            return ApiResult.fail(categoryError);
        }
        String uploadError = validateUploadKbCode(spec.getAgentOverrides());
        if (uploadError != null) {
            return ApiResult.fail(uploadError);
        }
        String execError = validateExecutor(spec);
        if (execError != null) {
            return ApiResult.fail(execError);
        }
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode(code);
        entity.setName(spec.getName());
        entity.setDescription(spec.getDescription());
        entity.setDefinitionJson(toDefinitionJson(code, spec));
        entity.setEnabled(true);
        definitionRepository.create(entity);
        refreshCatalog(code);
        saveHistory(code, spec, "CREATE");
        return ApiResult.ok(catalog.get(code));
    }

    /**
     * 覆盖能力定义（字段合并，保留未提交配置）
     * @param code
     * @param spec
     * @param enabled 是否启用（可选，仅传参时变更启停状态）
     * @return
     */
    @PutMapping("/{code}")
    public ApiResult<CapabilitySpec> update(@PathVariable String code,
                                             @RequestBody CapabilitySpec spec,
                                             @RequestParam(required = false) Boolean enabled) {
        if (spec == null) {
            return ApiResult.fail("请求体不能为空");
        }
        if (definitionRepository.findByCode(code).isEmpty() && catalog.get(code) == null) {
            return ApiResult.fail("能力不存在: " + code);
        }
        String categoryError = validateCategory(spec.getCategory());
        if (categoryError != null) {
            return ApiResult.fail(categoryError);
        }
        // 数据库定义为唯一来源
        CapabilitySpec base = definitionRepository.findByCode(code)
                .map(this::parseDefinition)
                .orElse(null);
        if (base == null) {
            base = new CapabilitySpec();
            base.setCode(code);
        }
        if (spec.getName() != null) {
            base.setName(spec.getName());
        }
        if (spec.getDescription() != null) {
            base.setDescription(spec.getDescription());
        }
        if (spec.getCategory() != null) {
            base.setCategory(spec.getCategory());
        }
        // 全字段合并：非null覆盖，null保留基底（前端全量提交，清空场景传空对象/空串）
        // 提交版本号与现版本一致时自动递增补丁号，形成新版本；手动调整过则按提交值
        if (spec.getVersion() != null) {
            boolean versionUnchanged = spec.getVersion().equals(base.getVersion());
            base.setVersion(versionUnchanged
                    ? CapabilityCatalog.bumpPatchVersion(base.getVersion())
                    : spec.getVersion());
        }
        if (spec.getAgentCode() != null) {
            base.setAgentCode(spec.getAgentCode());
        }
        if (spec.getExecType() != null) {
            base.setExecType(spec.getExecType());
        }
        if (spec.getWorkflowCode() != null) {
            base.setWorkflowCode(spec.getWorkflowCode());
        }
        if (spec.getAgentOverrides() != null) {
            base.setAgentOverrides(spec.getAgentOverrides());
        }
        if (spec.getInputSchema() != null) {
            base.setInputSchema(spec.getInputSchema());
        }
        if (spec.getOutputSchema() != null) {
            base.setOutputSchema(spec.getOutputSchema());
        }
        if (spec.getInputSchemaContent() != null) {
            base.setInputSchemaContent(spec.getInputSchemaContent());
        }
        if (spec.getOutputSchemaContent() != null) {
            base.setOutputSchemaContent(spec.getOutputSchemaContent());
        }
        if (spec.getPromptTemplate() != null) {
            base.setPromptTemplate(spec.getPromptTemplate());
        }
        if (spec.getPromptTemplateContent() != null) {
            base.setPromptTemplateContent(spec.getPromptTemplateContent());
        }
        if (spec.getExecution() != null) {
            base.setExecution(spec.getExecution());
        }
        if (spec.getContract() != null) {
            base.setContract(spec.getContract());
        }
        if (spec.getContext() != null) {
            base.setContext(spec.getContext());
        }
        if (spec.getAudit() != null) {
            base.setAudit(spec.getAudit());
        }
        // 合并后校验（uploadKbCode与kbCodes可能分别来自基底与新提交）
        String uploadError = validateUploadKbCode(base.getAgentOverrides());
        if (uploadError != null) {
            return ApiResult.fail(uploadError);
        }
        String execError = validateExecutor(base);
        if (execError != null) {
            return ApiResult.fail(execError);
        }
        definitionRepository.override(code, toDefinitionMap(code, base));
        if (enabled != null) {
            if (enabled) {
                definitionRepository.enable(code);
            } else {
                definitionRepository.disable(code);
            }
        }
        refreshCatalog(code);
        saveHistory(code, base, "UPDATE");
        return ApiResult.ok(catalog.get(code));
    }

    /**
     * 删除数据库能力定义
     * @param code
     * @return
     */
    @DeleteMapping("/{code}")
    public ApiResult<Void> delete(@PathVariable String code) {
        definitionRepository.delete(code);
        // 历史快照随能力删除一并清理，避免同编码重建后残留旧版本
        historyRepository.deleteByCode(code);
        // 数据库定义为唯一来源，删除后同步注销目录中的能力
        catalog.unregister(code);
        return ApiResult.ok(null);
    }

    /**
     * 查询能力版本历史（按快照时间倒序）
     * @param code
     * @return
     */
    @GetMapping("/{code}/history")
    public ApiResult<List<CapabilityDefinitionHistory>> history(@PathVariable String code) {
        return ApiResult.ok(historyRepository.findByCode(code));
    }

    /**
     * 查询版本历史快照详情
     * @param code
     * @param historyId
     * @return
     */
    @GetMapping("/{code}/history/{historyId}")
    public ApiResult<CapabilityDefinitionHistory> historyDetail(@PathVariable String code,
                                                                @PathVariable Long historyId) {
        CapabilityDefinitionHistory history = historyRepository.findById(historyId).orElse(null);
        if (history == null || !code.equals(history.getCapabilityCode())) {
            return ApiResult.fail("版本记录不存在");
        }
        return ApiResult.ok(history);
    }

    /**
     * 回滚到指定历史版本（以快照内容生成新版本，当前版本号递增补丁号）
     * @param code
     * @param historyId
     * @return
     */
    @PostMapping("/{code}/rollback/{historyId}")
    public ApiResult<CapabilitySpec> rollback(@PathVariable String code,
                                              @PathVariable Long historyId) {
        CapabilityDefinitionHistory history = historyRepository.findById(historyId).orElse(null);
        if (history == null || !code.equals(history.getCapabilityCode())) {
            return ApiResult.fail("版本记录不存在");
        }
        CapabilitySpec base = definitionRepository.findByCode(code)
                .map(this::parseDefinition)
                .orElse(null);
        if (base == null) {
            return ApiResult.fail("能力不存在: " + code);
        }
        CapabilitySpec snapshot = parseDefinitionJson(history.getDefinitionJson());
        if (snapshot == null) {
            return ApiResult.fail("历史版本定义解析失败");
        }
        // 回滚生成新版本：取当前版本递增补丁号，避免历史版本号低于现版本被目录合并跳过
        snapshot.setVersion(base.getVersion() != null
                ? CapabilityCatalog.bumpPatchVersion(base.getVersion())
                : snapshot.getVersion());
        snapshot.setCode(code);
        definitionRepository.override(code, toDefinitionMap(code, snapshot));
        refreshCatalog(code);
        saveHistory(code, snapshot, "ROLLBACK");
        return ApiResult.ok(catalog.get(code));
    }

    /**
     * 查询能力调用记录（按调用时间倒序分页）
     * @param code 能力编码
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return
     */
    @GetMapping("/{code}/calls")
    public ApiResult<Map<String, Object>> calls(@PathVariable String code,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        List<CapabilityCallRecord> records = new java.util.ArrayList<>(callRepository.findByCapability(code));
        // 按调用开始时间倒序，时间为空的记录恒排末尾（reversed会翻转nullsLast语义，故显式比较）
        records.sort((a, b) -> {
            java.time.Instant ta = a.getStartedAt();
            java.time.Instant tb = b.getStartedAt();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        int total = records.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        List<CapabilityCallRecord> pageItems = fromIndex <= toIndex ? records.subList(fromIndex, toIndex) : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", pageItems);
        result.put("total", total);
        return ApiResult.ok(result);
    }

    /**
     * 查询能力分类树（各节点含挂载能力数）
     * @return
     */
    @GetMapping("/category/tree")
    public ApiResult<Map<String, Object>> categoryTree() {
        List<CategoryNode> nodes = buildCategoryTree(categoryRepository.findAll());
        long ungroupedCount = catalog.list().stream()
                .filter(spec -> spec.getCategory() == null || spec.getCategory().isBlank())
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("ungroupedCount", ungroupedCount);
        return ApiResult.ok(result);
    }

    /**
     * 新增能力分类节点
     * @param category
     * @return
     */
    @PostMapping("/category")
    public ApiResult<CapabilityCategory> createCategory(@RequestBody CapabilityCategory category) {
        if (category == null || category.getCode() == null || category.getCode().isBlank()) {
            return ApiResult.fail("分类编码不能为空");
        }
        if (category.getName() == null || category.getName().isBlank()) {
            return ApiResult.fail("分类名称不能为空");
        }
        String code = category.getCode().trim();
        if (!code.matches("[a-zA-Z0-9_-]{1,64}")) {
            return ApiResult.fail("分类编码仅支持字母/数字/下划线/中划线，长度不超过64");
        }
        if (categoryRepository.findByCode(code).isPresent()) {
            return ApiResult.fail("分类编码已存在: " + code);
        }
        if (category.getParentId() != null) {
            List<CapabilityCategory> all = categoryRepository.findAll();
            if (all.stream().noneMatch(item -> category.getParentId().equals(item.getId()))) {
                return ApiResult.fail("父分类节点不存在");
            }
        }
        category.setCode(code);
        categoryRepository.create(category);
        return ApiResult.ok(category);
    }

    /**
     * 更新能力分类节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param category
     * @return
     */
    @PutMapping("/category/{id}")
    public ApiResult<CapabilityCategory> updateCategory(@PathVariable Long id,
                                                        @RequestBody CapabilityCategory category) {
        if (category == null) {
            return ApiResult.fail("请求体不能为空");
        }
        List<CapabilityCategory> all = categoryRepository.findAll();
        CapabilityCategory exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            return ApiResult.fail("分类节点不存在");
        }
        if (category.getParentId() != null) {
            if (category.getParentId().equals(id)) {
                return ApiResult.fail("父节点不能选择自身");
            }
            if (collectDescendantIds(all, id).contains(category.getParentId())) {
                return ApiResult.fail("父节点不能选择自身子孙节点");
            }
            if (all.stream().noneMatch(item -> category.getParentId().equals(item.getId()))) {
                return ApiResult.fail("父分类节点不存在");
            }
        }
        exist.setName(category.getName() != null ? category.getName() : exist.getName());
        exist.setParentId(category.getParentId());
        exist.setSortNum(category.getSortNum() != null ? category.getSortNum() : 0);
        categoryRepository.update(exist);
        return ApiResult.ok(exist);
    }

    /**
     * 删除能力分类节点（存在子节点或挂载能力时禁止删除）
     * @param id
     * @return
     */
    @DeleteMapping("/category/{id}")
    public ApiResult<Void> deleteCategory(@PathVariable Long id) {
        List<CapabilityCategory> all = categoryRepository.findAll();
        CapabilityCategory exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            return ApiResult.fail("分类节点不存在");
        }
        if (all.stream().anyMatch(item -> id.equals(item.getParentId()))) {
            return ApiResult.fail("存在子分类节点，请先删除子节点");
        }
        long capabilityCount = catalog.list().stream()
                .filter(spec -> exist.getCode().equals(spec.getCategory()))
                .count();
        if (capabilityCount > 0) {
            return ApiResult.fail("该分类下挂载了" + capabilityCount + "个能力，请先调整能力归属");
        }
        categoryRepository.deleteById(id);
        return ApiResult.ok(null);
    }

    /**
     * 校验文件上传库必须属于检索知识库范围
     * @param overrides
     * @return 错误信息，null表示校验通过
     */
    private String validateUploadKbCode(AgentOverrides overrides) {
        if (overrides == null || overrides.getKnowledgeBase() == null) {
            return null;
        }
        String uploadKbCode = overrides.getKnowledgeBase().getUploadKbCode();
        if (uploadKbCode == null || uploadKbCode.isBlank()) {
            return null;
        }
        List<String> kbCodes = overrides.getKnowledgeBase().getKbCodes();
        if (kbCodes == null || !kbCodes.contains(uploadKbCode)) {
            return "文件上传库必须属于知识库检索范围(kbCodes): " + uploadKbCode;
        }
        return null;
    }

    /**
     * 校验执行体配置（工作流执行体为企业版能力，社区版拒绝配置）
     * @param spec
     * @return 错误信息，null表示校验通过
     */
    private String validateExecutor(CapabilitySpec spec) {
        if ("WORKFLOW".equalsIgnoreCase(spec.getExecType())) {
            return "工作流执行体为企业版能力，社区版不支持配置";
        }
        return null;
    }

    /**
     * 重新载入数据库定义并刷新目录
     * @param code
     */
    private void refreshCatalog(String code) {
        catalog.unregister(code);
        definitionRepository.findByCode(code).ifPresent(catalog::mergeDatabaseDefinition);
    }

    /**
     * 解析库内定义JSON为规格
     * @param entity
     * @return
     */
    private CapabilitySpec parseDefinition(CapabilityDefinition entity) {
        try {
            return CapabilityLoader.parseSpecFromJson(entity.getDefinitionJson());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析历史快照JSON为规格
     * @param definitionJson
     * @return
     */
    private CapabilitySpec parseDefinitionJson(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return null;
        }
        try {
            return CapabilityLoader.parseSpecFromJson(definitionJson);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存能力定义版本快照（失败仅告警，不影响主流程）
     * @param code
     * @param spec
     * @param operation
     */
    private void saveHistory(String code, CapabilitySpec spec, String operation) {
        try {
            CapabilityDefinitionHistory history = new CapabilityDefinitionHistory();
            history.setCapabilityCode(code);
            history.setVersion(spec.getVersion());
            history.setName(spec.getName());
            history.setOperation(operation);
            history.setDefinitionJson(toDefinitionJson(code, spec));
            historyRepository.save(history);
        } catch (Exception e) {
            log.warn("记录能力版本历史失败: code={}, operation={}", code, operation, e);
        }
    }

    /**
     * 规格转定义JSON字符串
     * @param code
     * @param spec
     * @return
     */
    private String toDefinitionJson(String code, CapabilitySpec spec) {
        try {
            return JSON_MAPPER.writeValueAsString(toDefinitionMap(code, spec));
        } catch (Exception e) {
            throw new IllegalStateException("能力定义序列化失败: " + code, e);
        }
    }

    /**
     * 规格转定义Map（保证code以路径变量为准）
     * @param code
     * @param spec
     * @return
     */
    private Map<String, Object> toDefinitionMap(String code, CapabilitySpec spec) {
        Map<String, Object> map = JSON_MAPPER.convertValue(spec, Map.class);
        map.put("code", code);
        return map;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /**
     * 解析分类子树（含自身）的全部节点编码
     * @param categoryCode
     * @return
     */
    private Set<String> resolveSubtreeCodes(String categoryCode) {
        Set<String> codes = new HashSet<>();
        List<CapabilityCategory> all = categoryRepository.findAll();
        CapabilityCategory start = all.stream()
                .filter(item -> categoryCode.equals(item.getCode()))
                .findFirst().orElse(null);
        if (start == null) {
            return codes;
        }
        Deque<CapabilityCategory> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            CapabilityCategory current = stack.pop();
            codes.add(current.getCode());
            for (CapabilityCategory item : all) {
                if (current.getId().equals(item.getParentId())) {
                    stack.push(item);
                }
            }
        }
        return codes;
    }

    /**
     * 收集指定节点的全部子孙节点ID
     * @param all
     * @param rootId
     * @return
     */
    private Set<Long> collectDescendantIds(List<CapabilityCategory> all, Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            for (CapabilityCategory item : all) {
                if (current.equals(item.getParentId()) && result.add(item.getId())) {
                    stack.push(item.getId());
                }
            }
        }
        return result;
    }

    /**
     * 校验能力归属的分类编码必须存在于分类树
     * @param category
     * @return 错误信息，null表示校验通过
     */
    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        if (categoryRepository.findByCode(category.trim()).isEmpty()) {
            return "分类节点不存在: " + category;
        }
        return null;
    }

    /**
     * 组装分类树（各节点含挂载能力数）
     * @param categories
     * @return
     */
    private List<CategoryNode> buildCategoryTree(List<CapabilityCategory> categories) {
        Map<String, Long> countByCode = catalog.list().stream()
                .filter(spec -> spec.getCategory() != null && !spec.getCategory().isBlank())
                .collect(Collectors.groupingBy(CapabilitySpec::getCategory, Collectors.counting()));
        Map<Long, CategoryNode> nodeById = new LinkedHashMap<>();
        for (CapabilityCategory category : categories) {
            CategoryNode node = new CategoryNode();
            node.setId(category.getId());
            node.setCode(category.getCode());
            node.setName(category.getName());
            node.setParentId(category.getParentId());
            node.setSortNum(category.getSortNum());
            node.setCapabilityCount(countByCode.getOrDefault(category.getCode(), 0L));
            node.setChildren(new ArrayList<>());
            nodeById.put(category.getId(), node);
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (CapabilityCategory category : categories) {
            CategoryNode node = nodeById.get(category.getId());
            CategoryNode parent = category.getParentId() == null ? null : nodeById.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                // 根节点或父节点缺失的孤儿节点统一挂在顶层
                roots.add(node);
            }
        }
        Comparator<CategoryNode> byOrder = Comparator.comparing(CategoryNode::getSortNum,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CategoryNode::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(byOrder);
        for (CategoryNode node : nodeById.values()) {
            node.getChildren().sort(byOrder);
        }
        return roots;
    }

    /**
     * 能力分类树节点
     */
    public static class CategoryNode {

        /**
         * 主键
         */
        private Long id;

        /**
         * 分类编码（创建时自定义，唯一且不可修改）
         */
        private String code;

        /**
         * 分类名称
         */
        private String name;

        /**
         * 父节点ID（NULL为根节点）
         */
        private Long parentId;

        /**
         * 排序号
         */
        private Integer sortNum;

        /**
         * 挂载能力数
         */
        private Long capabilityCount;

        /**
         * 子节点列表
         */
        private List<CategoryNode> children;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public Integer getSortNum() {
            return sortNum;
        }

        public void setSortNum(Integer sortNum) {
            this.sortNum = sortNum;
        }

        public Long getCapabilityCount() {
            return capabilityCount;
        }

        public void setCapabilityCount(Long capabilityCount) {
            this.capabilityCount = capabilityCount;
        }

        public List<CategoryNode> getChildren() {
            return children;
        }

        public void setChildren(List<CategoryNode> children) {
            this.children = children;
        }
    }
}
