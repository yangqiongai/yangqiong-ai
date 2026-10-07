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
package com.yangqiongai.ai.open.capability.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力调用仓储
 * @author yangqiong
 */
class CapabilityCallRepositoryTest {

    private CapabilityCallRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCapabilityCallRepository();
    }

    @Test
    void testSaveAndFindById() {
        CapabilityCallRecord record = new CapabilityCallRecord();
        record.setId("call-001");
        record.setCapability("project-overview");
        record.setCaller("user-001");
        record.setStatus("success");

        repository.save(record);

        CapabilityCallRecord result = repository.findById("call-001");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("call-001");
        assertThat(result.getCapability()).isEqualTo("project-overview");
        assertThat(result.getCaller()).isEqualTo("user-001");
        assertThat(result.getStatus()).isEqualTo("success");
    }

    @Test
    void testFindByCapability() {
        CapabilityCallRecord record1 = new CapabilityCallRecord();
        record1.setId("call-001");
        record1.setCapability("project-overview");

        CapabilityCallRecord record2 = new CapabilityCallRecord();
        record2.setId("call-002");
        record2.setCapability("project-overview");

        CapabilityCallRecord record3 = new CapabilityCallRecord();
        record3.setId("call-003");
        record3.setCapability("contract-audit");

        repository.save(record1);
        repository.save(record2);
        repository.save(record3);

        List<CapabilityCallRecord> result = repository.findByCapability("project-overview");
        assertThat(result).hasSize(2)
                .extracting(CapabilityCallRecord::getId)
                .containsExactlyInAnyOrder("call-001", "call-002");
    }

    @Test
    void testFindAll() {
        CapabilityCallRecord record1 = new CapabilityCallRecord();
        record1.setId("call-001");
        record1.setCapability("project-overview");

        CapabilityCallRecord record2 = new CapabilityCallRecord();
        record2.setId("call-002");
        record2.setCapability("contract-audit");

        repository.save(record1);
        repository.save(record2);

        List<CapabilityCallRecord> result = repository.findAll();
        assertThat(result).hasSize(2);
    }

    @Test
    void testSaveWithNullId() {
        CapabilityCallRecord record = new CapabilityCallRecord();
        record.setCapability("project-overview");

        repository.save(record);

        // save with null id should be no-op
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void testSaveNullRecord() {
        repository.save(null);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void testFindByIdNonExistent() {
        CapabilityCallRecord result = repository.findById("non-existent");
        assertThat(result).isNull();
    }

    @Test
    void testFindByCapabilityNonExistent() {
        repository.save(createRecord("call-001", "project-overview"));

        List<CapabilityCallRecord> result = repository.findByCapability("non-existent");
        assertThat(result).isEmpty();
    }

    @Test
    void testFindAllEmpty() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void testSaveOverwritesExisting() {
        CapabilityCallRecord record1 = createRecord("call-001", "project-overview");
        record1.setStatus("success");
        repository.save(record1);

        CapabilityCallRecord record2 = createRecord("call-001", "project-overview");
        record2.setStatus("failure");
        repository.save(record2);

        CapabilityCallRecord result = repository.findById("call-001");
        assertThat(result.getStatus()).isEqualTo("failure");
    }

    private CapabilityCallRecord createRecord(String id, String capability) {
        CapabilityCallRecord record = new CapabilityCallRecord();
        record.setId(id);
        record.setCapability(capability);
        return record;
    }

    /**
     * 内存能力调用仓储
     * @author yangqiong
     */
    private static class InMemoryCapabilityCallRepository implements CapabilityCallRepository {

        private final Map<String, CapabilityCallRecord> store = new ConcurrentHashMap<>();

        @Override
        public void save(CapabilityCallRecord record) {
            if (record != null && record.getId() != null) {
                store.put(record.getId(), record);
            }
        }

        @Override
        public CapabilityCallRecord findById(String id) {
            return store.get(id);
        }

        @Override
        public List<CapabilityCallRecord> findByCapability(String capability) {
            List<CapabilityCallRecord> result = new ArrayList<>();
            for (CapabilityCallRecord record : store.values()) {
                if (capability.equals(record.getCapability())) {
                    result.add(record);
                }
            }
            return result;
        }

        @Override
        public List<CapabilityCallRecord> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}