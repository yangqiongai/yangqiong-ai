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
package com.yangqiongai.ai.agent.harness.memory;

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Harness长期记忆桥接
 * <p>
 * 实现框架层引擎 L3 SPI，将记忆存储与检索委托给 ai-memory 的 LongTermMemoryManager，
 * 复用其合并、结构化提取、去重与衰变能力。引擎记忆工具调用时 userId 可能为 null，
 * 通过 SessionContext ThreadLocal 兜底解析当前用户。
 * </p>
 * @author yangqiong
 */
public class HarnessLongTermMemoryBridge implements AgentLongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(HarnessLongTermMemoryBridge.class);

    /**
     * 异步执行器线程序号
     */
    private static final AtomicInteger POOL_SEQ = new AtomicInteger(1);

    /**
     * 记忆合并异步执行器：合并含LLM调用与向量索引（秒级耗时），异步执行避免阻塞引擎事件流收尾
     */
    private static final ExecutorService MERGE_EXECUTOR = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r, "ltm-merge-" + POOL_SEQ.getAndIncrement());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * ai-memory 长期记忆管理器
     */
    private final LongTermMemoryManager longTermMemoryManager;

    /**
     * 检索记忆的 Token 预算上限
     */
    private final int retrieveMaxTokens;

    public HarnessLongTermMemoryBridge(LongTermMemoryManager longTermMemoryManager, int retrieveMaxTokens) {
        this.longTermMemoryManager = longTermMemoryManager;
        this.retrieveMaxTokens = retrieveMaxTokens;
    }

    /**
     * 存储记忆，委托 LongTermMemoryManager 合并到用户长期记忆
     * <p>
     * 异步执行：合并流程含LLM调用与向量索引（秒级耗时），同步执行会阻塞引擎事件流收尾，
     * 导致SSE流延迟关闭（前端对话完成按钮/加载态迟迟不结束）。
     * </p>
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     * @return
     */
    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        String resolvedUserId = resolveUserId(userId);
        if (resolvedUserId == null || content == null || content.isBlank()) {
            return;
        }
        // 调用线程捕获scope，异步线程重新绑定保证DB租户分区与模型配置解析正确
        String scopeId = ScopeContext.getScopeId();
        boolean bound = scopeId != null && !scopeId.isBlank();
        MERGE_EXECUTOR.execute(() -> {
            if (bound) {
                ScopeContext.setScopeId(scopeId);
            }
            try {
                longTermMemoryManager.mergeSessionSummary(resolvedUserId, sessionId, content);
            } catch (Exception e) {
                log.error("存储长期记忆失败: userId={}, sessionId={}", resolvedUserId, sessionId, e);
            } finally {
                if (bound) {
                    ScopeContext.clear();
                }
            }
        });
    }

    /**
     * 检索记忆，委托 LongTermMemoryManager 按查询文本加载相关记忆
     * <p>
     * 注意：limit 参数语义为返回条目数上限，而 loadByQuery 第三参数为 Token 预算，
     * 二者不可混用。此处始终使用 retrieveMaxTokens 作为 Token 预算，
     * loadByQuery 返回单个拼接字符串，以 singletonList 返回。
     * </p>
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    @Override
    public List<String> search(String userId, String query, int limit) {
        String resolvedUserId = resolveUserId(userId);
        if (resolvedUserId == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String result = longTermMemoryManager.loadByQuery(resolvedUserId, query, retrieveMaxTokens);
            if (result == null || result.isBlank()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(result);
        } catch (Exception e) {
            log.error("检索长期记忆失败: userId={}, query={}", resolvedUserId, truncate(query), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除记忆，当前 ai-memory 未暴露按 ID 删除入口，暂不支持
     * @param memoryId
     * @return
     */
    @Override
    public void delete(String memoryId) {
        log.warn("删除长期记忆暂不支持: memoryId={}", memoryId);
    }

    /**
     * 解析 userId，入参为空时从 SessionContext 兜底
     * @param userId
     * @return
     */
    private String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        try {
            return SessionContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 截断查询文本用于日志
     * @param text
     * @return
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}
