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
package com.yangqiongai.ai.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 保存工作流定义请求
 * @author yangqiong
 */
@Data
public class WorkflowDefinitionSaveRequest {

    @NotBlank(message = "工作流定义名称不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{2,63}$", message = "名称须以字母开头，3-64位字母数字下划线横杠")
    private String definitionName;

    @Size(max = 128, message = "显示名称长度不能超过128")
    private String displayName;

    @Size(max = 1024, message = "描述长度不能超过1024")
    private String description;

    @Pattern(regexp = "^(ORCHESTRATION|REPORT|DATA_PIPELINE|CUSTOM)?$", message = "分类须为ORCHESTRATION/REPORT/DATA_PIPELINE/CUSTOM")
    private String category;

    private Map<String, Object> definition;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;
}
