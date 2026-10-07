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
package com.yangqiongai.ai.platform.connector.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SQL安全校验单元测试")
class SqlSafetyValidatorTest {

    @Test
    @DisplayName("空SQL拒绝")
    void shouldRejectBlankSql() {
        assertThat(SqlSafetyValidator.checkReadOnly(null)).isEqualTo("SQL不能为空");
        assertThat(SqlSafetyValidator.checkReadOnly("  ")).isEqualTo("SQL不能为空");
        assertThat(SqlSafetyValidator.checkReadOnly("-- 只有注释")).isEqualTo("SQL不能为空");
        assertThat(SqlSafetyValidator.checkReadOnly("/* 块注释 */")).isEqualTo("SQL不能为空");
    }

    @Test
    @DisplayName("允许的只读语句通过")
    void shouldAllowReadOnlyStatements() {
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("select id from t_user")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("SHOW TABLES")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("DESC t_user")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("DESCRIBE t_user")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("EXPLAIN SELECT 1")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("WITH c AS (SELECT 1 AS x) SELECT x FROM c")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("/* 注释 */ SELECT 1")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1 -- 行注释\n FROM t")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT * FROM `t_user` WHERE name = 'a;b'")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1;")).isNull();
    }

    @Test
    @DisplayName("写操作与管理操作拒绝")
    void shouldRejectWriteStatements() {
        assertThat(SqlSafetyValidator.checkReadOnly("DELETE FROM t_user")).contains("DELETE");
        assertThat(SqlSafetyValidator.checkReadOnly("UPDATE t_user SET name='a'")).contains("UPDATE");
        assertThat(SqlSafetyValidator.checkReadOnly("INSERT INTO t_user VALUES(1)")).contains("INSERT");
        assertThat(SqlSafetyValidator.checkReadOnly("TRUNCATE TABLE t_user")).contains("TRUNCATE");
        assertThat(SqlSafetyValidator.checkReadOnly("DROP TABLE t_user")).contains("DROP");
        assertThat(SqlSafetyValidator.checkReadOnly("CREATE TABLE t2(id INT)")).contains("CREATE");
        assertThat(SqlSafetyValidator.checkReadOnly("GRANT ALL ON *.* TO u")).contains("GRANT");
        assertThat(SqlSafetyValidator.checkReadOnly("SET @x=1")).contains("SET");
        assertThat(SqlSafetyValidator.checkReadOnly("USE other_db")).contains("USE");
        assertThat(SqlSafetyValidator.checkReadOnly("CALL proc()")).contains("CALL");
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE/DUMPFILE服务端写文件拒绝")
    void shouldRejectSelectIntoFile() {
        assertThat(SqlSafetyValidator.checkReadOnly(
                "SELECT * FROM t_user INTO OUTFILE '/var/data.txt'")).contains("OUTFILE");
        assertThat(SqlSafetyValidator.checkReadOnly(
                "SELECT * FROM t_user INTO DUMPFILE '/var/data.bin'")).contains("DUMPFILE");
        assertThat(SqlSafetyValidator.checkReadOnly(
                "select id into outfile '/x' from t_user")).contains("OUTFILE");
        // 注释伪装不生效
        assertThat(SqlSafetyValidator.checkReadOnly(
                "SELECT /*x*/ * FROM t INTO /*!*/ OUTFILE '/x'")).contains("OUTFILE");
    }

    @Test
    @DisplayName("多语句拒绝且字面量分号不误判")
    void shouldRejectMultiStatementButAllowLiteralSemicolon() {
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1; DELETE FROM t_user")).contains("多语句");
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1;; SELECT 2")).contains("多语句");
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1;\nSELECT 2")).contains("多语句");
        // 注释中藏分号与DML不生效
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1 /*;DROP TABLE t*/")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("SELECT 1 -- ;DROP TABLE t\n")).isNull();
    }

    @Test
    @DisplayName("首词前注释剥离后正确判定")
    void shouldStripLeadingCommentsBeforeFirstWord() {
        assertThat(SqlSafetyValidator.checkReadOnly("/* DELETE */ SELECT 1")).isNull();
        assertThat(SqlSafetyValidator.checkReadOnly("-- DROP\nDELETE FROM t_user")).contains("DELETE");
    }

    @Test
    @DisplayName("SQL白名单命中与放行")
    void shouldPassWhitelistedTables() {
        List<String> whitelist = List.of("t_user", "order_info");
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM t_user", whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM db1.t_user", whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM `T_USER`", whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM t_user u JOIN order_info o ON u.id=o.uid",
                whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT 1", whitelist)).isNull();
    }

    @Test
    @DisplayName("SQL白名单外表拒绝")
    void shouldRejectTableOutsideWhitelist() {
        List<String> whitelist = List.of("t_user");
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM t_secret", whitelist))
                .isEqualTo("表t_secret不在白名单内");
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM db1.t_secret", whitelist))
                .isEqualTo("表db1.t_secret不在白名单内");
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM t_user JOIN t_secret ON 1=1", whitelist))
                .isEqualTo("表t_secret不在白名单内");
    }

    @Test
    @DisplayName("空白名单不限制")
    void shouldSkipWhitelistWhenEmpty() {
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM any_table", null)).isNull();
        assertThat(SqlSafetyValidator.checkWhitelist("SELECT * FROM any_table", List.of())).isNull();
    }

    @Test
    @DisplayName("表清单白名单校验（describe_tables用）")
    void shouldCheckTableListAgainstWhitelist() {
        List<String> whitelist = List.of("t_user");
        assertThat(SqlSafetyValidator.checkTables(List.of("t_user"), whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkTables(List.of("db1.t_user"), whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkTables(List.of("t_secret"), whitelist))
                .isEqualTo("表t_secret不在白名单内");
        assertThat(SqlSafetyValidator.checkTables(List.of("t_user", "t_secret"), whitelist))
                .isEqualTo("表t_secret不在白名单内");
        assertThat(SqlSafetyValidator.checkTables(null, whitelist)).isNull();
        assertThat(SqlSafetyValidator.checkTables(List.of("any"), List.of())).isNull();
    }
}
