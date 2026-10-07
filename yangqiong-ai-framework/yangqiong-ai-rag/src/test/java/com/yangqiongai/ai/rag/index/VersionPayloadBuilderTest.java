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
package com.yangqiongai.ai.rag.index;

import com.yangqiongai.ai.rag.model.SliceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VersionPayloadBuilder 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class VersionPayloadBuilderTest {

    private VersionPayloadBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new VersionPayloadBuilder();
    }

    @Test
    @DisplayName("buildPayload - 包含所有必需字段")
    void buildPayload_containsAllRequiredFields() {
        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setDocId("doc-1");
        slice.setContent("测试内容");
        slice.setSliceType("parent");
        slice.setParentId("parent-1");
        slice.setChunkKey("chunk-key-1");
        slice.setTokenCount(100);
        slice.setMetadata("{\"key\":\"value\"}");

        Map<String, Object> payload = builder.buildPayload(slice, "kb-1", "v1");

        assertThat(payload).containsEntry("sliceId", "slice-1");
        assertThat(payload).containsEntry("docId", "doc-1");
        assertThat(payload).containsEntry("kbId", "kb-1");
        assertThat(payload).containsEntry("content", "测试内容");
        assertThat(payload).containsEntry("sliceType", "parent");
        assertThat(payload).containsEntry("parentId", "parent-1");
        assertThat(payload).containsEntry("chunkKey", "chunk-key-1");
        assertThat(payload).containsEntry("version", "v1");
        assertThat(payload).containsEntry("tokenCount", "100");
        assertThat(payload).containsEntry("metadata", "{\"key\":\"value\"}");
    }

    @Test
    @DisplayName("buildPayload - parentId为null时使用空字符串")
    void buildPayload_usesEmptyString_whenParentIdNull() {
        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setDocId("doc-1");
        slice.setContent("内容");
        slice.setSliceType("child");
        slice.setParentId(null);
        slice.setChunkKey(null);
        slice.setTokenCount(null);
        slice.setMetadata(null);

        Map<String, Object> payload = builder.buildPayload(slice, "kb-1", null);

        assertThat(payload).containsEntry("parentId", "");
        assertThat(payload).containsEntry("chunkKey", "");
        assertThat(payload).containsEntry("version", "");
        assertThat(payload).containsEntry("tokenCount", "0");
        assertThat(payload).containsEntry("metadata", "");
    }

    @Test
    @DisplayName("computeChunkKey - 返回content+docId的SHA-256哈希")
    void computeChunkKey_returnsSHA256Hash() throws Exception {
        String content = "测试内容";
        String docId = "doc-1";
        String expectedRaw = docId + ":" + content;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(expectedRaw.getBytes(StandardCharsets.UTF_8));
        StringBuilder expected = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                expected.append('0');
            }
            expected.append(hex);
        }

        String result = builder.computeChunkKey(content, docId);

        assertThat(result).isEqualTo(expected.toString());
        assertThat(result).hasSize(64); // SHA-256 hex string length
    }

    @Test
    @DisplayName("computeChunkKey - 不同输入产生不同哈希")
    void computeChunkKey_differentInputs_differentHashes() {
        String hash1 = builder.computeChunkKey("内容1", "doc-1");
        String hash2 = builder.computeChunkKey("内容2", "doc-1");
        String hash3 = builder.computeChunkKey("内容1", "doc-2");

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }
}
