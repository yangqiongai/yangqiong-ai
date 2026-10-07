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
package com.yangqiongai.ai.agent.local.workspace;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.repository.UserWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户工作区管理测试
 * @author yangqiong
 */
class UserWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private UserWorkspaceService service;

    private WorkspaceProperties properties;

    private List<WorkspaceInfo> store;

    @BeforeEach
    void setUp() throws Exception {
        service = new UserWorkspaceService();
        properties = new WorkspaceProperties();
        properties.setEnabled(true);
        store = new ArrayList<>();
        UserWorkspaceRepository repository = new InMemoryRepository(store);

        inject(service, "userWorkspaceRepository", repository);
        inject(service, "workspaceProperties", properties);
    }

    /**
     * 创建成功并存储canonical路径
     */
    @Test
    void createStoresCanonicalPath() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "我的工作区", dir.toString(), "描述", "MANUAL", false);
        assertEquals("我的工作区", info.getName());
        assertEquals(dir.toRealPath().toString(), info.getRootPath());
        assertEquals("MANUAL", info.getApprovalMode());
        assertEquals(1, store.size());
    }

    /**
     * 目录不存在且未开autoCreate时报错，开启后自动创建
     */
    @Test
    void createAutoCreatesOnlyWhenRequested() {
        Path missing = tempDir.resolve("auto").resolve("ws");
        assertThrows(IllegalArgumentException.class,
                () -> service.create("u1", "w", missing.toString(), null, null, false));
        WorkspaceInfo info = service.create("u1", "w", missing.toString(), null, null, true);
        assertTrue(Files.isDirectory(Path.of(info.getRootPath())));
    }

    /**
     * 空名称/空路径/非法审批层级/超上限均拒绝
     */
    @Test
    void createValidatesInput() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        assertThrows(IllegalArgumentException.class, () -> service.create("u1", " ", dir.toString(), null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.create("u1", "w", " ", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.create("u1", "w", dir.toString(), null, "BAD", false));

        properties.setMaxPerUser(1);
        service.create("u1", "w1", dir.toString(), null, null, false);
        Path dir2 = Files.createDirectories(tempDir.resolve("ws2"));
        assertThrows(IllegalArgumentException.class, () -> service.create("u1", "w2", dir2.toString(), null, null, false));
    }

    /**
     * 白名单根限制：根内放行、根外拒绝、未配置白名单不限
     */
    @Test
    void createEnforcesRootsWhitelist() throws Exception {
        Path allowed = Files.createDirectories(tempDir.resolve("allowed"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        properties.setRoots(List.of(allowed.toString()));

        assertThrows(IllegalArgumentException.class, () -> service.create("u1", "w", outside.toString(), null, null, false));
        WorkspaceInfo ok = service.create("u1", "w", allowed.toString(), null, null, false);
        assertEquals(allowed.toRealPath().toString(), ok.getRootPath());
    }

    /**
     * 同一根路径全局唯一
     */
    @Test
    void createRejectsDuplicateRoot() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        service.create("u1", "w1", dir.toString(), null, null, false);
        assertThrows(IllegalArgumentException.class, () -> service.create("u2", "w2", dir.toString(), null, null, false));
        assertEquals(1, store.size());
    }

    /**
     * 更新与删除必须归属本人
     */
    @Test
    void updateAndDeleteRequireOwnership() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "w", dir.toString(), null, null, false);

        assertThrows(IllegalArgumentException.class, () -> service.update(info.getId(), "u2", "改名", null, null, null));
        service.update(info.getId(), "u1", "改名", null, "AUTO", 0);
        assertEquals("改名", store.get(0).getName());
        assertEquals("AUTO", store.get(0).getApprovalMode());
        assertEquals(0, store.get(0).getStatus());

        assertThrows(IllegalArgumentException.class, () -> service.delete(info.getId(), "u2"));
        service.delete(info.getId(), "u1");
        assertTrue(store.isEmpty());
    }

    /**
     * 非法审批层级更新被拒绝
     */
    @Test
    void updateRejectsInvalidApprovalMode() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "w", dir.toString(), null, null, false);
        assertThrows(IllegalArgumentException.class, () -> service.update(info.getId(), "u1", null, null, "BAD", null));
    }

    /**
     * resolveEnabled：未启用/非本人/停用/目录被移动均返回null
     */
    @Test
    void resolveEnabledValidatesAllConditions() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "w", dir.toString(), null, null, false);

        properties.setEnabled(false);
        assertNull(service.resolveEnabled(info.getId(), "u1"));
        properties.setEnabled(true);
        assertEquals(info.getId(), service.resolveEnabled(info.getId(), "u1").getId());
        assertNull(service.resolveEnabled(info.getId(), "u2"));
        assertNull(service.resolveEnabled(null, "u1"));

        service.update(info.getId(), "u1", null, null, null, 0);
        assertNull(service.resolveEnabled(info.getId(), "u1"));

        // 目录被移动后canonical不一致，注入降级
        service.update(info.getId(), "u1", null, null, null, 1);
        Path moved = Files.createDirectories(tempDir.resolve("moved"));
        store.get(0).setRootPath(moved.resolve("ghost").toString());
        assertNull(service.resolveEnabled(info.getId(), "u1"));
    }

    /**
     * resolveEnabled在白名单配置下拒绝越界目录
     */
    @Test
    void resolveEnabledEnforcesRuntimeWhitelist() throws Exception {
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        WorkspaceInfo info = service.create("u1", "w", outside.toString(), null, null, false);
        properties.setRoots(List.of(tempDir.resolve("elsewhere").toString()));
        assertNull(service.resolveEnabled(info.getId(), "u1"));
    }

    /**
     * resolveEnabled：虚拟根（连接器/浏览器桥）跳过本地路径校验直接放行，路径安全由网关执行端约束
     */
    @Test
    void resolveEnabledAllowsVirtualRoot() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "w", dir.toString(), null, null, false);
        store.get(0).setRootPath("connector://2106246710350061569/root_cc59fcdcb64e");

        WorkspaceInfo resolved = service.resolveEnabled(info.getId(), "u1");

        assertNotNull(resolved);
        assertEquals("connector://2106246710350061569/root_cc59fcdcb64e", resolved.getRootPath());
    }

    /**
     * 文件树懒加载：目录排前、按名排序、返回相对路径
     */
    @Test
    void treeListsOneLevelSorted() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(root.resolve("b-dir"));
        Files.createDirectories(root.resolve("a-dir"));
        Files.writeString(root.resolve("z.txt"), "zz");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        List<Map<String, Object>> nodes = service.tree(info.getId(), "u1", null);
        assertEquals(3, nodes.size());
        assertEquals("a-dir", nodes.get(0).get("name"));
        assertEquals("b-dir", nodes.get(1).get("name"));
        assertEquals("z.txt", nodes.get(2).get("name"));
        assertEquals(Boolean.FALSE, nodes.get(2).get("dir"));
        assertThrows(IllegalArgumentException.class, () -> service.tree(info.getId(), "u2", null));
    }

    /**
     * 路径穿越被拒绝
     */
    @Test
    void treeRejectsPathTraversal() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(tempDir.resolve("secret"));
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);
        assertThrows(IllegalArgumentException.class,
                () -> service.tree(info.getId(), "u1", "..\\secret"));
        assertThrows(IllegalArgumentException.class,
                () -> service.tree(info.getId(), "u1", "../secret"));
        assertThrows(IllegalArgumentException.class,
                () -> service.tree(info.getId(), "u1", "not-exists"));
    }

    /**
     * 子目录懒加载：path必须带父目录前缀，避免与根目录同名文件key冲突
     */
    @Test
    void treeChildPathsIncludeParentPrefix() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(root.resolve("notes"));
        Files.writeString(root.resolve("d1.txt"), "root");
        Files.writeString(root.resolve("notes").resolve("d1.txt"), "child");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        List<Map<String, Object>> children = service.tree(info.getId(), "u1", "notes");
        assertEquals(1, children.size());
        assertEquals("notes/d1.txt", children.get(0).get("path"));
        assertEquals("d1.txt", children.get(0).get("name"));
    }

    /**
     * 创建文件夹：非法名称/父目录缺失/同名冲突拒绝，成功返回带/分隔路径
     */
    @Test
    void createDirectoryValidatesAndReturnsPath() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(root.resolve("notes"));
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        assertThrows(IllegalArgumentException.class, () -> service.createDirectory(info.getId(), "u1", "", "../bad"));
        assertThrows(IllegalArgumentException.class, () -> service.createDirectory(info.getId(), "u1", "missing", "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createDirectory(info.getId(), "u2", "", "a"));

        Map<String, Object> result = service.createDirectory(info.getId(), "u1", "notes", "新目录");
        assertEquals("notes/新目录", result.get("path"));
        assertTrue(Files.isDirectory(root.resolve("notes").resolve("新目录")));
        assertThrows(IllegalArgumentException.class, () -> service.createDirectory(info.getId(), "u1", "notes", "新目录"));
    }

    /**
     * 移动文件与目录：同名校验/目标冲突拒绝/移动到自身内部拒绝，成功后原位置不存在
     */
    @Test
    void moveFileMovesAndValidates() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(root.resolve("notes"));
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("a.txt"), "content");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.moveFile(info.getId(), "u1", "a.txt", "notes");
        assertEquals("notes/a.txt", result.get("path"));
        assertFalse(Files.exists(root.resolve("a.txt")));
        assertTrue(Files.exists(root.resolve("notes").resolve("a.txt")));

        assertThrows(IllegalArgumentException.class, () -> service.moveFile(info.getId(), "u1", "sub", "sub"));
        assertThrows(IllegalArgumentException.class, () -> service.moveFile(info.getId(), "u1", "missing", "notes"));
        assertThrows(IllegalArgumentException.class, () -> service.moveFile(info.getId(), "u2", "notes", ""));
    }

    /**
     * 文件预览：返回内容、目录拒绝、超限截断
     */
    @Test
    void previewReadsFileContent() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "hello 中文");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.preview(info.getId(), "u1", "a.txt");
        assertEquals("hello 中文", result.get("content"));
        assertEquals(Boolean.FALSE, result.get("truncated"));

        assertThrows(IllegalArgumentException.class, () -> service.preview(info.getId(), "u1", "."));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(info.getId(), "u1", "..\\..\\outside.txt"));
    }

    /**
     * 功能未启用时全部入口拒绝
     */
    @Test
    void allEntriesRequireEnabledFlag() throws Exception {
        properties.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.list("u1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("u1", "w", tempDir.toString(), null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, "u1", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1L, "u1"));
        assertThrows(IllegalArgumentException.class, () -> service.tree(1L, "u1", null));
        assertThrows(IllegalArgumentException.class, () -> service.preview(1L, "u1", "a.txt"));
    }

    /**
     * 伪xlsx（CSV内容伪装）降级为文本表格解析，保留表头与行数
     */
    @Test
    void previewTextSpreadsheetFallsBackToPlainTextParsing() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("holidays.xlsx"), "节日名称,放假开始日期\n元旦,2026-01-01\n春节,2026-02-15\n");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.preview(info.getId(), "u1", "holidays.xlsx");
        assertEquals("excel", result.get("type"));
        Map<String, Object> workbook = (Map<String, Object>) result.get("workbook");
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) workbook.get("sheets");
        assertEquals(1, sheets.size());
        assertEquals(List.of("节日名称", "放假开始日期"), sheets.get(0).get("header"));
        assertEquals(3, sheets.get(0).get("rowCount"));
        List<List<Object>> rows = (List<List<Object>>) sheets.get(0).get("rows");
        assertEquals(List.of("元旦", "2026-01-01"), rows.get(0));
    }

    /**
     * 真xlsx（PK魔数）走POI解析，PK魔数但非工作簿时报解析失败
     */
    @Test
    void previewBinarySpreadsheetUsesPoi() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Path real = root.resolve("real.xlsx");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = Files.newOutputStream(real)) {
            var sheet = wb.createSheet("数据");
            sheet.createRow(0).createCell(0).setCellValue("名称");
            wb.write(out);
        }
        Path fake = root.resolve("fake.xlsx");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(fake))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zos.write("plain zip".getBytes());
            zos.closeEntry();
        }
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.preview(info.getId(), "u1", "real.xlsx");
        assertEquals("excel", result.get("type"));
        Map<String, Object> workbook = (Map<String, Object>) result.get("workbook");
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) workbook.get("sheets");
        assertEquals("数据", sheets.get(0).get("name"));

        assertThrows(IllegalArgumentException.class, () -> service.preview(info.getId(), "u1", "fake.xlsx"));
    }

    /**
     * CSV双引号转义与TSV制表符分隔
     */
    @Test
    void previewParsesQuotedCsvAndTsv() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("q.csv"), "名称,备注\n\"张三\",\"会说\"\"你好\"\"\"\n李四,普通");
        Files.writeString(root.resolve("t.tsv"), "列A\t列B\n1\t2");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> csv = service.preview(info.getId(), "u1", "q.csv");
        Map<String, Object> csvWb = (Map<String, Object>) csv.get("workbook");
        List<Map<String, Object>> csvSheets = (List<Map<String, Object>>) csvWb.get("sheets");
        List<List<Object>> csvRows = (List<List<Object>>) csvSheets.get(0).get("rows");
        assertEquals("会说\"你好\"", csvRows.get(0).get(1));
        assertEquals("普通", csvRows.get(1).get(1));

        Map<String, Object> tsv = service.preview(info.getId(), "u1", "t.tsv");
        Map<String, Object> tsvWb = (Map<String, Object>) tsv.get("workbook");
        List<Map<String, Object>> tsvSheets = (List<Map<String, Object>>) tsvWb.get("sheets");
        List<Object> tsvHeader = (List<Object>) tsvSheets.get(0).get("header");
        assertEquals(List.of("列A", "列B"), tsvHeader);
    }

    /**
     * docx预览返回段落与表格结构（标题样式识别）
     */
    @Test
    void previewDocxReturnsStructuredDocument() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Path doc = root.resolve("note.docx");
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var out = Files.newOutputStream(doc)) {
            var title = document.createParagraph();
            title.getCTP().addNewPPr().addNewPStyle().setVal("Heading1");
            title.createRun().setText("周报");
            var body = document.createParagraph();
            body.createRun().setText("本周完成工作台");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("任务");
            document.write(out);
        }
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.preview(info.getId(), "u1", "note.docx");
        assertEquals("docx", result.get("type"));
        Map<String, Object> documentData = (Map<String, Object>) result.get("document");
        List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) documentData.get("paragraphs");
        assertEquals(2, paragraphs.size());
        assertEquals("heading", paragraphs.get(0).get("type"));
        assertEquals("周报", paragraphs.get(0).get("text"));
        List<Map<String, Object>> tables = (List<Map<String, Object>>) documentData.get("tables");
        assertEquals(1, tables.size());
        assertTrue(String.valueOf(result.get("content")).startsWith("data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,"));
    }

    /**
     * zip预览返回条目清单，pdf返回base64内联，Office锁文件走二进制占位
     */
    @Test
    void previewZipPdfAndLockFile() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Path zip = root.resolve("bundle.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("docs/"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("docs/a.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        Files.writeString(root.resolve("manual.pdf"), "%PDF-1.4 fake");
        Files.writeString(root.resolve("~$note.docx"), "lock");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> zipResult = service.preview(info.getId(), "u1", "bundle.zip");
        assertEquals("zip", zipResult.get("type"));
        List<Map<String, Object>> entries = (List<Map<String, Object>>) zipResult.get("entries");
        assertEquals(2, entries.size());
        assertEquals(Boolean.TRUE, entries.get(0).get("dir"));
        assertEquals("docs/a.txt", entries.get(1).get("name"));

        Map<String, Object> pdfResult = service.preview(info.getId(), "u1", "manual.pdf");
        assertEquals("pdf", pdfResult.get("type"));
        assertTrue(((String) pdfResult.get("content")).startsWith("data:application/pdf;base64,"));

        Map<String, Object> lockResult = service.preview(info.getId(), "u1", "~$note.docx");
        assertEquals("binary", lockResult.get("type"));
    }

    /**
     * 复制：文件/目录递归、同名自动副本后缀、根目录与自身子目录拒绝
     */
    @Test
    void copyFileSupportsRecursionAndConflictSuffix() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "A");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub").resolve("b.txt"), "B");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        Map<String, Object> result = service.copyFile(info.getId(), "u1", "a.txt", null);
        assertEquals("a - 副本.txt", result.get("path"));
        assertEquals("A", Files.readString(root.resolve("a - 副本.txt")));

        service.copyFile(info.getId(), "u1", "sub", null);
        assertEquals("B", Files.readString(root.resolve("sub - 副本").resolve("b.txt")));

        assertThrows(IllegalArgumentException.class, () -> service.copyFile(info.getId(), "u1", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.copyFile(info.getId(), "u1", "sub", "sub"));
        assertThrows(IllegalArgumentException.class, () -> service.copyFile(info.getId(), "u1", "sub", "a.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.copyFile(info.getId(), "u1", "..\\outside", null));
        assertThrows(IllegalArgumentException.class, () -> service.copyFile(info.getId(), "u2", "a.txt", null));
    }

    /**
     * 重命名：合法重命名生效，非法字符/同名冲突/根目录拒绝
     */
    @Test
    void renameFileValidatesName() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "A");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        service.renameFile(info.getId(), "u1", "a.txt", "b.txt");
        assertFalse(Files.exists(root.resolve("a.txt")));
        assertEquals("A", Files.readString(root.resolve("b.txt")));

        assertThrows(IllegalArgumentException.class, () -> service.renameFile(info.getId(), "u1", "b.txt", "c:d"));
        assertThrows(IllegalArgumentException.class, () -> service.renameFile(info.getId(), "u1", "b.txt", "../x"));
        assertThrows(IllegalArgumentException.class, () -> service.renameFile(info.getId(), "u1", "b.txt", " "));
        assertThrows(IllegalArgumentException.class, () -> service.renameFile(info.getId(), "u1", "b.txt", "b.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.renameFile(info.getId(), "u1", null, "r.txt"));
    }

    /**
     * 删除：目录递归删除，根目录与已删除路径拒绝
     */
    @Test
    void deleteFileRemovesRecursively() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "A");
        Files.createDirectories(root.resolve("sub").resolve("inner"));
        Files.writeString(root.resolve("sub").resolve("inner").resolve("b.txt"), "B");
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);

        service.deleteFile(info.getId(), "u1", "sub");
        assertFalse(Files.exists(root.resolve("sub")));
        service.deleteFile(info.getId(), "u1", "a.txt");
        assertFalse(Files.exists(root.resolve("a.txt")));
        assertThrows(IllegalArgumentException.class, () -> service.deleteFile(info.getId(), "u1", null));
        assertThrows(IllegalArgumentException.class, () -> service.deleteFile(info.getId(), "u1", "a.txt"));
    }

    /**
     * SERVER工作区列表掩码显示server://固定格式，不暴露服务器部署路径
     */
    @Test
    void listMasksServerRootPath() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        service.create("u1", "w", root.toString(), null, null, false);

        List<Map<String, Object>> result = service.list("u1");
        assertEquals(1, result.size());
        assertEquals("server://" + root.toRealPath().getFileName().toString(), result.get(0).get("rootPath"));
        assertEquals(Boolean.TRUE, result.get(0).get("valid"));
        assertFalse(result.get(0).containsKey("cloudManaged"));
    }

    /**
     * SERVER工作区列表附目录健康状态：目录存在为valid，目录被移除后为invalid
     */
    @Test
    void listReportsDirectoryHealth() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        service.create("u1", "w", root.toString(), null, null, false);
        assertEquals(Boolean.TRUE, service.list("u1").get(0).get("valid"));

        Files.delete(root);
        assertEquals(Boolean.FALSE, service.list("u1").get(0).get("valid"));
    }

    /**
     * 删除仅移除登记并发布级联清理事件，删除后重复删除被拒
     */
    @Test
    void deleteRemovesRegistrationAndPublishesEvent() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "w", root.toString(), null, null, false);
        List<Object> events = new ArrayList<>();
        inject(service, "eventPublisher", new RecordingPublisher(events));

        service.delete(info.getId(), "u1");
        assertEquals(0, store.size());
        assertEquals(1, events.size());
        UserWorkspaceDeletedEvent event = (UserWorkspaceDeletedEvent) events.get(0);
        assertEquals(info.getId(), event.getWorkspace().getId());
        assertEquals("u1", event.getOperatorId());

        assertThrows(IllegalArgumentException.class, () -> service.delete(info.getId(), "u1"));
        assertEquals(0, store.size());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 事件收集器（记录发布的应用事件供断言）
     */
    static class RecordingPublisher implements org.springframework.context.ApplicationEventPublisher {

        private final List<Object> events;

        RecordingPublisher(List<Object> events) {
            this.events = events;
        }

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }

    /**
     * 内存版用户工作区仓库
     */
    static class InMemoryRepository implements UserWorkspaceRepository {

        private final List<WorkspaceInfo> store;

        private final AtomicLong idGen = new AtomicLong(1);

        InMemoryRepository(List<WorkspaceInfo> store) {
            this.store = store;
        }

        @Override
        public void save(WorkspaceInfo info) {
            info.setId(idGen.getAndIncrement());
            store.add(info);
        }

        @Override
        public void updateById(WorkspaceInfo info) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(info.getId())) {
                    store.set(i, info);
                    return;
                }
            }
        }

        @Override
        public void deleteById(Long id) {
            store.removeIf(w -> w.getId().equals(id));
        }

        @Override
        public WorkspaceInfo findById(Long id) {
            return store.stream()
                    .filter(w -> w.getId().equals(id))
                    .findFirst().orElse(null);
        }

        @Override
        public WorkspaceInfo findByIdAndUserId(Long id, String userId) {
            return store.stream()
                    .filter(w -> w.getId().equals(id) && w.getUserId().equals(userId))
                    .findFirst().orElse(null);
        }

        @Override
        public List<WorkspaceInfo> listByUserId(String userId) {
            return store.stream().filter(w -> w.getUserId().equals(userId)).toList();
        }

        @Override
        public boolean existsByRootPath(String rootPath) {
            return store.stream().anyMatch(w -> w.getRootPath().equals(rootPath));
        }

        @Override
        public long countByUserId(String userId) {
            return store.stream().filter(w -> w.getUserId().equals(userId)).count();
        }
    }
}
