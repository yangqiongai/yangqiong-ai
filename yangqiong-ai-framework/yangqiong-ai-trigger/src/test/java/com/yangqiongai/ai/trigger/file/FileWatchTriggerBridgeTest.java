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

import com.yangqiongai.ai.agent.runtime.durable.RunLockStore;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件监听触发桥测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class FileWatchTriggerBridgeTest {

    @TempDir
    Path tempDir;

    @Mock
    private AgentTriggerRepository triggerRepository;

    @Mock
    private AgentTriggerService triggerService;

    @Mock
    private ObjectProvider<RunLockStore> lockStoreProvider;

    @Mock
    private RunLockStore lockStore;

    private FileWatchTriggerBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new FileWatchTriggerBridge();
        ReflectionTestUtils.setField(bridge, "triggerRepository", triggerRepository);
        ReflectionTestUtils.setField(bridge, "triggerService", triggerService);
        ReflectionTestUtils.setField(bridge, "lockStoreProvider", lockStoreProvider);
    }

    /**
     * 构建FILE规则
     */
    private AgentTriggerEntity fileTrigger(Long id, String code, String watchDir, int dedupWindowSeconds) {
        AgentTriggerEntity trigger = new AgentTriggerEntity();
        trigger.setId(id);
        trigger.setTriggerCode(code);
        trigger.setTriggerType(AgentTriggerEntity.TYPE_FILE);
        trigger.setAgentCode("agent-a");
        trigger.setWatchDir(watchDir);
        trigger.setEnabled(1);
        trigger.setDedupWindowSeconds(dedupWindowSeconds);
        return trigger;
    }

    @Test
    void reconcileStartsWatcherForEnabledFileTrigger() {
        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE))
                .thenReturn(List.of(fileTrigger(1L, "tg-file", tempDir.toString(), 0)));

        int watchers = bridge.reconcile();

        assertThat(watchers).isEqualTo(1);
    }

    @Test
    void reconcileStopsWatcherWhenTriggerDisabled() {
        AgentTriggerEntity trigger = fileTrigger(1L, "tg-file", tempDir.toString(), 0);
        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)).thenReturn(List.of(trigger));
        assertThat(bridge.reconcile()).isEqualTo(1);

        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)).thenReturn(List.of());
        int watchers = bridge.reconcile();

        assertThat(watchers).isEqualTo(0);
    }

    @Test
    void syncWatchersSkipsReconcileWhenNotLeader() {
        when(lockStoreProvider.getIfAvailable()).thenReturn(lockStore);
        when(lockStore.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        bridge.syncWatchers();

        verify(triggerRepository, never()).findEnabledByType(anyString());
        verify(lockStore, never()).renew(anyString(), anyString(), any());
    }

    @Test
    void syncWatchersRenewsLockWhenLeader() {
        when(lockStoreProvider.getIfAvailable()).thenReturn(lockStore);
        when(lockStore.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)).thenReturn(List.of());

        bridge.syncWatchers();

        verify(lockStore).renew(eq("trigger-file-watch-leader"), anyString(), any(Duration.class));
        verify(triggerRepository).findEnabledByType(AgentTriggerEntity.TYPE_FILE);
    }

    @Test
    void syncWatchersProceedsWithoutLockStore() {
        when(lockStoreProvider.getIfAvailable()).thenReturn(null);
        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)).thenReturn(List.of());

        bridge.syncWatchers();

        verify(triggerRepository).findEnabledByType(AgentTriggerEntity.TYPE_FILE);
    }

    @Test
    void handleFileEventFiresWithFileDedupKey() {
        AgentTriggerEntity trigger = fileTrigger(1L, "tg-file", tempDir.toString(), 0);
        Path file = tempDir.resolve("data.csv");
        when(triggerService.fire(eq("tg-file"), anyString(), anyString(), eq("FILE_WATCH")))
                .thenReturn(com.yangqiongai.ai.trigger.model.AgentTriggerFireResult.fired("task-1"));

        bridge.handleFileEvent(trigger, file, com.yangqiongai.agent.harness.trigger.FileEventType.CREATE);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(triggerService).fire(eq("tg-file"), keyCaptor.capture(),
                org.mockito.ArgumentMatchers.contains("data.csv"), eq("FILE_WATCH"));
        assertThat(keyCaptor.getValue()).startsWith("file-");
    }

    @Test
    void handleFileEventUsesStableKeyWithinWindow() throws Exception {
        AgentTriggerEntity trigger = fileTrigger(1L, "tg-file", tempDir.toString(), 60);
        Path file = tempDir.resolve("a.txt");
        lenient().when(triggerService.fire(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(com.yangqiongai.ai.trigger.model.AgentTriggerFireResult.fired("task-1"));

        bridge.handleFileEvent(trigger, file, com.yangqiongai.agent.harness.trigger.FileEventType.MODIFY);
        bridge.handleFileEvent(trigger, file, com.yangqiongai.agent.harness.trigger.FileEventType.MODIFY);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(triggerService, times(2)).fire(eq("tg-file"), keyCaptor.capture(), anyString(), anyString());
        // 同一时间窗内两次事件的幂等键一致，由服务层幂等判定拒绝重复触发
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void reconcileContinuesWhenWatcherStartFails() {
        // 目录不存在的规则启动失败不阻断其余规则
        AgentTriggerEntity bad = fileTrigger(1L, "tg-bad",
                tempDir.resolve("not-exists").toString(), 0);
        String goodDir = tempDir.resolve("good").toString();
        try {
            Files.createDirectory(tempDir.resolve("good"));
        } catch (Exception ignored) {
            // 目录创建失败不影响断言
        }
        AgentTriggerEntity good = fileTrigger(2L, "tg-good", goodDir, 0);
        when(triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_FILE)).thenReturn(List.of(bad, good));

        int watchers = bridge.reconcile();

        assertThat(watchers).isEqualTo(1);
    }
}
