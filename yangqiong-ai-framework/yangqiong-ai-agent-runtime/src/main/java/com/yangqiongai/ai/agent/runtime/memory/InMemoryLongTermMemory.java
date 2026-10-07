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
package com.yangqiongai.ai.agent.runtime.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 长期记忆内存默认实现
 * <p>
 * 按userId桶隔离存储记忆条目，跨用户数据完全隔离；
 * 无向量索引时的合理降级：对query分词后做大小写不敏感的子串匹配。
 * 作为L3默认实现，外部可替换为向量数据库/知识图谱等持久化实现。
 * </p>
 * @author yangqiong
 */
public class InMemoryLongTermMemory implements AgentLongTermMemory {

    /**
     * 最大记忆条目数，超限淘汰创建时间最旧条目，防止内存无界增长
     */
    public static final int MAX_ENTRIES = 10000;

    /**
     * 按用户桶隔离的记忆条目
     */
    private final Map<String, List<Entry>> storeByUser = new ConcurrentHashMap<>();

    /**
     * 按记忆ID索引，用于快速删除
     */
    private final Map<String, Entry> indexById = new ConcurrentHashMap<>();

    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return;
        }
        String bucket = userId != null ? userId : "";
        String id = UUID.randomUUID().toString();
        Entry entry = new Entry(id, bucket, content, metadata);
        indexById.put(id, entry);
        storeByUser.computeIfAbsent(bucket, k -> new CopyOnWriteArrayList<>()).add(entry);
        evictOverflow(entry);
    }

    /**
     * 容量超限时淘汰创建时间最旧条目并保持双索引一致
     * @param protectedEntry 本次保存的条目，禁止作为淘汰对象
     */
    private void evictOverflow(Entry protectedEntry) {
        if (indexById.size() <= MAX_ENTRIES) {
            return;
        }
        indexById.values().stream()
                .filter(e -> e != protectedEntry)
                .min(Comparator.comparingLong(e -> e.createdAt))
                .ifPresent(victim -> {
                    indexById.remove(victim.id, victim);
                    List<Entry> entries = storeByUser.get(victim.userId);
                    if (entries != null) {
                        entries.remove(victim);
                        if (entries.isEmpty()) {
                            storeByUser.remove(victim.userId, entries);
                        }
                    }
                });
    }

    @Override
    public List<String> search(String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Entry> entries = storeByUser.get(userId != null ? userId : "");
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        int topK = limit > 0 ? limit : entries.size();
        return entries.stream()
                .map(e -> new Scored(e, score(e, tokens)))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
                .limit(topK)
                .map(s -> s.entry.content)
                .toList();
    }

    @Override
    public void delete(String memoryId) {
        if (memoryId == null) {
            return;
        }
        Entry entry = indexById.remove(memoryId);
        if (entry == null) {
            return;
        }
        List<Entry> entries = storeByUser.get(entry.userId);
        if (entries != null) {
            entries.removeIf(e -> e.id.equals(memoryId));
        }
    }

    /**
     * 简单分词：按非字母数字字符切分并小写
     * @param query
     * @return
     */
    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.length() > 0) {
                tokens.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    /**
     * 计算记忆条目对查询词的命中数
     * @param entry
     * @param tokens
     * @return
     */
    private int score(Entry entry, List<String> tokens) {
        String text = entry.content.toLowerCase();
        int hits = 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 记忆条目
     */
    private static final class Entry {

        /**
         * 记忆ID
         */
        final String id;

        /**
         * 所属用户桶
         */
        final String userId;

        /**
         * 记忆内容
         */
        final String content;

        /**
         * 元数据
         */
        final Map<String, Object> metadata;

        /**
         * 创建时间戳，用于容量超限时淘汰最旧条目
         */
        final long createdAt;

        Entry(String id, String userId, String content, Map<String, Object> metadata) {
            this.id = id;
            this.userId = userId;
            this.content = content;
            this.metadata = metadata;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * 带分值的检索命中
     */
    private static final class Scored {

        /**
         * 命中的记忆条目
         */
        final Entry entry;

        /**
         * 命中分值
         */
        final int score;

        Scored(Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
