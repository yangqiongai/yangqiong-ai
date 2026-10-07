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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档切片查询
 * @author yangqiong
 */
@Service
public class KbSliceQuerier {

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    /**
     * 查询文档切片列表
     * @param kbId
     * @param docId
     * @return
     */
    public List<SliceRecord> listSlices(String kbId, String docId) {
        return sliceRecordRepository.listByKbIdAndDocId(kbId, docId);
    }

    /**
     * 查询切片详情
     * @param kbId
     * @param docId
     * @param sliceId
     * @return
     */
    public SliceRecord getSlice(String kbId, String docId, String sliceId) {
        return sliceRecordRepository.getByKbIdAndDocIdAndSliceId(kbId, docId, sliceId);
    }
}
