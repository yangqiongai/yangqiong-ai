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

import com.hankcs.hanlp.HanLP;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import io.micrometer.tracing.annotation.NewSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 全文检索
 * @author yangqiong
 */
@Service
public class DefaultFullTextSearcher implements FullTextSearcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultFullTextSearcher.class);

    @Autowired
    private RagProperties ragProperties;

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    @Qualifier("ragRetryTemplate")
    private RetryTemplate retryTemplate;

    /**
     * 全文检索通道
     * @param query
     * @param kbId
     * @param limit
     * @param activeVersion
     * @return
     */
    @Override
    @NewSpan("rag-fulltext-search")
    public List<ChunkCandidate> searchFullText(String query, String kbId, int limit, String activeVersion) {
        String trimmedQuery = normalizeQuery(query);
        if (!StringUtils.hasText(trimmedQuery) || !StringUtils.hasText(kbId)) {
            return List.of();
        }
        int effectiveLimit = limit > 0 ? limit : ragProperties.getFulltext().getFallbackResultSize();

        List<ChunkCandidate> sliceResults = matchSlices(trimmedQuery, kbId, effectiveLimit, activeVersion);
        if (!sliceResults.isEmpty()) {
            log.info("切片全文检索命中, query长度: {}, kbId: {}, 命中数: {}", trimmedQuery.length(), kbId, sliceResults.size());
            return sliceResults;
        }

        List<ChunkCandidate> parentResults = matchParents(trimmedQuery, kbId, effectiveLimit, activeVersion);
        log.info("父块全文检索, query长度: {}, kbId: {}, 命中数: {}", trimmedQuery.length(), kbId, parentResults.size());
        return parentResults;
    }

    /**
     * 切片级全文匹配
     * @param keyword
     * @param kbId
     * @param maxCount
     * @param activeVersion
     * @return
     */
    private List<ChunkCandidate> matchSlices(String keyword, String kbId, int maxCount, String activeVersion) {
        List<SliceRecord> rawMatches = fetchSliceRecords(keyword, kbId, true, maxCount, activeVersion);
        if (rawMatches.isEmpty() && keyword.length() > 1) {
            rawMatches = fetchSliceRecords(keyword, kbId, false, maxCount, activeVersion);
        }
        if (rawMatches.isEmpty()) {
            return List.of();
        }

        Set<String> tokens = tokenize(keyword);
        List<RankedSlice> ranked = new ArrayList<>();
        for (SliceRecord rec : rawMatches) {
            if (rec == null || !StringUtils.hasText(rec.getContent())) {
                continue;
            }
            double relevance = computeSliceRelevance(rec.getContent(), keyword, tokens);
            ranked.add(new RankedSlice(rec, relevance));
        }
        ranked.sort(Comparator.comparingDouble(RankedSlice::relevance).reversed());

        LinkedHashMap<String, ChunkCandidate> dedupMap = new LinkedHashMap<>();
        for (RankedSlice entry : ranked) {
            String dedupeKey = StringUtils.hasText(entry.record.getSliceId())
                    ? entry.record.getSliceId()
                    : String.valueOf(entry.record.getId());
            dedupMap.computeIfAbsent(dedupeKey, k -> buildSliceCandidate(entry.record, entry.relevance));
            if (dedupMap.size() >= maxCount) {
                break;
            }
        }
        return new ArrayList<>(dedupMap.values());
    }

    /**
     * 父块级全文匹配
     * @param keyword
     * @param kbId
     * @param maxCount
     * @param activeVersion
     * @return
     */
    private List<ChunkCandidate> matchParents(String keyword, String kbId, int maxCount, String activeVersion) {
        List<SliceRecord> rawMatches = fetchParentRecords(keyword, kbId, true, maxCount, activeVersion);
        if (rawMatches.isEmpty() && keyword.length() > 1) {
            rawMatches = fetchParentRecords(keyword, kbId, false, maxCount, activeVersion);
        }
        if (rawMatches.isEmpty()) {
            return List.of();
        }

        Set<String> tokens = tokenize(keyword);
        List<RankedSlice> ranked = new ArrayList<>();
        for (SliceRecord rec : rawMatches) {
            if (rec == null || !StringUtils.hasText(rec.getContent())) {
                continue;
            }
            double relevance = computeSliceRelevance(rec.getContent(), keyword, tokens);
            ranked.add(new RankedSlice(rec, relevance));
        }
        ranked.sort(Comparator.comparingDouble(RankedSlice::relevance).reversed());

        LinkedHashMap<String, ChunkCandidate> dedupMap = new LinkedHashMap<>();
        for (RankedSlice entry : ranked) {
            String dedupeKey = StringUtils.hasText(entry.record.getSliceId())
                    ? entry.record.getSliceId()
                    : String.valueOf(entry.record.getId());
            dedupMap.computeIfAbsent(dedupeKey, k -> buildSliceCandidate(entry.record, entry.relevance));
            if (dedupMap.size() >= maxCount) {
                break;
            }
        }
        return new ArrayList<>(dedupMap.values());
    }

    private List<SliceRecord> fetchSliceRecords(String keyword, String kbId, boolean fullMode, int maxCount, String activeVersion) {
        int overfetch = fullMode
                ? Math.max(ragProperties.getFulltext().getMinOverfetch(), maxCount * ragProperties.getFulltext().getOverfetchFactor())
                : Math.min(ragProperties.getFulltext().getLikeFallbackLimit(), Math.max(ragProperties.getFulltext().getMinOverfetch(), maxCount * ragProperties.getFulltext().getOverfetchFactor()));
        return retryTemplate.execute(context -> sliceRecordRepository.searchFullTextSlices(kbId, activeVersion, fullMode, overfetch, buildQueryKeyword(keyword, fullMode), null));
    }

    private List<SliceRecord> fetchParentRecords(String keyword, String kbId, boolean fullMode, int maxCount, String activeVersion) {
        int overfetch = fullMode
                ? Math.max(ragProperties.getFulltext().getMinOverfetch(), maxCount * ragProperties.getFulltext().getOverfetchFactor())
                : Math.min(ragProperties.getFulltext().getLikeFallbackLimit(), Math.max(ragProperties.getFulltext().getMinOverfetch(), maxCount * ragProperties.getFulltext().getOverfetchFactor()));
        return retryTemplate.execute(context -> sliceRecordRepository.searchFullTextSlices(kbId, activeVersion, fullMode, overfetch, buildQueryKeyword(keyword, fullMode), "parent"));
    }

    /**
     * 构建查询关键词：布尔模式按分词结果空格拼接（BOOLEAN MODE下空格分隔为OR匹配，
     * 规避长句整串短语化导致的不命中）；LIKE回退使用分词片段+滑窗片段
     * @param keyword
     * @param fullMode
     * @return
     */
    private String buildQueryKeyword(String keyword, boolean fullMode) {
        List<String> fragments = fullMode ? new ArrayList<>(tokenize(keyword)) : buildLikeFragments(keyword);
        return fragments.isEmpty() ? keyword : String.join(" ", fragments);
    }

    private double computeSliceRelevance(String content, String keyword, Set<String> tokens) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        double score = ragProperties.getFulltext().getBaseRelevance();
        if (lowerContent.contains(lowerKeyword)) {
            score += ragProperties.getFulltext().getExactMatchBonus();
        }
        for (String token : tokens) {
            if (lowerContent.contains(token)) {
                score += ragProperties.getFulltext().getTermHitBonus();
            }
        }
        return Math.min(score, 1.0);
    }

    private Set<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        List<com.hankcs.hanlp.seg.common.Term> terms = HanLP.segment(text.trim());
        for (com.hankcs.hanlp.seg.common.Term term : terms) {
            String word = term.word.trim().toLowerCase(Locale.ROOT);
            if (word.length() >= 2) {
                result.add(word);
            }
        }
        return result;
    }

    private List<String> buildLikeFragments(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> fragments = new HashSet<>(tokenize(text));
        String compact = text.trim().replaceAll("\\s+", "");
        if (compact.length() > 8) {
            int window = Math.min(8, Math.max(4, compact.length() / 2));
            int step = Math.max(2, window / 2);
            for (int i = 0; i + window <= compact.length() && fragments.size() < 6; i += step) {
                fragments.add(compact.substring(i, i + window));
            }
        }
        if (fragments.isEmpty()) {
            fragments.add(compact);
        }
        return new ArrayList<>(fragments);
    }

    private ChunkCandidate buildSliceCandidate(SliceRecord record, double relevance) {
        ChunkCandidate candidate = new ChunkCandidate();
        candidate.setText(record.getContent());
        candidate.setDocId(record.getDocId());
        candidate.setDocName(record.getDocName());
        candidate.setKbId(record.getKbId());
        candidate.setSliceId(record.getSliceId());
        candidate.setParentId(record.getParentId());
        candidate.setChildHit("child".equals(record.getSliceType()));
        candidate.setScore(relevance);
        return candidate;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private record RankedSlice(SliceRecord record, double relevance) {
    }
}
