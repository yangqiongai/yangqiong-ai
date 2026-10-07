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
package com.yangqiongai.ai.memory.extract;

import java.time.LocalDateTime;

/**
 * 结构化记忆提取结果
 * @author yangqiong
 */
public class ExtractedMemory {

    /**
     * 记忆类型(FACT/PREFERENCE/CONSTRAINT/PROFILE/EVENT)
     */
    private String memoryType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 事实/偏好的键（如"项目截止日期"、"饮食习惯"）
     */
    private String key;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 置信度 0-1
     */
    private Double confidence;

    /**
     * 生效时间
     */
    private LocalDateTime validFrom;

    /**
     * 失效时间
     */
    private LocalDateTime validUntil;

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }
}
