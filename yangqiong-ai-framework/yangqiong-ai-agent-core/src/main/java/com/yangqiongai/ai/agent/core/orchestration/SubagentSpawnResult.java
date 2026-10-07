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
package com.yangqiongai.ai.agent.core.orchestration;

import java.util.concurrent.CompletableFuture;

/**
 * 子代理生成结果
 * @author yangqiong
 */
public class SubagentSpawnResult {

    /**
     * 生成ID
     */
    private final String spawnId;

    /**
     * 子代理名称
     */
    private final String agentName;

    /**
     * 执行模式
     */
    private final SpawnMode mode;

    /**
     * 同步结果
     */
    private final String result;

    /**
     * 后台任务Future
     */
    private final CompletableFuture<String> future;

    /**
     * 错误信息
     */
    private final String error;

    private SubagentSpawnResult(String spawnId, String agentName, SpawnMode mode,
                                String result, CompletableFuture<String> future, String error) {
        this.spawnId = spawnId;
        this.agentName = agentName;
        this.mode = mode;
        this.result = result;
        this.future = future;
        this.error = error;
    }

    /**
     * 创建同步结果
     * @param spawnId
     * @param agentName
     * @param result
     * @return
     */
    public static SubagentSpawnResult sync(String spawnId, String agentName, String result) {
        return new SubagentSpawnResult(spawnId, agentName, SpawnMode.SYNC, result, null, null);
    }

    /**
     * 创建后台结果
     * @param spawnId
     * @param agentName
     * @param future
     * @return
     */
    public static SubagentSpawnResult background(String spawnId, String agentName, CompletableFuture<String> future) {
        return new SubagentSpawnResult(spawnId, agentName, SpawnMode.BACKGROUND, null, future, null);
    }

    /**
     * 创建失败结果
     * @param spawnId
     * @param agentName
     * @param error
     * @return
     */
    public static SubagentSpawnResult failed(String spawnId, String agentName, String error) {
        return new SubagentSpawnResult(spawnId, agentName, SpawnMode.SYNC, null, null, error);
    }

    /**
     * 是否成功（同步模式且有结果）
     * @return
     */
    public boolean isSuccess() {
        return error == null && mode == SpawnMode.SYNC;
    }

    /**
     * 是否后台模式
     * @return
     */
    public boolean isBackground() {
        return mode == SpawnMode.BACKGROUND;
    }

    public String getSpawnId() {
        return spawnId;
    }

    public String getAgentName() {
        return agentName;
    }

    public SpawnMode getMode() {
        return mode;
    }

    public String getResult() {
        return result;
    }

    public CompletableFuture<String> getFuture() {
        return future;
    }

    public String getError() {
        return error;
    }

    /**
     * 生成模式
     */
    public enum SpawnMode {
        SYNC,
        BACKGROUND
    }
}
