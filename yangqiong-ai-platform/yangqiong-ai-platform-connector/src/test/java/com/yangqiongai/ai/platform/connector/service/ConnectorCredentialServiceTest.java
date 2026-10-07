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
package com.yangqiongai.ai.platform.connector.service;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.entity.ConnectorCredential;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorCredentialMapper;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorField;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("连接器凭证托管单元测试")
class ConnectorCredentialServiceTest {

    /**
     * 测试用提供商：appKey必填非敏感、appSecret必填敏感、region选填
     */
    private static final String PROVIDER_CODE = "testprov";

    @Mock
    private ConnectorCredentialMapper credentialMapper;

    @Mock
    private ConnectorInstanceMapper instanceMapper;

    @Mock
    private ConnectorProvider provider;

    private ConnectorRegistry registry;

    private ConnectorCredentialService service;

    @BeforeEach
    void setUp() {
        lenient().when(provider.providerCode()).thenReturn(PROVIDER_CODE);
        registry = new ConnectorRegistry(List.of(provider));
        lenient().when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试提供商", "test")
                .credentialField(ConnectorField.of("appKey", "应用Key").required().build())
                .credentialField(ConnectorField.of("appSecret", "应用密钥").required().secret().build())
                .credentialField(ConnectorField.of("region", "地域").build())
                .build());
        service = new ConnectorCredentialService(credentialMapper, instanceMapper, registry);
    }

    /**
     * 构建凭证参数
     * @param id 可空
     * @param credentialJson 可空
     * @return
     */
    private ConnectorCredential buildCredential(Long id, String credentialJson) {
        ConnectorCredential credential = new ConnectorCredential();
        credential.setDbId(id);
        credential.setProviderCode(PROVIDER_CODE);
        credential.setName("测试凭证");
        credential.setCredentialJson(credentialJson);
        return credential;
    }

    @Test
    @DisplayName("新增凭证加密落库并返回脱敏副本")
    void shouldEncryptAndMaskOnInsert() {
        String plain = "{\"appKey\":\"ak12345678\",\"appSecret\":\"sk-secret-9999\",\"region\":\"hangzhou\"}";

        ConnectorCredential saved = service.save(buildCredential(null, plain));

        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(credentialMapper).insert(captor.capture());
        ConnectorCredential inserted = captor.getValue();
        // 落库内容为密文且不含明文
        assertThat(inserted.getCredentialJson()).isNotEqualTo(plain);
        assertThat(inserted.getCredentialJson()).startsWith("v0:");
        // 掩码JSON：敏感字段掩码（sk-前缀保留，主体保留前4位与尾4位）、非敏感字段明文
        Map<String, String> masked = JSON.parseObject(inserted.getMaskedJson(), Map.class);
        assertThat(masked.get("appSecret")).isEqualTo("sk-secr****9999");
        assertThat(masked.get("appKey")).isEqualTo("ak12345678");
        assertThat(masked.get("region")).isEqualTo("hangzhou");
        // 返回副本不含密文
        assertThat(saved.getCredentialJson()).isNull();
    }

    @Test
    @DisplayName("新增凭证缺少必填字段时拒绝")
    void shouldRejectMissingRequiredField() {
        ConnectorCredential credential = buildCredential(null, "{\"appKey\":\"ak12345678\"}");

        assertThatThrownBy(() -> service.save(credential))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("appSecret");
        verify(credentialMapper, never()).insert(any(ConnectorCredential.class));
    }

    @Test
    @DisplayName("未知提供商或参数缺失时拒绝")
    void shouldRejectUnknownProviderOrMissingParams() {
        ConnectorCredential unknownProvider = buildCredential(null, "{}");
        unknownProvider.setProviderCode("no_such");

        assertThatThrownBy(() -> service.save(unknownProvider))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("未知");

        ConnectorCredential noName = buildCredential(null, "{}");
        noName.setName(null);

        assertThatThrownBy(() -> service.save(noName))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("名称");

        ConnectorCredential noJson = buildCredential(null, null);
        assertThatThrownBy(() -> service.save(noJson))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证内容");
    }

    @Test
    @DisplayName("更新时凭证内容为空保留原密钥")
    void shouldKeepOriginalCipherWhenContentBlank() {
        ConnectorCredential existing = new ConnectorCredential();
        existing.setDbId(1L);
        existing.setProviderCode(PROVIDER_CODE);
        existing.setName("旧凭证");
        existing.setCredentialJson("v0:oldcipher");
        existing.setMaskedJson("{\"appSecret\":\"****0000\"}");
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        ConnectorCredential update = buildCredential(1L, "  ");
        update.setName("新凭证");

        ConnectorCredential saved = service.save(update);

        assertThat(saved.getCredentialJson()).isNull();
        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(credentialMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCredentialJson()).isEqualTo("v0:oldcipher");
        assertThat(captor.getValue().getName()).isEqualTo("新凭证");
    }

    @Test
    @DisplayName("删除被实例引用的凭证时拒绝")
    void shouldRejectDeleteWhenReferenced() {
        ConnectorCredential existing = new ConnectorCredential();
        existing.setDbId(1L);
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(instanceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("2");
        verify(credentialMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("删除无引用凭证成功")
    void shouldDeleteWhenNotReferenced() {
        ConnectorCredential existing = new ConnectorCredential();
        existing.setDbId(1L);
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(instanceMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(credentialMapper).deleteById(1L);
    }

    @Test
    @DisplayName("加载凭证视图解密并含全量字段")
    void shouldLoadDecryptedCredentialView() {
        String plain = "{\"appKey\":\"ak\",\"appSecret\":\"sk\",\"region\":\"hz\"}";
        ConnectorCredential existing = new ConnectorCredential();
        existing.setDbId(1L);
        existing.setProviderCode(PROVIDER_CODE);
        existing.setCredentialJson(com.yangqiongai.ai.common.util.SecretCipherUtil.encrypt(plain, PROVIDER_CODE));
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        ConnectorCredentialView view = service.loadCredentialView(1L);

        assertThat(view.get("appKey")).isEqualTo("ak");
        assertThat(view.get("appSecret")).isEqualTo("sk");
        assertThat(view.get("region")).isEqualTo("hz");
    }

    @Test
    @DisplayName("连通性测试转发提供商并回传失败原因")
    void shouldForwardTestToProvider() {
        ConnectorCredential existing = new ConnectorCredential();
        existing.setDbId(1L);
        existing.setProviderCode(PROVIDER_CODE);
        existing.setCredentialJson(com.yangqiongai.ai.common.util.SecretCipherUtil.encrypt(
                "{\"appKey\":\"ak\",\"appSecret\":\"sk\"}", PROVIDER_CODE));
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(provider.testCredential(any())).thenReturn("token无效");

        assertThat(service.test(1L)).isEqualTo("token无效");
    }

    @Test
    @DisplayName("凭证不存在时抛资源不存在异常")
    void shouldThrowWhenCredentialNotFound() {
        when(credentialMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(AiException.class);
    }
}
