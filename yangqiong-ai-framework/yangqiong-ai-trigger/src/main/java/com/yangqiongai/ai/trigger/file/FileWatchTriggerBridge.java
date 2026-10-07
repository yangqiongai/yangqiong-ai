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
package com.yangqiongai.ai.trigger.file;

import com.yangqiongai.agent.harness.trigger.FileEventType;
import com.yangqiongai.agent.harness.trigger.FileWatchTrigger;
import com.yangqiongai.ai.agent.runtime.durable.RunLockStore;
import com.yangqiongai.ai.common.util.StringUtils;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文件监听触发桥
 * <p>
 * 复用引擎FileWatchTrigger对FILE类型触发规则做目录递归监听（企业增强，默认关闭）。
 * 多实例部署时以分布式锁选主单点监听，锁过期自动转移；
 * 锁存储不可用时退化为单实例语义（各实例均监听，靠幂等键去重兜底）。
 * 同一文件事件以（路径+类型+时间窗）幂等键去重，避免重复触发。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.agent.trigger.file", name = "enabled", havingValue = "true")
public class FileWatchTriggerBridge {

    private static final Logger log = LoggerFactory.getLogger(FileWatchTriggerBridge.class);

    private static final String FIRE_SOURCE = "FILE_WATCH";

    /**
     * 无去重窗口时的默认时间窗秒数
     */
    private static final long DEFAULT_DEDUP_WINDOW_SECONDS = 60L;

    /**
     * 分布式选主锁键（全局单点监听）
     */
    private static final String LEADER_LOCK_KEY = "trigger-file-watch-leader";

    /**
     * 锁TTL（到期未续约自动转移给其他实例）
     */
    private static final Duration LEADER_LOCK_TTL = Duration.ofSeconds(30);

    @Autowired
    private AgentTriggerRepository triggerRepository;

    @Autowired
    private AgentTriggerService triggerService;

    @Autowired
    private ObjectProvider<RunLockStore> lockStoreProvider;

    /**
     * 节点标识（锁属主）
     */
    private final String nodeId = "trigger-" + StringUtils.generateCompactId();

    /**
     * 运行中的监听器（triggerId→监听器）
     */
    private final Map<Long, FileWatchTrigger> watchers = new ConcurrentHashMap<>();

    /**
     * 监听维护循环：选主→同步本地监听器与启用规则一致
     */
    @Scheduled(fixedDelayString = "${ai.agent.trigger.file.sync-interval-ms:15000}",
            initialDelayString = "${ai.agent.trigger.file.sync-initial-delay-ms:10000}")
    public void syncWatchers() {
        RunLockStore lockStore = lockStoreProvider.getIfAvailable();
        boolean leader = true;
        if (lockStore != null) {
            leader = lockStore.tryLock(LEADER_LOCK_KEY, nodeId, LEADER_LOCK_TTL);
        }
        if (!leader) {
            // 非主实例停掉本地监听，锁转移后由新主重建
            closeAll();
            return;
        }
        if (lockStore != null) {
            lockStore.renew(LEADER_LOCK_KEY, nodeId, LEADER_LOCK_TTL);
        }
        reconcile();
    }

    /**
     * 处理单个文件事件（按规则时间窗生成幂等键后走统一触发入口）
     * @param trigger
     * @param path
     * @param type
     */
    void handleFileEvent(AgentTriggerEntity trigger, Path path, FileEventType type) {
        long windowSeconds = trigger.getDedupWindowSeconds() == null || trigger.getDedupWindowSeconds() <= 0
                ? DEFAULT_DEDUP_WINDOW_SECONDS : trigger.getDedupWindowSeconds();
        long bucket = Instant.now().toEpochMilli() / (windowSeconds * 1000L);
        String dedupKey = "file-" + AgentTriggerService.sha256(path + "|" + type + "|" + bucket);
        String payload = "文件变更(" + type + "): " + path;
        AgentTriggerFireResult result = triggerService.fire(trigger.getTriggerCode(), dedupKey, payload,
                FIRE_SOURCE);
        if (!result.isFired()) {
            log.debug("文件事件触发被拒绝: trigger={}, path={}, status={}",
                    trigger.getTriggerCode(), path, result.getStatus());
        }
    }

    /**
     * 本地监听器与启用中的FILE规则对齐
     * @return 当前监听器数量
     */
    int reconcile() {
        Set<Long> desired = new HashSet<>();
        for (AgentTriggerEntity trigger : triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)) {
            desired.add(trigger.getId());
            if (watchers.containsKey(trigger.getId())) {
                continue;
            }
            startWatcher(trigger);
        }
        // 关闭已删除或停用规则的监听器
        watchers.keySet().removeIf(id -> {
            if (desired.contains(id)) {
                return false;
            }
            closeWatcher(id);
            return true;
        });
        return watchers.size();
    }

    /**
     * 启动单个规则的目录监听（回调闭包捕获规则实体）
     * @param trigger
     */
    private void startWatcher(AgentTriggerEntity trigger) {
        try {
            Path root = Paths.get(trigger.getWatchDir()).toAbsolutePath().normalize();
            FileWatchTrigger watcher = new FileWatchTrigger(root,
                    (path, type) -> handleFileEvent(trigger, path, type),
                    500L, parseSuffixes(trigger));
            watcher.start();
            watchers.put(trigger.getId(), watcher);
            log.info("文件监听已启动: trigger={}, dir={}", trigger.getTriggerCode(), root);
        } catch (Exception e) {
            log.warn("文件监听启动失败: trigger={}, dir={}", trigger.getTriggerCode(), trigger.getWatchDir(), e);
        }
    }

    /**
     * 关闭全部监听器（失去主角色时调用）
     */
    private void closeAll() {
        if (watchers.isEmpty()) {
            return;
        }
        for (Long id : new HashSet<>(watchers.keySet())) {
            closeWatcher(id);
        }
        log.info("已释放文件监听(非主实例): node={}", nodeId);
    }

    /**
     * 关闭单个监听器
     * @param id
     */
    private void closeWatcher(Long id) {
        FileWatchTrigger watcher = watchers.remove(id);
        if (watcher != null) {
            watcher.close();
        }
    }

    /**
     * 解析后缀过滤配置（逗号分隔）
     * @param trigger
     * @return
     */
    private Set<String> parseSuffixes(AgentTriggerEntity trigger) {
        if (trigger.getFileSuffixes() == null || trigger.getFileSuffixes().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(trigger.getFileSuffixes().split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
