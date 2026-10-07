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
package com.yangqiongai.ai.memory.graph;

import com.hankcs.hanlp.HanLP;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.memory.config.ConditionalOnCloudMemoryMode;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.ValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识图谱服务
 * @author yangqiong
 */
@Service
@ConditionalOnCloudMemoryMode
@ConditionalOnBean(QdrantVectorStorage.class)
@ConditionalOnProperty(prefix = "ai.memory.graph", name = "enabled", havingValue = "true")
public class MemoryGraphManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryGraphManager.class);

    private static final String DEFAULT_DISTANCE = "Cosine";

    private static final int DEFAULT_VECTOR_SIZE = 1024;

    private static final int MAX_BFS_VISIT = 50;

    private static final String PAYLOAD_KEY_TRIPLE_ID = "tripleId";

    private static final String PAYLOAD_KEY_USER_ID = "userId";

    private static final String PAYLOAD_KEY_SUBJECT = "subject";

    private static final String PAYLOAD_KEY_PREDICATE = "predicate";

    private static final String PAYLOAD_KEY_OBJECT = "object";

    private static final String PAYLOAD_KEY_IS_CAUSAL = "isCausal";

    private static final String PAYLOAD_KEY_VALID_FROM = "validFrom";

    private static final String PAYLOAD_KEY_VALID_UNTIL = "validUntil";

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            从以下文本中提取实体-关系三元组，仅输出JSON数组（不要输出任何其他内容、不要markdown代码块）：
            [{"subject":"用户","predicate":"喜欢","object":"咖啡","isCausal":false,"validFrom":"","validUntil":"","confidence":0.9}]
            
            字段说明：
            - subject: 主体实体（必须）
            - predicate: 关系/谓词（必须，如"喜欢"、"负责"、"导致"）
            - object: 客体实体（必须）
            - isCausal: 是否为因果关系（true表示A导致B，false表示一般关系）
            - validFrom: 关系生效时间（ISO格式，可为空）
            - validUntil: 关系失效时间（ISO格式，可为空，表示永久有效）
            - confidence: 置信度 0-1
            
            提取规则：
            1. 仅提取明确表达的关系，不要推断
            2. 因果关系（导致、引起、使得）isCausal=true
            3. 偏好、属性、归属等关系 isCausal=false
            4. 实体名应简洁（如"张三"而非"张三这个人"）
            
            文本：{text}""";

    private static final DateTimeFormatter[] ISO_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    @Autowired
    private QdrantVectorStorage qdrantVectorService;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.memory.graph.collection:ai_memory_graph}")
    private String collectionName;

    @Value("${ai.memory.graph.model-code:bge-base-zh-djl}")
    private String embeddingModelCode;

    @Value("${ai.memory.graph.extraction-model-code:defaultAgent}")
    private String extractionModelCode;

    /**
     * 实体识别使用的模型代码
     */
    @Value("${ai.memory.graph.entity-recognizer.llm.model-code:${ai.memory.graph.model-code:defaultAgent}}")
    private String entityModelCode;

    /**
     * LLM实体识别超时（毫秒）
     */
    @Value("${ai.memory.graph.entity-recognizer.llm.timeout-ms:3000}")
    private int entityLlmTimeoutMs;

    /**
     * 实体提取Prompt模板
     */
    private static final String ENTITY_EXTRACTION_PROMPT = """
            你是一个实体识别助手。从下面的用户查询中，提取所有可能的命名实体。
            命名实体类型包括：人名、地名、机构名、项目名、技术术语、产品名、日期时间、数字金额等。
            要求：
            1. 每行一个实体，不要包含解释
            2. 实体长度至少2个字符
            3. 最多返回 %d 个实体
            4. 去除停用词（如"的"、"是"、"在"等）
            5. 只输出实体本身，不要加序号或标点
            
            示例：
            查询：南京天气预报 7月19日 2026
            南京
            7月19日
            2026
            
            查询：%s
            """;

    /**
     * JSON数组提取模式（兼容LLM返回JSON格式的情况）
     */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[([^\\[\\]]+)\\]");

    private volatile boolean collectionInitialized;

    /**
     * 从文本中提取实体-关系三元组
     * @param text
     * @param userId
     * @return
     */
    public List<KnowledgeTriple> extractTriples(String text, String userId) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        if (languageModelFactory == null) {
            log.debug("LanguageModelFactory 未注入, 跳过三元组提取");
            return Collections.emptyList();
        }
        try {
            String prompt = EXTRACTION_PROMPT_TEMPLATE.replace("{text}", text);
            String response = languageModelFactory.generateText(extractionModelCode, prompt);
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }
            return parseTriples(response, userId);
        } catch (Exception e) {
            log.warn("三元组提取失败, 降级为不提取: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 按实体查询关联记忆（多跳推理，沿所有边遍历）
     * @param entity
     * @param userId
     * @param hops
     * @return
     */
    public List<KnowledgeTriple> searchByEntity(String entity, String userId, int hops) {
        if (entity == null || entity.isBlank() || userId == null || userId.isBlank() || hops < 1) {
            return Collections.emptyList();
        }
        return bfsTraverse(entity, userId, hops, false);
    }

    /**
     * 因果链推理：沿因果边从起始实体推导到目标实体
     * @param startEntity
     * @param userId
     * @param maxHops
     * @return
     */
    public List<KnowledgeTriple> searchByCausalChain(String startEntity, String userId, int maxHops) {
        if (startEntity == null || startEntity.isBlank() || userId == null || userId.isBlank() || maxHops < 1) {
            return Collections.emptyList();
        }
        return bfsTraverse(startEntity, userId, maxHops, true);
    }

    /**
     * 持久化三元组到Qdrant
     * @param triples
     * @param userId
     * @return
     */
    public int saveTriples(List<KnowledgeTriple> triples, String userId) {
        if (triples == null || triples.isEmpty() || userId == null) {
            return 0;
        }
        if (languageModelFactory == null) {
            log.debug("LanguageModelFactory 未注入, 跳过三元组存储");
            return 0;
        }
        int saved = 0;
        List<PointStruct> points = new ArrayList<>(triples.size());
        int vectorSize = -1;
        for (KnowledgeTriple triple : triples) {
            if (!isValidTriple(triple)) {
                continue;
            }
            try {
                float[] vector = embed(triple.toText());
                if (vector == null || vector.length == 0) {
                    continue;
                }
                vectorSize = vector.length;
                String tripleId = triple.getTripleId() != null ? triple.getTripleId() : UUID.randomUUID().toString();
                triple.setTripleId(tripleId);
                triple.setUserId(userId);
                PointStruct point = buildPoint(tripleId, vector, triple, userId);
                points.add(point);
                saved++;
            } catch (Exception e) {
                log.warn("三元组向量化失败: {}", triple, e);
            }
        }
        if (points.isEmpty()) {
            return 0;
        }
        try {
            ensureCollectionExists(vectorSize > 0 ? vectorSize : DEFAULT_VECTOR_SIZE);
            qdrantVectorService.upsertPoints(collectionName, points);
            log.info("三元组批量写入完成: userId={}, 数量={}", userId, saved);
            return saved;
        } catch (Exception e) {
            log.warn("三元组批量写入失败: userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 按用户ID批量删除所有知识图谱三元组
     * @param userId
     * @return
     */
    public int deleteByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return -1;
        }
        try {
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey(PAYLOAD_KEY_USER_ID)
                                    .setMatch(Match.newBuilder().setKeyword(userId).build())
                                    .build())
                            .build())
                    .build();
            int count = countTriplesByFilter(filter);
            if (count <= 0) {
                return 0;
            }
            qdrantVectorService.deleteByFilter(collectionName, filter);
            log.info("按用户删除知识图谱完成: userId={}, 删除数量={}", userId, count);
            return count;
        } catch (Exception e) {
            log.warn("按用户删除知识图谱失败: userId={}", userId, e);
            return -1;
        }
    }

    /**
     * 统计满足过滤条件的三元组数量
     * @param filter
     * @return
     */
    private int countTriplesByFilter(Filter filter) {
        try {
            List<RetrievedPoint> points = qdrantVectorService.scrollPoints(collectionName, filter, 1000);
            return points == null ? 0 : points.size();
        } catch (Exception e) {
            log.warn("统计三元组数量失败", e);
            return 0;
        }
    }

    /**
     * 使用大模型从文本中识别候选实体
     * @param text 查询文本
     * @param maxEntities 最大实体数量
     * @return
     */
    public List<String> recognizeEntitiesByLlm(String text, int maxEntities) {
        if (text == null || text.isBlank() || maxEntities <= 0) {
            return new ArrayList<>();
        }
        if (languageModelFactory == null) {
            log.debug("LanguageModelFactory未注入, LLM实体识别降级为空");
            return new ArrayList<>();
        }
        try {
            String prompt = String.format(ENTITY_EXTRACTION_PROMPT, maxEntities, text);
            String response = languageModelFactory.generateText(entityModelCode, prompt);
            if (response == null || response.isBlank()) {
                return new ArrayList<>();
            }
            return parseEntityResponse(response, maxEntities);
        } catch (Exception e) {
            log.warn("LLM实体识别异常, 降级为空: query={}", text, e);
            return new ArrayList<>();
        }
    }

    /**
     * 使用HanLP分词从文本中识别候选实体
     * @param text 查询文本
     * @param maxEntities 最大实体数量
     * @return
     */
    public List<String> recognizeEntitiesByHanlp(String text, int maxEntities) {
        if (text == null || text.isBlank() || maxEntities <= 0) {
            return new ArrayList<>();
        }
        List<com.hankcs.hanlp.seg.common.Term> terms = HanLP.segment(text);
        Set<String> entities = new LinkedHashSet<>();
        for (com.hankcs.hanlp.seg.common.Term term : terms) {
            String word = term.word.trim();
            if (isValidEntity(word)) {
                entities.add(word);
                if (entities.size() >= maxEntities) {
                    break;
                }
            }
        }
        return new ArrayList<>(entities);
    }

    /**
     * 解析LLM返回的实体列表
     * <p>
     * 支持每行一个实体和JSON数组两种格式。
     * </p>
     * @param response LLM响应
     * @param maxEntities 最大实体数量
     * @return
     */
    private List<String> parseEntityResponse(String response, int maxEntities) {
        Set<String> entities = new LinkedHashSet<>();
        String trimmed = response.trim();

        // 优先尝试JSON数组解析
        if (trimmed.startsWith("[")) {
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                String arrayContent = matcher.group(1);
                String[] items = arrayContent.split(",");
                for (String item : items) {
                    String cleaned = item.trim()
                            .replaceAll("\"", "")
                            .replaceAll("'", "")
                            .trim();
                    if (isValidEntity(cleaned)) {
                        entities.add(cleaned);
                        if (entities.size() >= maxEntities) {
                            break;
                        }
                    }
                }
                if (!entities.isEmpty()) {
                    return new ArrayList<>(entities);
                }
            }
        }

        // 降级为按行解析
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.trim()
                    .replaceAll("^[\\d]+[.、）)]\\s*", "")
                    .replaceAll("^[\\-•*]\\s*", "")
                    .replaceAll("\"", "")
                    .replaceAll("'", "")
                    .trim();
            if (isValidEntity(cleaned)) {
                entities.add(cleaned);
                if (entities.size() >= maxEntities) {
                    break;
                }
            }
        }
        return new ArrayList<>(entities);
    }

    /**
     * 判断是否为有效实体
     * @param entity 待判断字符串
     * @return
     */
    private boolean isValidEntity(String entity) {
        if (entity == null || entity.length() < 2 || entity.length() > 50) {
            return false;
        }
        if (entity.matches("^[\\p{Punct}\\s]+$")) {
            return false;
        }
        List<String> stopWords = Arrays.asList("的", "是", "在", "了", "和", "与", "或", "我", "你", "他", "她", "它", "这", "那");
        return !stopWords.contains(entity);
    }

    /**
     * BFS遍历图谱，支持仅沿因果边或所有边
     * @param startEntity
     * @param userId
     * @param maxHops
     * @param causalOnly
     * @return
     */
    private List<KnowledgeTriple> bfsTraverse(String startEntity, String userId, int maxHops, boolean causalOnly) {
        List<KnowledgeTriple> result = new ArrayList<>();
        Set<String> visitedEntities = new HashSet<>();
        Queue<BfsNode> queue = new LinkedList<>();
        queue.offer(new BfsNode(startEntity, 0));
        visitedEntities.add(startEntity);
        int visitCount = 0;
        while (!queue.isEmpty() && visitCount < MAX_BFS_VISIT) {
            BfsNode current = queue.poll();
            visitCount++;
            if (current.hop >= maxHops) {
                continue;
            }
            List<KnowledgeTriple> outEdges = queryOutEdges(current.entity, userId, causalOnly);
            for (KnowledgeTriple edge : outEdges) {
                result.add(edge);
                String nextEntity = edge.getObject();
                if (nextEntity != null && !nextEntity.isBlank() && !visitedEntities.contains(nextEntity)) {
                    visitedEntities.add(nextEntity);
                    queue.offer(new BfsNode(nextEntity, current.hop + 1));
                }
            }
        }
        return result;
    }

    /**
     * 查询某实体的出边（按subject过滤），causalOnly=true时仅返回因果边
     * @param subject
     * @param userId
     * @param causalOnly
     * @return
     */
    private List<KnowledgeTriple> queryOutEdges(String subject, String userId, boolean causalOnly) {
        try {
            Filter filter = composeSubjectFilter(subject, userId, causalOnly);
            List<RetrievedPoint> points = qdrantVectorService.scrollPoints(collectionName, filter, 50);
            List<KnowledgeTriple> triples = new ArrayList<>(points.size());
            for (RetrievedPoint point : points) {
                KnowledgeTriple triple = extractTripleFromPoint(point);
                if (triple != null && isTripleValid(triple)) {
                    triples.add(triple);
                }
            }
            return triples;
        } catch (Exception e) {
            log.warn("查询出边失败: subject={}, userId={}", subject, userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析LLM返回的三元组JSON数组
     * @param response
     * @param userId
     * @return
     */
    private List<KnowledgeTriple> parseTriples(String response, String userId) {
        String json = stripMarkdownCodeBlock(response).trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("三元组提取响应非JSON数组格式: {}", truncate(json));
            return Collections.emptyList();
        }
        json = json.substring(start, end + 1);
        try {
            JSONArray array = JSON.parseArray(json);
            List<KnowledgeTriple> result = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                KnowledgeTriple triple = parseTriple(obj, userId);
                if (triple != null) {
                    result.add(triple);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析三元组JSON失败: {}", truncate(json), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析单个三元组JSON对象
     * @param obj
     * @param userId
     * @return
     */
    private KnowledgeTriple parseTriple(JSONObject obj, String userId) {
        String subject = obj.getString("subject");
        String predicate = obj.getString("predicate");
        String object = obj.getString("object");
        if (subject == null || subject.isBlank() || predicate == null || predicate.isBlank()
                || object == null || object.isBlank()) {
            return null;
        }
        KnowledgeTriple triple = new KnowledgeTriple();
        triple.setTripleId(UUID.randomUUID().toString());
        triple.setUserId(userId);
        triple.setSubject(subject.trim());
        triple.setPredicate(predicate.trim());
        triple.setObject(object.trim());
        triple.setCausal(obj.getBooleanValue("isCausal"));
        triple.setValidFrom(parseDateTime(obj.getString("validFrom")));
        triple.setValidUntil(parseDateTime(obj.getString("validUntil")));
        Double confidence = obj.getDouble("confidence");
        triple.setConfidence(confidence != null ? confidence : 0.5);
        return triple;
    }

    /**
     * 构建Qdrant向量点
     * @param tripleId
     * @param vector
     * @param triple
     * @param userId
     * @return
     */
    private PointStruct buildPoint(String tripleId, float[] vector, KnowledgeTriple triple, String userId) {
        PointStruct.Builder builder = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(tripleId).build())
                .setVectors(Vectors.newBuilder()
                        .setVector(Vector.newBuilder()
                                .addAllData(toFloatList(vector))
                                .build())
                        .build());
        builder.putPayload(PAYLOAD_KEY_TRIPLE_ID, ValueFactory.value(tripleId));
        builder.putPayload(PAYLOAD_KEY_USER_ID, ValueFactory.value(userId));
        builder.putPayload(PAYLOAD_KEY_SUBJECT, ValueFactory.value(triple.getSubject()));
        builder.putPayload(PAYLOAD_KEY_PREDICATE, ValueFactory.value(triple.getPredicate()));
        builder.putPayload(PAYLOAD_KEY_OBJECT, ValueFactory.value(triple.getObject()));
        builder.putPayload(PAYLOAD_KEY_IS_CAUSAL, ValueFactory.value(triple.isCausal()));
        if (triple.getValidFrom() != null) {
            builder.putPayload(PAYLOAD_KEY_VALID_FROM,
                    ValueFactory.value(triple.getValidFrom().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        }
        if (triple.getValidUntil() != null) {
            builder.putPayload(PAYLOAD_KEY_VALID_UNTIL,
                    ValueFactory.value(triple.getValidUntil().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        }
        return builder.build();
    }

    /**
     * 构建按subject过滤的Qdrant Filter（同时过滤userId和时序有效性，causalOnly=true时仅返回因果边）
     * @param subject
     * @param userId
     * @param causalOnly
     * @return
     */
    private Filter composeSubjectFilter(String subject, String userId, boolean causalOnly) {
        Filter.Builder builder = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey(PAYLOAD_KEY_SUBJECT)
                                .setMatch(Match.newBuilder().setKeyword(subject).build())
                                .build())
                        .build())
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey(PAYLOAD_KEY_USER_ID)
                                .setMatch(Match.newBuilder().setKeyword(userId).build())
                                .build())
                        .build());
        if (causalOnly) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(PAYLOAD_KEY_IS_CAUSAL)
                            .setMatch(Match.newBuilder().setBoolean(true).build())
                            .build())
                    .build());
        }
        return builder.build();
    }

    /**
     * 从RetrievedPoint中提取三元组
     * @param point
     * @return
     */
    private KnowledgeTriple extractTripleFromPoint(RetrievedPoint point) {
        if (point == null || point.getPayloadMap() == null) {
            return null;
        }
        var payload = point.getPayloadMap();
        KnowledgeTriple triple = new KnowledgeTriple();
        if (payload.containsKey(PAYLOAD_KEY_TRIPLE_ID)) {
            triple.setTripleId(payload.get(PAYLOAD_KEY_TRIPLE_ID).getStringValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_USER_ID)) {
            triple.setUserId(payload.get(PAYLOAD_KEY_USER_ID).getStringValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_SUBJECT)) {
            triple.setSubject(payload.get(PAYLOAD_KEY_SUBJECT).getStringValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_PREDICATE)) {
            triple.setPredicate(payload.get(PAYLOAD_KEY_PREDICATE).getStringValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_OBJECT)) {
            triple.setObject(payload.get(PAYLOAD_KEY_OBJECT).getStringValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_IS_CAUSAL)) {
            triple.setCausal(payload.get(PAYLOAD_KEY_IS_CAUSAL).getBoolValue());
        }
        if (payload.containsKey(PAYLOAD_KEY_VALID_FROM)) {
            triple.setValidFrom(parseDateTime(payload.get(PAYLOAD_KEY_VALID_FROM).getStringValue()));
        }
        if (payload.containsKey(PAYLOAD_KEY_VALID_UNTIL)) {
            triple.setValidUntil(parseDateTime(payload.get(PAYLOAD_KEY_VALID_UNTIL).getStringValue()));
        }
        return triple;
    }

    /**
     * 判断三元组时序是否有效（validUntil为空或晚于当前时间）
     * @param triple
     * @return
     */
    private boolean isTripleValid(KnowledgeTriple triple) {
        if (triple.getValidUntil() == null) {
            return true;
        }
        return triple.getValidUntil().isAfter(LocalDateTime.now());
    }

    /**
     * 判断三元组字段是否完整
     * @param triple
     * @return
     */
    private boolean isValidTriple(KnowledgeTriple triple) {
        return triple != null
                && triple.getSubject() != null && !triple.getSubject().isBlank()
                && triple.getPredicate() != null && !triple.getPredicate().isBlank()
                && triple.getObject() != null && !triple.getObject().isBlank();
    }

    /**
     * 调用嵌入模型生成向量
     * @param text
     * @return
     */
    private float[] embed(String text) {
        EmbeddingClient client = languageModelFactory.getTextEmbeddingClient(embeddingModelCode);
        return client.embed(text);
    }

    /**
     * 确保Qdrant集合存在
     * @param vectorSize
     */
    private void ensureCollectionExists(int vectorSize) {
        if (collectionInitialized) {
            return;
        }
        synchronized (this) {
            if (collectionInitialized) {
                return;
            }
            if (!qdrantVectorService.collectionExists(collectionName)) {
                qdrantVectorService.createCollection(collectionName, vectorSize, DEFAULT_DISTANCE);
                log.info("创建知识图谱集合: {}, 维度: {}", collectionName, vectorSize);
            }
            collectionInitialized = true;
        }
    }

    /**
     * 去除markdown代码块标记
     * @param text
     * @return
     */
    private String stripMarkdownCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed;
    }

    /**
     * 解析ISO格式时间字符串
     * @param value
     * @return
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : ISO_FORMATS) {
            try {
                if (formatter == DateTimeFormatter.ofPattern("yyyy-MM-dd")) {
                    return java.time.LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (Exception e) {
                // try next format
            }
        }
        log.debug("无法解析时间字符串: {}", value);
        return null;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /**
     * BFS遍历节点
     */
    private static class BfsNode {
        final String entity;
        final int hop;

        BfsNode(String entity, int hop) {
            this.entity = entity;
            this.hop = hop;
        }
    }
}
