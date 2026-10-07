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

import com.yangqiongai.ai.agent.skill.model.SkillUsageInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillUsageRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能使用量统计
 * @author yangqiong
 */
@Tag(name = "技能使用量统计接口")
@RestController
@RequestMapping("/api/agent/skill/usage")
public class SkillUsageController {

    @Autowired
    private SkillUsageRepository skillUsageRepository;

    /**
     * 获取技能使用量详情
     * @param skillId
     * @return
     */
    @Operation(summary = "获取技能使用量详情")
    @GetMapping("/{skillId}")
    public ApiResult<SkillUsageInfo> getBySkillId(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId) {
        SkillUsageInfo info = skillUsageRepository.getBySkillId(skillId);
        if (info == null) {
            return ApiResult.ok(null);
        }
        return ApiResult.ok(info);
    }

    /**
     * 获取使用量排行
     * @param limit
     * @return
     */
    @Operation(summary = "获取使用量排行")
    @GetMapping("/top")
    public ApiResult<List<SkillUsageInfo>> listTop(
            @Parameter(name = "limit", description = "返回数量") @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(skillUsageRepository.listTopByUseCount(limit));
    }

    /**
     * 设置技能生命周期状态
     * @param skillId
     * @param body
     * @return
     */
    @Operation(summary = "设置技能生命周期状态")
    @PutMapping("/{skillId}/state")
    public ApiResult<Void> setState(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @RequestBody Map<String, String> body) {
        String state = body.get("state");
        if (state == null || state.isBlank()) {
            return ApiResult.fail(400, "state不能为空");
        }
        List<String> validStates = List.of("DRAFT", "ACTIVE", "STALE", "ARCHIVED");
        if (!validStates.contains(state)) {
            return ApiResult.fail(400, "state只能为: " + String.join(", ", validStates));
        }
        if (!skillUsageRepository.setState(skillId, state)) {
            return ApiResult.fail(404, "技能使用记录不存在: " + skillId);
        }
        return ApiResult.ok();
    }

    /**
     * 设置技能置顶
     * @param skillId
     * @param body
     * @return
     */
    @Operation(summary = "设置技能置顶")
    @PutMapping("/{skillId}/pinned")
    public ApiResult<Void> setPinned(
            @Parameter(name = "skillId", description = "技能ID") @PathVariable String skillId,
            @RequestBody Map<String, Boolean> body) {
        Boolean pinned = body.get("pinned");
        if (pinned == null) {
            return ApiResult.fail(400, "pinned不能为空");
        }
        if (!skillUsageRepository.setPinned(skillId, pinned)) {
            return ApiResult.fail(404, "技能使用记录不存在: " + skillId);
        }
        return ApiResult.ok();
    }
}
