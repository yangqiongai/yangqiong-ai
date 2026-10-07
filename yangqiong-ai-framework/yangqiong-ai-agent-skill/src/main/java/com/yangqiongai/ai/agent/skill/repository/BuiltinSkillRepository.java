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
package com.yangqiongai.ai.agent.skill.repository;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import com.yangqiongai.ai.agent.skill.parser.SkillMetadataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置技能仓库
 * @author yangqiong
 */
public class BuiltinSkillRepository implements SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillRepository.class);

    /**
     * 技能内存表(classpath技能不可变，首次加载后常驻，无淘汰路径)
     */
    private final Map<String, SkillDefinition> cache = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    loadFromClasspath();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 从classpath加载技能文件和资源文件
     * <p>
     * 一技能一目录，结构如下：
     * skills/
     *   my-skill/                  ← 技能目录（目录名即skillId）
     *     my-skill.md              ← 技能定义
     *     resources/               ← 资源文件目录
     *       schema.sql             ← 资源文件
     *       controller-spec.json          ← 资源文件
     * </p>
     */
    private void loadFromClasspath() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var mdResources = resolver.getResources("classpath*:skills/**/*.md");
            for (var resource : mdResources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                String urlStr = resource.getURL().toString();
                // resources/目录下的md属于资源文件，跳过防止以相同skillId覆盖主定义
                if (isUnderResourcesDir(urlStr)) {
                    continue;
                }

                String skillId = extractSkillId(urlStr, filename);
                String content;
                try (var is = resource.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                SkillDefinition skill = SkillMetadataParser.parse(content, skillId);

                // 加载同名目录下 resources/ 中的资源文件
                Map<String, String> fileResources = loadResourcesForSkill(resolver, skillId);
                if (!fileResources.isEmpty()) {
                    if (skill.getResources() == null || skill.getResources().isEmpty()) {
                        skill.setResources(fileResources);
                    } else {
                        Map<String, String> merged = new LinkedHashMap<>(fileResources);
                        merged.putAll(skill.getResources());
                        skill.setResources(merged);
                    }
                }

                cache.put(skillId, skill);
                log.info("加载内置技能 {} (resources={})", skillId, fileResources.size());
            }
        } catch (IOException e) {
            log.warn("加载classpath技能失败 {}", e.getMessage());
        }
    }

    /**
     * 判断md文件是否位于技能的resources资源目录下
     * <p>
     * resources/目录下的md文件属于资源文件，不能作为技能主定义参与扫描，
     * 否则会以相同skillId覆盖主定义内容。
     * </p>
     * @param urlStr
     * @return
     */
    private boolean isUnderResourcesDir(String urlStr) {
        int skillsIdx = urlStr.lastIndexOf("skills/");
        if (skillsIdx < 0) {
            return false;
        }
        String afterSkills = urlStr.substring(skillsIdx + "skills/".length());
        return afterSkills.startsWith("resources/") || afterSkills.contains("/resources/");
    }

    /**
     * 从URL路径提取skillId
     * <p>
     * 路径格式：.../skills/{skillId}/{filename}.md
     * 提取 skills/ 之后、文件名之前的那一段作为skillId。
     * 兼容扁平结构 .../skills/{skillId}.md（skillId = 去掉.md后缀的文件名）。
     * </p>
     * @param urlStr
     * @param filename
     * @return
     */
    private String extractSkillId(String urlStr, String filename) {
        int skillsIdx = urlStr.lastIndexOf("skills/");
        if (skillsIdx >= 0) {
            String afterSkills = urlStr.substring(skillsIdx + "skills/".length());
            int slashIdx = afterSkills.indexOf('/');
            if (slashIdx > 0) {
                return afterSkills.substring(0, slashIdx);
            }
        }
        return filename.replace(".md", "");
    }

    /**
     * 加载技能的资源文件
     * <p>
     * 扫描 skills/{skillId}/resources/ 下的所有文件，
     * key为相对路径（不含 skills/{skillId}/resources/ 前缀），value为文件内容。
     * </p>
     * @param resolver
     * @param skillId
     * @return
     */
    private Map<String, String> loadResourcesForSkill(PathMatchingResourcePatternResolver resolver, String skillId) {
        Map<String, String> fileResources = new LinkedHashMap<>();
        try {
            String location = "classpath*:skills/" + skillId + "/resources/**";
            var resourceFiles = resolver.getResources(location);
            String prefix = "skills/" + skillId + "/resources/";
            for (var resFile : resourceFiles) {
                String resFilename = resFile.getFilename();
                if (resFilename == null) continue;
                if (!resFile.isReadable()) continue;
                String resPath;
                String urlStr = resFile.getURL().toString();
                int idx = urlStr.indexOf(prefix);
                if (idx >= 0) {
                    resPath = urlStr.substring(idx + prefix.length());
                } else {
                    resPath = resFilename;
                }
                try (var is = resFile.getInputStream()) {
                    String fileContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    fileResources.put(resPath, fileContent);
                }
            }
        } catch (IOException e) {
            log.debug("技能 {} 无资源文件目录: {}", skillId, e.getMessage());
        }
        return fileResources;
    }

    /**
     * 根据ID查找技能
     * @param skillId
     * @return
     */
    @Override
    public Optional<SkillDefinition> findById(String skillId) {
        ensureInitialized();
        return Optional.ofNullable(cache.get(skillId));
    }

    /**
     * 查找所有技能
     * @return
     */
    @Override
    public List<SkillDefinition> findAll() {
        ensureInitialized();
        return new ArrayList<>(cache.values());
    }

    /**
     * 按信任等级查找技能
     * @param trustLevel
     * @return
     */
    @Override
    public List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel) {
        return findAll().stream()
                .filter(s -> s.getTrustLevel() == trustLevel)
                .toList();
    }

    /**
     * 保存技能
     * @param skill
     */
    @Override
    public void save(SkillDefinition skill) {
        cache.put(skill.getSkillId(), skill);
    }

    /**
     * 删除技能
     * @param skillId
     */
    @Override
    public void deleteById(String skillId) {
        cache.remove(skillId);
    }
}
