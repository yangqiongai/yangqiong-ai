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
package com.yangqiongai.ai.rag.retriever;

import com.yangqiongai.ai.common.rag.DocumentMetadataHandler;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.rag.config.KnowledgeBaseVersionResolver;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上下文窗口扩展器
 * @author yangqiong
 */
@Service
public class ContextWindowExpander {

    private static final Logger log = LoggerFactory.getLogger(ContextWindowExpander.class);

    @Autowired
    private RagProperties ragProperties;

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    private DocumentMetadataHandler documentMetadataHandler;

    @Autowired
    private KnowledgeBaseVersionResolver knowledgeBaseVersionResolver;

    /**
     * 单文档上下文扩展
     * @param docId
     * @param query
     * @param maxChars
     * @return
     */
    public String expandSingleDoc(String docId, String query, int maxChars) {
        if (!StringUtils.hasText(docId)) {
            return "";
        }

        IngestDocument document = fetchDocumentByDocId(docId);
        List<SliceRecord> orderedChunks = fetchOrderedParentChunks(docId);
        if (orderedChunks.isEmpty()) {
            return "";
        }
        return buildSingleParentContext(document, orderedChunks, query, maxChars);
    }

    /**
     * 构建有序文档上下文
     * @param docId
     * @return
     */
    public String buildOrderedContext(String docId) {
        List<SliceRecord> parentChunks = fetchOrderedParentChunks(docId);
        return concatenateChunkTexts(parentChunks.stream()
                .map(SliceRecord::getContent)
                .toList());
    }

    /**
     * 多文档上下文组装（增强版：预算分配 + 邻居扩展 + 文档名/摘要注入）
     * @param candidates
     * @param maxChars
     * @return
     */
    public String assembleContext(List<ChunkCandidate> candidates, int maxChars) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        List<DocAggregation> parentAggs = new ArrayList<>();
        List<DocAggregation> sliceAggs = new ArrayList<>();
        List<String> rawTexts = new ArrayList<>();

        for (ChunkCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getText())) {
                continue;
            }

            if (candidate.isChildHit() && StringUtils.hasText(candidate.getParentId())) {
                parentAggs.add(new DocAggregation(
                        candidate.getDocId(), candidate.getParentId(), candidate.getScore(),
                        candidate.getPrevSliceId(), candidate.getNextSliceId()));
                continue;
            }

            if (StringUtils.hasText(candidate.getDocId()) && StringUtils.hasText(candidate.getSliceId())) {
                sliceAggs.add(new DocAggregation(
                        candidate.getDocId(), candidate.getSliceId(), candidate.getScore(),
                        candidate.getPrevSliceId(), candidate.getNextSliceId()));
            } else {
                rawTexts.add(candidate.getText().trim());
            }
        }

        List<RenderedFragment> fragments = new ArrayList<>();
        fragments.addAll(buildParentFragments(parentAggs));
        fragments.addAll(buildSliceFragments(sliceAggs));
        return renderFragments(fragments, rawTexts, maxChars);
    }

    /**
     * 从查询语句中提取关键词集合
     * @param query
     * @return
     */
    Set<String> parseKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        Set<String> keywords = new LinkedHashSet<>();
        String normalized = query.trim().toLowerCase(Locale.ROOT);

        for (String token : normalized.split("[\\p{Punct}\\p{Space}\\p{IsPunctuation}]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= ragProperties.getContextExpand().getMinKeywordLen()) {
                keywords.add(trimmed);
            }
        }

        String compact = normalized.replaceAll("\\s+", "");
        if (compact.length() >= ragProperties.getContextExpand().getMinKeywordLen() && compact.length() <= 20) {
            keywords.add(compact);
        }

        return keywords;
    }

    /**
     * 定位文本中与关键词相关的区间
     * @param text
     * @param keywords
     * @param maxChars
     * @return
     */
    List<TextRegion> locateRelevantRegions(String text, Set<String> keywords, int maxChars) {
        int radius = Math.max(ragProperties.getContextExpand().getMinRadius(), (int) (maxChars * ragProperties.getContextExpand().getAnchorRadiusRatio()));
        String lowerText = text.toLowerCase(Locale.ROOT);

        List<HitPosition> hits = new ArrayList<>();
        for (String kw : keywords) {
            int from = 0;
            while (from < lowerText.length()) {
                int pos = lowerText.indexOf(kw, from);
                if (pos < 0) {
                    break;
                }
                hits.add(new HitPosition(pos, kw.length()));
                from = pos + kw.length();
            }
        }

        if (hits.isEmpty()) {
            return List.of();
        }

        hits.sort(Comparator.comparingInt(HitPosition::getOffset));

        int gapThreshold = radius / 2;
        List<List<HitPosition>> clusters = new ArrayList<>();
        List<HitPosition> current = new ArrayList<>();
        current.add(hits.get(0));

        for (int i = 1; i < hits.size(); i++) {
            int gap = hits.get(i).getOffset() - hits.get(i - 1).getOffset();
            if (gap <= gapThreshold) {
                current.add(hits.get(i));
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(hits.get(i));
            }
        }
        clusters.add(current);

        List<TextRegion> regions = new ArrayList<>();
        for (List<HitPosition> cluster : clusters) {
            int minOffset = cluster.stream().mapToInt(HitPosition::getOffset).min().orElse(0);
            int maxEnd = cluster.stream().mapToInt(h -> h.getOffset() + h.getLength()).max().orElse(0);
            int start = Math.max(0, minOffset - radius);
            int end = Math.min(text.length(), maxEnd + radius);
            regions.add(new TextRegion(start, end, cluster.size()));
        }

        regions.sort(Comparator
                .comparingInt(TextRegion::getWeight).reversed()
                .thenComparingInt(TextRegion::getStart));

        if (regions.size() > ragProperties.getContextExpand().getMaxClusterLimit()) {
            regions = new ArrayList<>(regions.subList(0, ragProperties.getContextExpand().getMaxClusterLimit()));
        }

        return regions;
    }

    /**
     * 合并重叠或相邻的文本区间
     * @param regions
     * @return
     */
    List<TextRegion> mergeAdjacentRegions(List<TextRegion> regions) {
        if (regions.isEmpty()) {
            return regions;
        }

        List<TextRegion> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparingInt(TextRegion::getStart));

        List<TextRegion> merged = new ArrayList<>();
        TextRegion carrying = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            TextRegion next = sorted.get(i);
            if (next.getStart() <= carrying.getEnd()) {
                carrying = new TextRegion(
                        carrying.getStart(),
                        Math.max(carrying.getEnd(), next.getEnd()),
                        carrying.getWeight() + next.getWeight()
                );
            } else {
                merged.add(carrying);
                carrying = next;
            }
        }
        merged.add(carrying);

        return merged;
    }

    /**
     * 按区间从原文中提取内容，控制在预算内
     * @param text
     * @param regions
     * @param maxChars
     * @return
     */
    String extractWithinBudget(String text, List<TextRegion> regions, int maxChars) {
        StringBuilder result = new StringBuilder();
        for (TextRegion region : regions) {
            String segment = text.substring(region.getStart(), region.getEnd());
            int needed = segment.length() + (result.length() > 0 ? 2 : 0);

            if (result.length() + needed > maxChars) {
                int remaining = maxChars - result.length();
                if (remaining > 10) {
                    if (result.length() > 0) {
                        result.append("\n\n");
                        remaining -= 2;
                    }
                    result.append(segment, 0, Math.min(segment.length(), remaining));
                }
                break;
            }

            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append(segment);
        }
        return result.toString();
    }

    // ========== 单文档上下文构建 ==========

    /**
     * 构建单文档父块上下文
     * @param document
     * @param orderedChunks
     * @param query
     * @param maxChars
     * @return
     */
    private String buildSingleParentContext(IngestDocument document,
                                            List<SliceRecord> orderedChunks,
                                            String query,
                                            int maxChars) {
        if (canBypassAsSmallDoc(document, orderedChunks.size())) {
            int totalContentLen = sumChunkContentLengths(orderedChunks.stream()
                    .map(SliceRecord::getContent).toList());
            if (totalContentLen > 0 && totalContentLen <= maxChars) {
                return renderDocumentWithMeta(document, orderedChunks, true, maxChars);
            }
        }

        List<SliceRecord> focused = pickFocusedChunks(orderedChunks, query);
        if (!focused.isEmpty()) {
            return renderDocumentWithMeta(document, focused, false, maxChars);
        }
        return renderDocumentWithMeta(document, orderedChunks, false, maxChars);
    }

    /**
     * 构建单文档切片上下文
     * @param document
     * @param orderedSlices
     * @param query
     * @param maxChars
     * @return
     */
    private String buildSingleSliceContext(IngestDocument document,
                                           List<SliceRecord> orderedSlices,
                                           String query,
                                           int maxChars) {
        if (canBypassAsSmallDoc(document, orderedSlices.size())) {
            int totalContentLen = sumChunkContentLengths(orderedSlices.stream()
                    .map(SliceRecord::getContent).toList());
            if (totalContentLen > 0 && totalContentLen <= maxChars) {
                return renderSliceDocWithMeta(document, orderedSlices, true, maxChars);
            }
        }

        List<SliceRecord> focused = pickFocusedSlices(orderedSlices, query);
        if (!focused.isEmpty()) {
            return renderSliceDocWithMeta(document, focused, false, maxChars);
        }
        return renderSliceDocWithMeta(document, orderedSlices, false, maxChars);
    }

    // ========== 多文档预算分配与邻居扩展 ==========

    /**
     * 构建父块命中文档的渲染片段
     * @param aggs
     * @return
     */
    private List<RenderedFragment> buildParentFragments(List<DocAggregation> aggs) {
        if (aggs.isEmpty()) {
            return List.of();
        }

        Map<String, ParentDocBucket> bucketMap = groupParentHitsByDoc(aggs);
        List<ParentDocBucket> buckets = bucketMap.values().stream()
                .sorted(Comparator.comparingDouble(ParentDocBucket::topScore).reversed())
                .toList();
        if (buckets.isEmpty()) {
            return List.of();
        }

        Map<String, SliceRecord> chunkLookup = loadParentChunkLookup(
                buckets.stream().flatMap(b -> b.chunkEntries.keySet().stream())
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        attachDocumentToParentBuckets(buckets);
        prepareParentNeighborContext(buckets, chunkLookup);
        assignParentAllocationTier(buckets);
        fillParentFullContextForEntireTier(buckets);

        return buckets.stream()
                .filter(b -> b.tier != AllocationTier.DISCARDED && !b.renderEntries.isEmpty())
                .sorted(Comparator.comparingDouble(ParentDocBucket::topScore).reversed())
                .map(b -> new RenderedFragment(b.docId, b.topScore(), renderParentBucket(b)))
                .filter(f -> StringUtils.hasText(f.content))
                .toList();
    }

    /**
     * 构建切片命中文档的渲染片段
     * @param aggs
     * @return
     */
    private List<RenderedFragment> buildSliceFragments(List<DocAggregation> aggs) {
        if (aggs.isEmpty()) {
            return List.of();
        }

        Map<String, SliceDocBucket> bucketMap = groupSliceHitsByDoc(aggs);
        List<SliceDocBucket> buckets = bucketMap.values().stream()
                .sorted(Comparator.comparingDouble(SliceDocBucket::topScore).reversed())
                .toList();
        if (buckets.isEmpty()) {
            return List.of();
        }

        attachDocumentToSliceBuckets(buckets);
        hydrateSliceOrderedSlices(buckets);
        prepareSliceNeighborContext(buckets);
        assignSliceAllocationTier(buckets);
        fillSliceFullContextForEntireTier(buckets);

        return buckets.stream()
                .filter(b -> b.tier != AllocationTier.DISCARDED && !b.renderEntries.isEmpty())
                .sorted(Comparator.comparingDouble(SliceDocBucket::topScore).reversed())
                .map(b -> new RenderedFragment(b.docId, b.topScore(), renderSliceBucket(b)))
                .filter(f -> StringUtils.hasText(f.content))
                .toList();
    }

    // ========== 文档聚合 ==========

    /**
     * 将父块命中按文档聚合
     * @param aggs
     * @return
     */
    private Map<String, ParentDocBucket> groupParentHitsByDoc(List<DocAggregation> aggs) {
        Map<String, ParentDocBucket> result = new LinkedHashMap<>();
        for (DocAggregation agg : aggs) {
            if (!StringUtils.hasText(agg.chunkId)) {
                continue;
            }
            ParentDocBucket bucket = result.computeIfAbsent(
                    agg.docId, k -> new ParentDocBucket(agg.docId));
            bucket.topScore = Math.max(bucket.topScore, agg.score);
            bucket.chunkEntries.computeIfAbsent(agg.chunkId,
                    k -> new ChunkRef(agg.chunkId, agg.score, "matched"));
        }
        return result;
    }

    /**
     * 将切片命中按文档聚合
     * @param aggs
     * @return
     */
    private Map<String, SliceDocBucket> groupSliceHitsByDoc(List<DocAggregation> aggs) {
        Map<String, SliceDocBucket> result = new LinkedHashMap<>();
        for (DocAggregation agg : aggs) {
            if (!StringUtils.hasText(agg.docId) || !StringUtils.hasText(agg.chunkId)) {
                continue;
            }
            SliceDocBucket bucket = result.computeIfAbsent(
                    agg.docId, k -> new SliceDocBucket(agg.docId));
            bucket.topScore = Math.max(bucket.topScore, agg.score);
            bucket.matchedSliceIds.add(agg.chunkId);
        }
        return result;
    }

    // ========== 邻居扩展 ==========

    /**
     * 为父块文档桶准备邻居上下文
     * @param buckets
     * @param chunkLookup
     */
    private void prepareParentNeighborContext(List<ParentDocBucket> buckets,
                                              Map<String, SliceRecord> chunkLookup) {
        for (ParentDocBucket bucket : buckets) {
            Set<String> neighborIds = new LinkedHashSet<>();
            for (ChunkRef ref : bucket.chunkEntries.values()) {
                SliceRecord chunk = chunkLookup.get(ref.chunkId);
                if (chunk == null) {
                    continue;
                }
                // 利用sortNum查找真正的前后邻居
                collectNeighborIds(neighborIds, chunk, chunkLookup, bucket.chunkEntries.keySet());
            }

            Map<String, SliceRecord> neighborChunks = loadParentChunkLookup(neighborIds);
            for (SliceRecord neighbor : neighborChunks.values()) {
                bucket.chunkEntries.computeIfAbsent(neighbor.getSliceId(),
                        k -> new ChunkRef(neighbor.getSliceId(), 0, "neighbor"));
            }

            bucket.partialEntries = bucket.chunkEntries.values().stream()
                    .sorted(Comparator.comparing(ChunkRef::getChunkId))
                    .toList();
            bucket.partialCharCount = estimateParentBucketChars(bucket.document, bucket.partialEntries, chunkLookup);
        }
    }

    /**
     * 为切片文档桶准备邻居上下文
     * @param buckets
     */
    private void prepareSliceNeighborContext(List<SliceDocBucket> buckets) {
        for (SliceDocBucket bucket : buckets) {
            if (bucket.orderedSlices.isEmpty()) {
                continue;
            }

            Map<String, Integer> sliceIndexMap = new LinkedHashMap<>();
            for (int i = 0; i < bucket.orderedSlices.size(); i++) {
                SliceRecord slice = bucket.orderedSlices.get(i);
                if (StringUtils.hasText(slice.getSliceId())) {
                    sliceIndexMap.putIfAbsent(slice.getSliceId(), i);
                }
            }

            Set<Integer> matchedIndexes = new LinkedHashSet<>();
            for (String sliceId : bucket.matchedSliceIds) {
                Integer idx = sliceIndexMap.get(sliceId);
                if (idx != null) {
                    matchedIndexes.add(idx);
                }
            }
            if (matchedIndexes.isEmpty()) {
                continue;
            }

            Set<Integer> selectedIndexes = new LinkedHashSet<>();
            for (Integer idx : matchedIndexes) {
                selectedIndexes.add(idx);
                for (int offset = 1; offset <= ragProperties.getContextExpand().getNeighborSpan(); offset++) {
                    if (idx - offset >= 0) {
                        selectedIndexes.add(idx - offset);
                    }
                    if (idx + offset < bucket.orderedSlices.size()) {
                        selectedIndexes.add(idx + offset);
                    }
                }
            }

            List<SliceRef> partial = new ArrayList<>();
            for (int i = 0; i < bucket.orderedSlices.size(); i++) {
                if (!selectedIndexes.contains(i)) {
                    continue;
                }
                SliceRecord slice = bucket.orderedSlices.get(i);
                String origin = matchedIndexes.contains(i) ? "matched" : "neighbor";
                partial.add(new SliceRef(slice, i + 1, origin));
            }
            bucket.partialEntries = partial;
            bucket.partialCharCount = estimateSliceBucketChars(bucket.document, partial);
        }
    }

    /**
     * 利用sortNum查找真正的前后邻居
     * @param neighborIds
     * @param currentChunk
     * @param chunkLookup
     * @param existingIds
     */
    private void collectNeighborIds(Set<String> neighborIds, SliceRecord currentChunk,
                                     Map<String, SliceRecord> chunkLookup, Set<String> existingIds) {
        if (currentChunk == null || !StringUtils.hasText(currentChunk.getSliceId())) {
            return;
        }
        Integer currentSortNum = currentChunk.getSortNum();
        String currentDocId = currentChunk.getDocId();
        if (currentSortNum == null || !StringUtils.hasText(currentDocId)) {
            return;
        }

        // 在chunkLookup中查找同文档、sortNum±1的邻居
        for (SliceRecord candidate : chunkLookup.values()) {
            if (candidate == null || !currentDocId.equals(candidate.getDocId())) {
                continue;
            }
            Integer candidateSortNum = candidate.getSortNum();
            if (candidateSortNum == null) {
                continue;
            }
            int diff = candidateSortNum - currentSortNum;
            // 只找前一个和后一个邻居
            if (Math.abs(diff) == 1) {
                String neighborId = candidate.getSliceId();
                if (StringUtils.hasText(neighborId) && !existingIds.contains(neighborId)) {
                    neighborIds.add(neighborId);
                }
            }
        }
    }

    // ========== 预算分配 ==========

    /**
     * 为父块文档桶分配预算层级
     * @param buckets
     */
    private void assignParentAllocationTier(List<ParentDocBucket> buckets) {
        int usedChars = 0;

        for (ParentDocBucket bucket : buckets) {
            int partialChars = bucket.partialCharCount > 0
                    ? bucket.partialCharCount
                    : estimateParentBucketChars(bucket.document, bucket.partialEntries, Map.of());
            if (partialChars <= 0 || usedChars + partialChars > Integer.MAX_VALUE / 2) {
                bucket.tier = AllocationTier.DISCARDED;
                continue;
            }

            int chunkCount = bucket.document != null && bucket.document.getChunkCount() != null
                    ? bucket.document.getChunkCount()
                    : bucket.partialEntries.size();
            if (canBypassAsSmallDoc(bucket.document, chunkCount)) {
                int fullChars = estimateFullDocumentChars(bucket.document);
                if (fullChars > 0 && usedChars + fullChars <= Integer.MAX_VALUE / 2) {
                    bucket.tier = AllocationTier.ENTIRE;
                    bucket.allocatedChars = fullChars;
                    usedChars += fullChars;
                    continue;
                }
            }

            bucket.tier = AllocationTier.EXCERPT;
            bucket.allocatedChars = partialChars;
            usedChars += partialChars;
        }

        log.debug("父块预算分配完成, 文档数: {}", buckets.size());
    }

    /**
     * 为切片文档桶分配预算层级
     * @param buckets
     */
    private void assignSliceAllocationTier(List<SliceDocBucket> buckets) {
        int usedChars = 0;

        for (SliceDocBucket bucket : buckets) {
            int partialChars = bucket.partialCharCount > 0
                    ? bucket.partialCharCount
                    : estimateSliceBucketChars(bucket.document, bucket.partialEntries);
            if (partialChars <= 0 || usedChars + partialChars > Integer.MAX_VALUE / 2) {
                bucket.tier = AllocationTier.DISCARDED;
                continue;
            }

            if (canBypassAsSmallDoc(bucket.document, bucket.orderedSlices.size())) {
                int fullChars = estimateFullDocumentChars(bucket.document);
                if (fullChars > 0 && usedChars + fullChars <= Integer.MAX_VALUE / 2) {
                    bucket.tier = AllocationTier.ENTIRE;
                    bucket.allocatedChars = fullChars;
                    usedChars += fullChars;
                    continue;
                }
            }

            bucket.tier = AllocationTier.EXCERPT;
            bucket.allocatedChars = partialChars;
            usedChars += partialChars;
        }

        log.debug("切片预算分配完成, 文档数: {}", buckets.size());
    }

    // ========== 全文填充 ==========

    /**
     * 为ENTIRE层级的父块桶加载全文
     * @param buckets
     */
    private void fillParentFullContextForEntireTier(List<ParentDocBucket> buckets) {
        for (ParentDocBucket bucket : buckets) {
            if (bucket.tier != AllocationTier.ENTIRE || bucket.document == null) {
                continue;
            }
            List<SliceRecord> allChunks = fetchOrderedParentChunks(bucket.docId);
            List<ChunkRef> fullRefs = allChunks.stream()
                    .map(c -> new ChunkRef(c.getSliceId(), 0, "full_document"))
                    .toList();
            bucket.renderEntries = fullRefs;
        }

        for (ParentDocBucket bucket : buckets) {
            if (bucket.tier == AllocationTier.ENTIRE) {
                // renderEntries already set above
            } else if (bucket.tier == AllocationTier.EXCERPT) {
                bucket.renderEntries = bucket.partialEntries;
            }
        }
    }

    /**
     * 为ENTIRE层级的切片桶加载全文
     * @param buckets
     */
    private void fillSliceFullContextForEntireTier(List<SliceDocBucket> buckets) {
        for (SliceDocBucket bucket : buckets) {
            if (bucket.tier == AllocationTier.ENTIRE) {
                List<SliceRef> fullRefs = new ArrayList<>();
                for (int i = 0; i < bucket.orderedSlices.size(); i++) {
                    fullRefs.add(new SliceRef(bucket.orderedSlices.get(i), i + 1, "full_document"));
                }
                bucket.renderEntries = fullRefs;
            } else if (bucket.tier == AllocationTier.EXCERPT) {
                bucket.renderEntries = bucket.partialEntries;
            }
        }
    }

    // ========== 渲染 ==========

    /**
     * 渲染父块文档桶为文本
     * @param bucket
     * @return
     */
    private String renderParentBucket(ParentDocBucket bucket) {
        StringBuilder sb = new StringBuilder();
        sb.append("[文档] ").append(resolveDocName(bucket.document)).append("\n");
        if (bucket.tier != AllocationTier.ENTIRE
                && bucket.document != null
                && StringUtils.hasText(bucket.document.getSummary())) {
            sb.append("[全文摘要]\n").append(bucket.document.getSummary().trim()).append("\n");
        }

        Map<String, SliceRecord> lookup = loadParentChunkLookup(
                bucket.renderEntries.stream().map(ChunkRef::getChunkId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        for (ChunkRef ref : bucket.renderEntries) {
            SliceRecord chunk = lookup.get(ref.chunkId);
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }
            sb.append("\n[父段正文 #").append(ref.chunkId).append("]\n");
            sb.append(safeTrim(chunk.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 渲染切片文档桶为文本
     * @param bucket
     * @return
     */
    private String renderSliceBucket(SliceDocBucket bucket) {
        StringBuilder sb = new StringBuilder();
        sb.append("[文档] ").append(resolveDocName(bucket.document)).append("\n");
        if (bucket.tier != AllocationTier.ENTIRE
                && bucket.document != null
                && StringUtils.hasText(bucket.document.getSummary())) {
            sb.append("[全文摘要]\n").append(bucket.document.getSummary().trim()).append("\n");
        }

        for (SliceRef ref : bucket.renderEntries) {
            if (!StringUtils.hasText(ref.slice.getContent())) {
                continue;
            }
            sb.append("\n[分片正文 #").append(ref.order).append("]\n");
            sb.append(safeTrim(ref.slice.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 渲染单文档父块上下文（含文档名和摘要）
     * @param document
     * @param chunks
     * @param isFullDoc
     * @param maxChars
     * @return
     */
    private String renderDocumentWithMeta(IngestDocument document, List<SliceRecord> chunks,
                                          boolean isFullDoc, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("[文档] ").append(resolveDocName(document)).append("\n");
        if (!isFullDoc && document != null && StringUtils.hasText(document.getSummary())) {
            sb.append("[全文摘要]\n").append(document.getSummary().trim()).append("\n");
        }
        for (SliceRecord chunk : chunks) {
            if (!StringUtils.hasText(chunk.getContent())) {
                continue;
            }
            int needed = chunk.getContent().length() + 4;
            if (sb.length() + needed > maxChars) {
                break;
            }
            sb.append("\n[父段正文 #").append(chunk.getSliceId()).append("]\n");
            sb.append(safeTrim(chunk.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 渲染单文档切片上下文（含文档名和摘要）
     * @param document
     * @param slices
     * @param isFullDoc
     * @param maxChars
     * @return
     */
    private String renderSliceDocWithMeta(IngestDocument document, List<SliceRecord> slices,
                                          boolean isFullDoc, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("[文档] ").append(resolveDocName(document)).append("\n");
        if (!isFullDoc && document != null && StringUtils.hasText(document.getSummary())) {
            sb.append("[全文摘要]\n").append(document.getSummary().trim()).append("\n");
        }
        int order = 1;
        for (SliceRecord slice : slices) {
            if (!StringUtils.hasText(slice.getContent())) {
                continue;
            }
            int needed = slice.getContent().length() + 12;
            if (sb.length() + needed > maxChars) {
                break;
            }
            sb.append("\n[分片正文 #").append(order++).append("]\n");
            sb.append(safeTrim(slice.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 拼接渲染片段和兜底文本
     * @param fragments
     * @param rawTexts
     * @param maxChars
     * @return
     */
    private String renderFragments(List<RenderedFragment> fragments, List<String> rawTexts, int maxChars) {
        if (fragments.isEmpty() && rawTexts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        fragments.stream()
                .sorted(Comparator.comparingDouble(RenderedFragment::score).reversed())
                .forEach(frag -> {
                    if (!StringUtils.hasText(frag.content)) {
                        return;
                    }
                    if (sb.length() > 0) {
                        appendWithinLimit(sb, "\n\n", maxChars);
                    }
                    appendWithinLimit(sb, frag.content.trim(), maxChars);
                });

        if (sb.length() < maxChars && !rawTexts.isEmpty()) {
            LinkedHashSet<String> deduped = new LinkedHashSet<>();
            for (String text : rawTexts) {
                String normalized = safeTrim(text);
                if (StringUtils.hasText(normalized)) {
                    deduped.add(normalized);
                }
            }
            if (!deduped.isEmpty()) {
                if (sb.length() > 0) {
                    appendWithinLimit(sb, "\n\n", maxChars);
                }
                appendWithinLimit(sb, "[通用切片]\n", maxChars);
                for (String text : deduped) {
                    appendWithinLimit(sb, "- " + text + "\n", maxChars);
                }
            }
        }
        return sb.toString().trim();
    }

    // ========== 聚焦选择 ==========

    /**
     * 从有序父块中选取与查询相关的聚焦块
     * @param orderedChunks
     * @param query
     * @return
     */
    private List<SliceRecord> pickFocusedChunks(List<SliceRecord> orderedChunks, String query) {
        if (!StringUtils.hasText(query) || orderedChunks.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = parseKeywords(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<RankedIndex> ranked = new ArrayList<>();
        for (int i = 0; i < orderedChunks.size(); i++) {
            SliceRecord chunk = orderedChunks.get(i);
            int relevance = scoreChunkRelevance(chunk.getContent(), null, query, queryTerms);
            if (relevance > 0) {
                ranked.add(new RankedIndex(i, relevance));
            }
        }
        if (ranked.isEmpty()) {
            return List.of();
        }

        ranked.sort(Comparator.comparingInt(RankedIndex::score).reversed()
                .thenComparingInt(RankedIndex::index));

        Set<Integer> selectedIndexes = gatherFocusIndexes(ranked, orderedChunks.size(), ragProperties.getContextExpand().getFocusNeighborSpan());
        List<SliceRecord> focused = new ArrayList<>();
        for (int i = 0; i < orderedChunks.size(); i++) {
            if (selectedIndexes.contains(i)) {
                focused.add(orderedChunks.get(i));
            }
        }
        return focused;
    }

    /**
     * 从有序切片中选取与查询相关的聚焦切片
     * @param orderedSlices
     * @param query
     * @return
     */
    private List<SliceRecord> pickFocusedSlices(List<SliceRecord> orderedSlices, String query) {
        if (!StringUtils.hasText(query) || orderedSlices.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = parseKeywords(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<RankedIndex> ranked = new ArrayList<>();
        for (int i = 0; i < orderedSlices.size(); i++) {
            SliceRecord slice = orderedSlices.get(i);
            int relevance = scoreChunkRelevance(slice.getContent(), null, query, queryTerms);
            if (relevance > 0) {
                ranked.add(new RankedIndex(i, relevance));
            }
        }
        if (ranked.isEmpty()) {
            return List.of();
        }

        ranked.sort(Comparator.comparingInt(RankedIndex::score).reversed()
                .thenComparingInt(RankedIndex::index));

        Set<Integer> selectedIndexes = gatherFocusIndexes(ranked, orderedSlices.size(), ragProperties.getContextExpand().getFocusNeighborSpan());
        List<SliceRecord> focused = new ArrayList<>();
        for (int i = 0; i < orderedSlices.size(); i++) {
            if (selectedIndexes.contains(i)) {
                focused.add(orderedSlices.get(i));
            }
        }
        return focused;
    }

    /**
     * 收集聚焦锚点及其邻居的索引
     * @param ranked
     * @param totalSize
     * @param neighborSpan
     * @return
     */
    private Set<Integer> gatherFocusIndexes(List<RankedIndex> ranked, int totalSize, int neighborSpan) {
        Set<Integer> selected = new LinkedHashSet<>();
        int anchorCount = 0;
        for (RankedIndex ri : ranked) {
            if (anchorCount >= ragProperties.getContextExpand().getFocusAnchorCeiling()) {
                break;
            }
            if (!selected.add(ri.index)) {
                continue;
            }
            anchorCount++;
            for (int offset = 1; offset <= neighborSpan; offset++) {
                if (ri.index - offset >= 0) {
                    selected.add(ri.index - offset);
                }
                if (ri.index + offset < totalSize) {
                    selected.add(ri.index + offset);
                }
            }
        }
        return selected;
    }

    /**
     * 计算块与查询的相关度分数
     * @param content
     * @param summary
     * @param query
     * @param queryTerms
     * @return
     */
    private int scoreChunkRelevance(String content, String summary, String query, Set<String> queryTerms) {
        String lowerContent = lowerSafe(content);
        String lowerSummary = lowerSafe(summary);
        String lowerQuery = lowerSafe(query);
        int score = 0;

        if (StringUtils.hasText(lowerQuery)) {
            if (StringUtils.hasText(lowerSummary) && lowerSummary.contains(lowerQuery)) {
                score += 12;
            }
            if (StringUtils.hasText(lowerContent) && lowerContent.contains(lowerQuery)) {
                score += 8;
            }
        }

        for (String term : queryTerms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            if (StringUtils.hasText(lowerSummary) && lowerSummary.contains(term)) {
                score += 5;
            }
            if (StringUtils.hasText(lowerContent) && lowerContent.contains(term)) {
                score += 2;
            }
        }
        return score;
    }

    // ========== 数据加载 ==========

    /**
     * 根据docId获取文档
     * @param docId
     * @return
     */
    private IngestDocument fetchDocumentByDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            return null;
        }
        return documentMetadataHandler.loadDocument(docId);
    }

    /**
     * 根据docId获取知识库当前活跃版本号
     * @param docId
     * @return
     */
    private String resolveActiveVersion(String docId) {
        IngestDocument document = fetchDocumentByDocId(docId);
        if (document == null || !StringUtils.hasText(document.getKbId())) {
            return null;
        }
        return knowledgeBaseVersionResolver.resolveActiveVersion(document.getKbId());
    }

    /**
     * 获取文档的有序父块列表
     * @param docId
     * @return
     */
    private List<SliceRecord> fetchOrderedParentChunks(String docId) {
        if (!StringUtils.hasText(docId)) {
            return List.of();
        }
        String activeVersion = resolveActiveVersion(docId);
        List<SliceRecord> chunks = sliceRecordRepository.fetchOrderedParentChunks(docId, activeVersion);
        return chunks != null ? chunks : List.of();
    }

    /**
     * 获取文档的有序切片列表
     * @param docId
     * @return
     */
    private List<SliceRecord> fetchOrderedSlices(String docId) {
        if (!StringUtils.hasText(docId)) {
            return List.of();
        }
        String activeVersion = resolveActiveVersion(docId);
        List<SliceRecord> slices = sliceRecordRepository.fetchOrderedParentChunks(docId, activeVersion);
        return slices != null ? slices : List.of();
    }

    /**
     * 批量加载父块到Map
     * @param parentIds
     * @return
     */
    private Map<String, SliceRecord> loadParentChunkLookup(Set<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        return sliceRecordRepository.loadParentChunkLookup(parentIds);
    }

    /**
     * 为父块桶附加文档信息（批量查询避免N+1）
     * @param buckets
     */
    private void attachDocumentToParentBuckets(List<ParentDocBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        Set<String> docIds = buckets.stream()
                .map(b -> b.docId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, IngestDocument> docMap = batchFetchDocuments(docIds);
        for (ParentDocBucket bucket : buckets) {
            bucket.document = docMap.get(bucket.docId);
        }
    }

    /**
     * 为切片桶附加文档信息（批量查询避免N+1）
     * @param buckets
     */
    private void attachDocumentToSliceBuckets(List<SliceDocBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        Set<String> docIds = buckets.stream()
                .map(b -> b.docId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, IngestDocument> docMap = batchFetchDocuments(docIds);
        for (SliceDocBucket bucket : buckets) {
            bucket.document = docMap.get(bucket.docId);
        }
    }

    /**
     * 批量查询文档
     * @param docIds
     * @return
     */
    private Map<String, IngestDocument> batchFetchDocuments(Set<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Map.of();
        }
        return documentMetadataHandler.batchLoadDocuments(docIds);
    }

    /**
     * 为切片桶加载有序切片（批量查询避免N+1）
     * @param buckets
     */
    private void hydrateSliceOrderedSlices(List<SliceDocBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        Set<String> docIds = buckets.stream()
                .map(b -> b.docId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (docIds.isEmpty()) {
            return;
        }

        // 批量查询所有切片记录
        List<SliceRecord> allSlices = sliceRecordRepository.fetchOrderedSlicesByDocIds(docIds, null);
        if (allSlices == null || allSlices.isEmpty()) {
            for (SliceDocBucket bucket : buckets) {
                bucket.orderedSlices = List.of();
            }
            return;
        }

        // 按docId分组
        Map<String, List<SliceRecord>> slicesByDoc = allSlices.stream()
                .collect(Collectors.groupingBy(SliceRecord::getDocId, LinkedHashMap::new, Collectors.toList()));

        for (SliceDocBucket bucket : buckets) {
            List<SliceRecord> slices = slicesByDoc.getOrDefault(bucket.docId, List.of());
            // 按活跃版本过滤
            String activeVersion = resolveActiveVersionFromDocument(bucket.document);
            if (StringUtils.hasText(activeVersion)) {
                slices = slices.stream()
                        .filter(s -> activeVersion.equals(s.getVersion()))
                        .collect(Collectors.toList());
            }
            bucket.orderedSlices = slices;
        }
    }

    /**
     * 从已加载的文档中解析活跃版本号
     * @param document
     * @return
     */
    private String resolveActiveVersionFromDocument(IngestDocument document) {
        if (document == null || !StringUtils.hasText(document.getKbId())) {
            return null;
        }
        return knowledgeBaseVersionResolver.resolveActiveVersion(document.getKbId());
    }

    // ========== 工具方法 ==========

    /**
     * 判断文档是否符合小文档全文直通条件
     * @param document
     * @param segmentCount
     * @return
     */
    private boolean canBypassAsSmallDoc(IngestDocument document, int segmentCount) {
        if (document == null) {
            return false;
        }
        return segmentCount > 0 && segmentCount <= ragProperties.getContextExpand().getSmallDocChunkCeiling();
    }

    /**
     * 估算文档全文字符数
     * @param document
     * @return
     */
    private int estimateFullDocumentChars(IngestDocument document) {
        if (document == null) {
            return 0;
        }
        int base = 0;
        if (document.getFileSize() != null && document.getFileSize() > 0) {
            base = (int) Math.min(document.getFileSize(), Integer.MAX_VALUE);
        }
        int summaryLen = StringUtils.hasText(document.getSummary()) ? document.getSummary().length() : 0;
        int nameLen = StringUtils.hasText(document.getDocName()) ? document.getDocName().length() : 0;
        return base + summaryLen + nameLen + 32;
    }

    /**
     * 估算父块桶的部分字符数
     * @param document
     * @param entries
     * @param chunkLookup
     * @return
     */
    private int estimateParentBucketChars(IngestDocument document, List<ChunkRef> entries,
                                           Map<String, SliceRecord> chunkLookup) {
        int total = 0;
        if (document != null) {
            total += safeLen("[文档]\n" + resolveDocName(document));
            total += safeLen(document.getSummary());
        }
        for (ChunkRef ref : entries) {
            SliceRecord chunk = chunkLookup.get(ref.chunkId);
            total += chunk != null ? safeLen(chunk.getContent()) : 0;
            total += 16;
        }
        return total;
    }

    /**
     * 估算切片桶的部分字符数
     * @param document
     * @param entries
     * @return
     */
    private int estimateSliceBucketChars(IngestDocument document, List<SliceRef> entries) {
        int total = 0;
        if (document != null) {
            total += safeLen("[文档]\n" + resolveDocName(document));
            total += safeLen(document.getSummary());
        }
        for (SliceRef ref : entries) {
            total += safeLen(ref.slice.getContent());
            total += 12;
        }
        return total;
    }

    /**
     * 计算多个文本内容的总字符长度
     * @param contents
     * @return
     */
    private int sumChunkContentLengths(List<String> contents) {
        int total = 0;
        for (String content : contents) {
            total += content == null ? 0 : content.length();
        }
        return total;
    }

    /**
     * 拼接文本列表
     * @param texts
     * @return
     */
    private String concatenateChunkTexts(List<String> texts) {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(text.trim());
        }
        return sb.toString();
    }

    /**
     * 获取文档显示名称
     * @param document
     * @return
     */
    private String resolveDocName(IngestDocument document) {
        if (document == null || !StringUtils.hasText(document.getDocName())) {
            return "(未知文档)";
        }
        return document.getDocName().trim();
    }

    /**
     * 安全追加文本，不超过最大字符数
     * @param sb
     * @param text
     * @param maxChars
     */
    private void appendWithinLimit(StringBuilder sb, String text, int maxChars) {
        if (!StringUtils.hasText(text) || sb.length() >= maxChars) {
            return;
        }
        int remaining = maxChars - sb.length();
        if (text.length() <= remaining) {
            sb.append(text);
        } else {
            sb.append(text, 0, remaining);
        }
    }

    private String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }

    private int safeLen(String text) {
        return text == null ? 0 : text.length();
    }

    private String lowerSafe(String text) {
        return StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT) : "";
    }

    // ========== 内部类 ==========

    /**
     * 预算分配层级
     */
    private enum AllocationTier {
        ENTIRE,
        EXCERPT,
        DISCARDED
    }

    /**
     * 关键词命中位置
     */
    private static class HitPosition {

        private final int offset;

        private final int length;

        HitPosition(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        int getOffset() {
            return offset;
        }

        int getLength() {
            return length;
        }
    }

    /**
     * 文本区间
     */
    private static class TextRegion {

        private final int start;

        private final int end;

        private final int weight;

        TextRegion(int start, int end, int weight) {
            this.start = start;
            this.end = end;
            this.weight = weight;
        }

        int getStart() {
            return start;
        }

        int getEnd() {
            return end;
        }

        int getWeight() {
            return weight;
        }
    }

    /**
     * 文档聚合记录
     */
    private static class DocAggregation {
        final String docId;
        final String chunkId;
        final double score;
        final String prevChunkId;
        final String nextChunkId;

        DocAggregation(String docId, String chunkId, double score,
                       String prevChunkId, String nextChunkId) {
            this.docId = docId;
            this.chunkId = chunkId;
            this.score = score;
            this.prevChunkId = prevChunkId;
            this.nextChunkId = nextChunkId;
        }
    }

    /**
     * 渲染片段
     */
    private static class RenderedFragment {
        final String docId;
        final double score;
        final String content;

        RenderedFragment(String docId, double score, String content) {
            this.docId = docId;
            this.score = score;
            this.content = content;
        }

        double score() {
            return score;
        }
    }

    /**
     * 排名索引
     */
    private static class RankedIndex {
        final int index;
        final int score;

        RankedIndex(int index, int score) {
            this.index = index;
            this.score = score;
        }

        int index() {
            return index;
        }

        int score() {
            return score;
        }
    }

    /**
     * 切片引用
     */
    private static class ChunkRef {
        private final String chunkId;
        private final double hitScore;
        private final String origin;

        ChunkRef(String chunkId, double hitScore, String origin) {
            this.chunkId = chunkId;
            this.hitScore = hitScore;
            this.origin = origin;
        }

        String getChunkId() {
            return chunkId;
        }
    }

    /**
     * 切片条目
     */
    private static class SliceRef {
        final SliceRecord slice;
        final int order;
        final String origin;

        SliceRef(SliceRecord slice, int order, String origin) {
            this.slice = slice;
            this.order = order;
            this.origin = origin;
        }
    }

    /**
     * 父块文档桶
     */
    private static class ParentDocBucket {
        final String docId;
        IngestDocument document;
        double topScore;
        int partialCharCount;
        int allocatedChars;
        AllocationTier tier = AllocationTier.DISCARDED;
        final Map<String, ChunkRef> chunkEntries = new LinkedHashMap<>();
        List<ChunkRef> partialEntries = List.of();
        List<ChunkRef> renderEntries = List.of();

        ParentDocBucket(String docId) {
            this.docId = docId;
        }

        double topScore() {
            return topScore;
        }
    }

    /**
     * 切片文档桶
     */
    private static class SliceDocBucket {
        final String docId;
        IngestDocument document;
        double topScore;
        int partialCharCount;
        int allocatedChars;
        AllocationTier tier = AllocationTier.DISCARDED;
        final Set<String> matchedSliceIds = new LinkedHashSet<>();
        List<SliceRecord> orderedSlices = List.of();
        List<SliceRef> partialEntries = List.of();
        List<SliceRef> renderEntries = List.of();

        SliceDocBucket(String docId) {
            this.docId = docId;
        }

        double topScore() {
            return topScore;
        }
    }
}
