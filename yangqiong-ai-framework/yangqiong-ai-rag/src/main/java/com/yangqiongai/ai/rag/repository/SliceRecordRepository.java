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
package com.yangqiongai.ai.rag.repository;

import com.yangqiongai.ai.rag.model.SliceRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库切片记录
 * @author yangqiong
 */
public interface SliceRecordRepository {

    /**
     * 插入切片记录
     * @param record
     */
    void insert(SliceRecord record);

    /**
     * 按主键更新
     * @param record
     */
    void updateById(SliceRecord record);

    /**
     * 查询文档切片列表（按sortNum升序）
     * @param kbId
     * @param docId
     * @return
     */
    List<SliceRecord> listByKbIdAndDocId(String kbId, String docId);

    /**
     * 查询切片详情
     * @param kbId
     * @param docId
     * @param sliceId
     * @return
     */
    SliceRecord getByKbIdAndDocIdAndSliceId(String kbId, String docId, String sliceId);

    /**
     * 按文档ID删除切片
     * @param docId
     * @return
     */
    int deleteByDocId(String docId);

    /**
     * 按知识库ID删除切片
     * @param kbId
     * @return
     */
    int deleteByKbId(String kbId);

    /**
     * 按文档ID和知识库ID删除切片
     * @param kbId
     * @param docId
     * @return
     */
    int deleteByKbIdAndDocId(String kbId, String docId);

    /**
     * 按文档ID和知识库ID查询已存在的切片哈希键
     * @param docId
     * @param kbId
     * @return
     */
    Set<String> loadExistingChunkKeys(String docId, String kbId);

    /**
     * 获取文档的有序父块列表
     * @param docId
     * @param activeVersion
     * @return
     */
    List<SliceRecord> fetchOrderedParentChunks(String docId, String activeVersion);

    /**
     * 批量按sliceId加载父块到Map
     * @param parentIds
     * @return
     */
    Map<String, SliceRecord> loadParentChunkLookup(Set<String> parentIds);

    /**
     * 批量按docId加载有序切片
     * @param docIds
     * @param activeVersion
     * @return
     */
    List<SliceRecord> fetchOrderedSlicesByDocIds(Set<String> docIds, String activeVersion);

    /**
     * 全文检索：查询切片记录
     * @param kbId
     * @param activeVersion
     * @param fullMode
     * @param overfetch
     * @param keyword
     * @param sliceType
     * @return
     */
    List<SliceRecord> searchFullTextSlices(String kbId, String activeVersion, boolean fullMode,
                                            int overfetch, String keyword, String sliceType);

    /**
     * 按知识库ID和可选文档ID列表查询有效切片（按sortNum升序）
     * @param kbId
     * @param docIds
     * @return
     */
    List<SliceRecord> listActiveByKbId(String kbId, List<String> docIds);

    /**
     * 按知识库ID和可选文档ID列表统计有效切片数
     * @param kbId
     * @param docIds
     * @return
     */
    int countActiveByKbId(String kbId, List<String> docIds);
}
