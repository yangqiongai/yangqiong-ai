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
package com.yangqiongai.ai.memory.spi;

/**
 * 记忆去重决策
 * @author yangqiong
 */
public class DedupDecision {

    /**
     * 去重动作
     */
    private DedupAction action;

    /**
     * 冲突/重复的已有记忆ID
     */
    private Long existingMemoryId;

    /**
     * 相似度
     */
    private double similarity;

    public DedupDecision(DedupAction action, Long existingMemoryId, double similarity) {
        this.action = action;
        this.existingMemoryId = existingMemoryId;
        this.similarity = similarity;
    }

    public static DedupDecision add() {
        return new DedupDecision(DedupAction.ADD, null, 0.0);
    }

    public DedupAction getAction() {
        return action;
    }

    public Long getExistingMemoryId() {
        return existingMemoryId;
    }

    public double getSimilarity() {
        return similarity;
    }
}
