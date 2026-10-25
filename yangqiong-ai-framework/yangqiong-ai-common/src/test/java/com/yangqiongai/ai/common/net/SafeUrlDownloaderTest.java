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
package com.yangqiongai.ai.common.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 安全URL内容拉取单测
 * @author yangqiong
 */
@DisplayName("安全URL内容拉取单测")
class SafeUrlDownloaderTest {

    private SafeUrlDownloader downloader;

    private HttpServer server;

    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        downloader = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 仅放行回环地址的测试实例（本地HttpServer目标为回环，需放行后验证其余逻辑）
     */
    static class LoopbackPermitDownloader extends SafeUrlDownloader {
        LoopbackPermitDownloader(long maxSizeBytes) {
            super(maxSizeBytes, 3000, 3000, 2);
        }

        @Override
        protected boolean isForbiddenAddress(InetAddress address) {
            if (address.isLoopbackAddress()) {
                return false;
            }
            return super.isForbiddenAddress(address);
        }
    }

    @Test
    @DisplayName("拒绝非http/https协议")
    void rejectsNonHttpProtocol() {
        SafeUrlDownloader guard = new SafeUrlDownloader(1024, 1000, 1000, 2);
        assertThatThrownBy(() -> guard.fetch("ftp://example.com/file.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("仅支持http/https协议");
    }

    @Test
    @DisplayName("拒绝回环地址")
    void rejectsLoopback() {
        assertThatThrownBy(() -> downloader.fetch("http://127.0.0.1:8082/x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }

    @Test
    @DisplayName("拒绝私有网段地址")
    void rejectsPrivateRanges() {
        for (String url : new String[]{"http://10.0.0.1/x", "http://172.16.0.1/x", "http://192.168.1.1/x"}) {
            assertThatThrownBy(() -> downloader.fetch(url))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("内网或保留地址");
        }
    }

    @Test
    @DisplayName("拒绝链路本地地址（云元数据端点）")
    void rejectsLinkLocal() {
        assertThatThrownBy(() -> downloader.fetch("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }

    @Test
    @DisplayName("拒绝任意本地与组播地址")
    void rejectsAnyLocalAndMulticast() {
        assertThatThrownBy(() -> downloader.fetch("http://0.0.0.0/x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
        assertThatThrownBy(() -> downloader.fetch("http://224.0.0.1/x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }

    @Test
    @DisplayName("拒绝IPv6回环/唯一本地/IPv4映射内网地址")
    void rejectsForbiddenIpv6() {
        for (String url : new String[]{
                "http://[::1]/x", "http://[fc00::1]/x", "http://[::ffff:192.168.1.1]/x"}) {
            assertThatThrownBy(() -> downloader.fetch(url))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("内网或保留地址");
        }
    }

    @Test
    @DisplayName("主机解析失败时拒绝拉取")
    void rejectsUnresolvableHost() {
        assertThatThrownBy(() -> downloader.fetch("http://nonexistent-yangqiong-test.invalid/x"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("正常拉取并优先取Content-Disposition文件名")
    void fetchesWithDispositionFileName() throws IOException {
        server.createContext("/file", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            // HTTP头仅支持ASCII，中文文件名按URL编码传输（解码逻辑由下载器负责）
            exchange.getResponseHeaders().add("Content-Disposition",
                    "attachment; filename=\"%E5%B9%B4%E5%BA%A6%E6%8A%A5%E5%91%8A.pdf\"");
            byte[] body = "hello-open-api".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SafeUrlDownloader.Result result = new LoopbackPermitDownloader(10 * 1024).fetch(baseUrl + "/file");
        assertThat(result.getFileName()).isEqualTo("年度报告.pdf");
        assertThat(result.getContentType()).contains("text/plain");
        assertThat(new String(result.getBytes(), StandardCharsets.UTF_8)).isEqualTo("hello-open-api");
    }

    @Test
    @DisplayName("无Content-Disposition时从URL路径提取文件名")
    void resolvesFileNameFromUrlPath() throws IOException {
        server.createContext("/docs", exchange -> {
            byte[] body = "demo".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SafeUrlDownloader.Result result = new LoopbackPermitDownloader(10 * 1024)
                .fetch(baseUrl + "/docs/%E6%A0%B7%E6%9C%AC.pdf");
        assertThat(result.getFileName()).isEqualTo("样本.pdf");
    }

    @Test
    @DisplayName("Content-Length超限时拒绝")
    void rejectsOversizeByContentLength() throws IOException {
        server.createContext("/big", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[100]);
            }
        });
        LoopbackPermitDownloader small = new LoopbackPermitDownloader(10);
        assertThatThrownBy(() -> small.fetch(baseUrl + "/big"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("文件大小超过限制");
    }

    @Test
    @DisplayName("无Content-Length时流式读取超限同样拒绝")
    void rejectsOversizeWhenStreaming() throws IOException {
        server.createContext("/chunked", exchange -> {
            // 响应长度传0表示chunked流式输出
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[100]);
            }
        });
        LoopbackPermitDownloader small = new LoopbackPermitDownloader(10);
        assertThatThrownBy(() -> small.fetch(baseUrl + "/chunked"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("文件大小超过限制");
    }

    @Test
    @DisplayName("重定向跳数超过限制时拒绝")
    void rejectsTooManyRedirects() throws IOException {
        // /r1 → /r2 → /r3 → /r4，限制2跳时第三次跳转被拒
        for (int i = 1; i <= 4; i++) {
            final int current = i;
            server.createContext("/r" + current, exchange -> {
                if (current < 4) {
                    exchange.getResponseHeaders().add("Location", baseUrl + "/r" + (current + 1));
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                }
                byte[] body = "final".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
        }
        LoopbackPermitDownloader permit = new LoopbackPermitDownloader(10 * 1024);
        assertThatThrownBy(() -> permit.fetch(baseUrl + "/r1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("重定向次数超过限制");
    }

    @Test
    @DisplayName("重定向到内网地址被逐跳校验拦截")
    void rejectsRedirectToPrivateAddress() throws IOException {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://192.168.1.1/inner");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        LoopbackPermitDownloader permit = new LoopbackPermitDownloader(10 * 1024);
        assertThatThrownBy(() -> permit.fetch(baseUrl + "/redirect"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }

    @Test
    @DisplayName("响应状态异常时拒绝")
    void rejectsErrorStatus() throws IOException {
        server.createContext("/err", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        LoopbackPermitDownloader permit = new LoopbackPermitDownloader(10 * 1024);
        assertThatThrownBy(() -> permit.fetch(baseUrl + "/err"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("URL响应状态异常");
    }

    @Test
    @DisplayName("白名单精确IP放行内网地址拉取")
    void whitelistExactIpAllowsFetch() throws IOException {
        server.createContext("/minio", exchange -> {
            byte[] body = "minio-object".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SafeUrlDownloader whitelisted = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2,
                java.util.List.of("127.0.0.1"));
        SafeUrlDownloader.Result result = whitelisted.fetch(baseUrl + "/minio");
        assertThat(new String(result.getBytes(), StandardCharsets.UTF_8)).isEqualTo("minio-object");
    }

    @Test
    @DisplayName("白名单CIDR网段放行内网地址拉取")
    void whitelistCidrAllowsFetch() throws IOException {
        server.createContext("/cidr", exchange -> {
            byte[] body = "cidr-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SafeUrlDownloader whitelisted = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2,
                java.util.List.of("127.0.0.0/8"));
        SafeUrlDownloader.Result result = whitelisted.fetch(baseUrl + "/cidr");
        assertThat(new String(result.getBytes(), StandardCharsets.UTF_8)).isEqualTo("cidr-ok");
    }

    @Test
    @DisplayName("白名单主机名条目按URL主机文本放行")
    void whitelistHostnameAllowsFetch() throws IOException {
        server.createContext("/host", exchange -> {
            byte[] body = "host-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SafeUrlDownloader whitelisted = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2,
                java.util.List.of("localhost"));
        SafeUrlDownloader.Result result = whitelisted.fetch("http://localhost:" + server.getAddress().getPort() + "/host");
        assertThat(new String(result.getBytes(), StandardCharsets.UTF_8)).isEqualTo("host-ok");
    }

    @Test
    @DisplayName("白名单外的内网地址仍被拦截")
    void addressesOutsideWhitelistStillBlocked() {
        SafeUrlDownloader whitelisted = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2,
                java.util.List.of("10.0.0.1"));
        assertThatThrownBy(() -> whitelisted.fetch("http://192.168.1.1/x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }

    @Test
    @DisplayName("重定向到白名单外的内网地址仍被逐跳校验拦截")
    void redirectOutsideWhitelistStillBlocked() throws IOException {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://192.168.1.1/inner");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        SafeUrlDownloader whitelisted = new SafeUrlDownloader(10 * 1024, 3000, 3000, 2,
                java.util.List.of("127.0.0.1"));
        assertThatThrownBy(() -> whitelisted.fetch(baseUrl + "/redirect"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("内网或保留地址");
    }
}
