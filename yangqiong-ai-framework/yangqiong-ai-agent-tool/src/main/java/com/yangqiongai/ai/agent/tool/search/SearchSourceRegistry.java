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
package com.yangqiongai.ai.agent.tool.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 搜索源注册中心
 * <p>
 * 管理所有可用的搜索源，支持按名称查找和遍历。
 * 搜索源通过 Spring 自动注入（实现 {@link SearchSource} 接口并加 @Component）。
 * </p>
 * @author yangqiong
 */
@Component
public class SearchSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(SearchSourceRegistry.class);

    private final Map<String, SearchSource> sources = new LinkedHashMap<>();

    public SearchSourceRegistry(List<SearchSource> searchSources) {
        if (searchSources != null) {
            for (SearchSource source : searchSources) {
                register(source);
            }
        }
    }

    /**
     * 注册搜索源
     * @param source
     */
    public void register(SearchSource source) {
        if (source == null || source.getName() == null || source.getName().isBlank()) {
            return;
        }
        sources.put(source.getName().toLowerCase(), source);
        log.info("注册搜索源: name={}, class={}", source.getName(), source.getClass().getSimpleName());
    }

    /**
     * 按名称获取搜索源
     * @param name
     * @return
     */
    public SearchSource get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return sources.get(name.toLowerCase());
    }

    /**
     * 获取默认搜索源（第一个注册的）
     * @return
     */
    public SearchSource getDefault() {
        return sources.values().stream().findFirst().orElse(null);
    }

    /**
     * 获取所有已注册的搜索源名称
     * @return
     */
    public List<String> getNames() {
        return new ArrayList<>(sources.keySet());
    }

    /**
     * 是否有可用的搜索源
     * @return
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /**
     * 获取所有已注册的搜索源（按注册顺序）
     * @return
     */
    public List<SearchSource> getAll() {
        return new ArrayList<>(sources.values());
    }

    /**
     * 获取排除指定名称后的搜索源列表（按注册顺序）
     * @param excludeNames 需要排除的搜索源名称
     * @return
     */
    public List<SearchSource> getOthers(String... excludeNames) {
        Set<String> excludeSet = new HashSet<>();
        if (excludeNames != null) {
            for (String name : excludeNames) {
                if (name != null) {
                    excludeSet.add(name.toLowerCase());
                }
            }
        }
        List<SearchSource> result = new ArrayList<>();
        for (Map.Entry<String, SearchSource> entry : sources.entrySet()) {
            if (!excludeSet.contains(entry.getKey())) {
                result.add(entry.getValue());
            }
        }
        return result;
    }
}
