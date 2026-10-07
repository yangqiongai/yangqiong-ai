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
package com.yangqiongai.ai.data.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.data.rag.entity.KbSliceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 知识库切片记录
 * @author yangqiong
 */
@Mapper
public interface KbSliceRecordMapper extends BaseMapper<KbSliceRecord> {

    /**
     * 按文档ID批量查询文档名称
     * @param docIds
     * @return
     */
    @Select("<script>SELECT doc_id AS docId, doc_name AS docName FROM ai_kb_document WHERE doc_id IN "
            + "<foreach collection='docIds' item='item' open='(' separator=',' close=')'>#{item}</foreach></script>")
    List<Map<String, Object>> selectDocNamesByDocIds(@Param("docIds") Collection<String> docIds);
}
