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
package com.yangqiongai.ai.data.rag.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.data.rag.entity.KbSliceRecord;
import com.yangqiongai.ai.data.rag.mapper.KbSliceRecordMapper;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库切片记录
 * @author yangqiong
 */
public class DefaultSliceRecordRepository implements SliceRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultSliceRecordRepository.class);

    @Autowired
    private KbSliceRecordMapper kbSliceRecordMapper;

    @Override
    public void insert(SliceRecord record) {
        KbSliceRecord entity = toEntity(record);
        kbSliceRecordMapper.insert(entity);
        record.setId(entity.getId());
    }

    @Override
    public void updateById(SliceRecord record) {
        KbSliceRecord entity = toEntity(record);
        kbSliceRecordMapper.updateById(entity);
    }

    @Override
    public List<SliceRecord> listByKbIdAndDocId(String kbId, String docId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getDocId, docId)
                .eq(KbSliceRecord::getIsActive, true)
                .orderByAsc(KbSliceRecord::getSortNum);
        return kbSliceRecordMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public SliceRecord getByKbIdAndDocIdAndSliceId(String kbId, String docId, String sliceId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getDocId, docId)
                .eq(KbSliceRecord::getSliceId, sliceId);
        KbSliceRecord entity = kbSliceRecordMapper.selectOne(wrapper);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public int deleteByDocId(String docId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getDocId, docId);
        return kbSliceRecordMapper.delete(wrapper);
    }

    @Override
    public int deleteByKbId(String kbId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getKbId, kbId);
        return kbSliceRecordMapper.delete(wrapper);
    }

    @Override
    public int deleteByKbIdAndDocId(String kbId, String docId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getDocId, docId);
        return kbSliceRecordMapper.delete(wrapper);
    }

    @Override
    public Set<String> loadExistingChunkKeys(String docId, String kbId) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KbSliceRecord::getChunkKey)
                .eq(KbSliceRecord::getDocId, docId)
                .eq(KbSliceRecord::getKbId, kbId);
        return kbSliceRecordMapper.selectList(wrapper).stream()
                .map(KbSliceRecord::getChunkKey)
                .collect(Collectors.toSet());
    }

    @Override
    public List<SliceRecord> fetchOrderedParentChunks(String docId, String activeVersion) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getDocId, docId)
                .eq(KbSliceRecord::getIsActive, true)
                .eq(KbSliceRecord::getSliceType, "parent")
                .eq(KbSliceRecord::getVersion, activeVersion)
                .orderByAsc(KbSliceRecord::getSortNum);
        return kbSliceRecordMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public Map<String, SliceRecord> loadParentChunkLookup(Set<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KbSliceRecord::getSliceId, parentIds)
                .eq(KbSliceRecord::getIsActive, true);
        List<KbSliceRecord> entities = kbSliceRecordMapper.selectList(wrapper);
        Map<String, SliceRecord> result = new HashMap<>();
        for (KbSliceRecord entity : entities) {
            result.put(entity.getSliceId(), toModel(entity));
        }
        return result;
    }

    @Override
    public List<SliceRecord> fetchOrderedSlicesByDocIds(Set<String> docIds, String activeVersion) {
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KbSliceRecord::getDocId, docIds)
                .eq(KbSliceRecord::getIsActive, true)
                .eq(KbSliceRecord::getVersion, activeVersion)
                .orderByAsc(KbSliceRecord::getSortNum);
        return kbSliceRecordMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<SliceRecord> searchFullTextSlices(String kbId, String activeVersion, boolean fullMode,
                                                   int overfetch, String keyword, String sliceType) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getIsActive, true)
                .eq(KbSliceRecord::getVersion, activeVersion);
        if (sliceType != null) {
            wrapper.eq(KbSliceRecord::getSliceType, sliceType);
        }
        if (fullMode && keyword != null && !keyword.isBlank()) {
            wrapper.apply("MATCH(chunk_text) AGAINST({0} IN BOOLEAN MODE)", keyword);
        } else if (keyword != null && !keyword.isBlank()) {
            // 空格分隔的多片段为OR匹配（分词片段/滑窗片段），提升LIKE回退命中率
            String[] fragments = keyword.trim().split("\\s+");
            if (fragments.length == 1) {
                wrapper.like(KbSliceRecord::getContent, keyword.trim());
            } else {
                wrapper.and(w -> {
                    for (String fragment : fragments) {
                        w.or().like(KbSliceRecord::getContent, fragment);
                    }
                });
            }
        }
        wrapper.last("LIMIT " + overfetch);
        List<SliceRecord> records = kbSliceRecordMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
        fillDocNames(records);
        return records;
    }

    @Override
    public List<SliceRecord> listActiveByKbId(String kbId, List<String> docIds) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<KbSliceRecord>()
                .eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getIsActive, true);
        if (docIds != null && !docIds.isEmpty()) {
            wrapper.in(KbSliceRecord::getDocId, docIds);
        }
        wrapper.orderByAsc(KbSliceRecord::getSortNum);
        List<KbSliceRecord> entities = kbSliceRecordMapper.selectList(wrapper);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public int countActiveByKbId(String kbId, List<String> docIds) {
        LambdaQueryWrapper<KbSliceRecord> wrapper = new LambdaQueryWrapper<KbSliceRecord>()
                .eq(KbSliceRecord::getKbId, kbId)
                .eq(KbSliceRecord::getIsActive, true);
        if (docIds != null && !docIds.isEmpty()) {
            wrapper.in(KbSliceRecord::getDocId, docIds);
        }
        Long count = kbSliceRecordMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 批量回填文档名称，供全文检索证据透出
     * @param records
     */
    private void fillDocNames(List<SliceRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<String> docIds = records.stream()
                .map(SliceRecord::getDocId)
                .filter(docId -> docId != null && !docId.isBlank())
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return;
        }
        Map<String, String> docNameMap = new HashMap<>();
        for (Map<String, Object> row : kbSliceRecordMapper.selectDocNamesByDocIds(docIds)) {
            Object docId = row.get("docId");
            Object docName = row.get("docName");
            if (docId instanceof String id && docName instanceof String name) {
                docNameMap.put(id, name);
            }
        }
        if (docNameMap.isEmpty()) {
            return;
        }
        for (SliceRecord record : records) {
            record.setDocName(docNameMap.get(record.getDocId()));
        }
    }

    private KbSliceRecord toEntity(SliceRecord model) {
        KbSliceRecord entity = new KbSliceRecord();
        entity.setId(model.getId());
        entity.setSliceId(model.getSliceId());
        entity.setDocId(model.getDocId());
        entity.setKbId(model.getKbId());
        entity.setContent(model.getContent());
        entity.setChunkKey(model.getChunkKey());
        entity.setParentId(model.getParentId());
        entity.setSliceType(model.getSliceType());
        entity.setTokenCount(model.getTokenCount());
        entity.setVersion(model.getVersion());
        entity.setSortNum(model.getSortNum());
        entity.setChunkIndex(model.getSortNum());
        entity.setIsActive(model.getIsActive());
        entity.setMetadata(model.getMetadata());
        return entity;
    }

    private SliceRecord toModel(KbSliceRecord entity) {
        SliceRecord model = new SliceRecord();
        model.setId(entity.getId());
        model.setSliceId(entity.getSliceId());
        model.setDocId(entity.getDocId());
        model.setKbId(entity.getKbId());
        model.setContent(entity.getContent());
        model.setChunkKey(entity.getChunkKey());
        model.setParentId(entity.getParentId());
        model.setSliceType(entity.getSliceType());
        model.setTokenCount(entity.getTokenCount());
        model.setVersion(entity.getVersion());
        model.setSortNum(entity.getSortNum());
        model.setIsActive(entity.getIsActive());
        model.setMetadata(entity.getMetadata());
        return model;
    }
}
