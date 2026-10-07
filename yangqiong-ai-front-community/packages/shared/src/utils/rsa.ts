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
/**
 * 登录凭证RSA加密工具
 * <p>
 * 使用内置RSA-2048公钥(PKCS#1 v1.5填充)加密账号密码，密文以rsa:前缀传输，
 * 后端用配置的私钥解密还原，避免登录凭证明文抓包泄露。
 * </p>
 */

/**
 * RSA公钥（PKCS#8 SPKI格式PEM，与后端配置的解密私钥配对）
 */
const RSA_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsIbFoqMQpSEz3RwzL6eK
oAq64CiA1dsMqVbBXXM/jAlPRx77RMz+4IlR5k6fUV5Vm+nExB2rl0zN3LScrPqb
lBb339iFHzRnJhmfbeD6WDvffu8QkNh9ldHJbMwn8O+U2lgBIHbNsJQclf+CXfkm
DWP1xQw+H/T/GAqTnyvr1CCcfnrIXFsVVG5g11DiW9XG/c3oCPnKnb0zKjUveSYN
l9YBmDLeM2dJDuAjcSKdfwsXecBC6qtW/IRhWg6LiWQ2YSddffeM0h1kaRfFN8WZ
/+7WcHtEVS/G8l/USZtKuCbDM5KAe0oryTjr398VdkcZa01RC2SLimXv3fWena2Y
XwIDAQAB
-----END PUBLIC KEY-----`;

/**
 * 密文前缀，与后端 RsaCipherUtil 约定
 */
const CIPHER_PREFIX = 'rsa:';

/**
 * PEM转Base64（去除头尾标识与换行）
 * @param pem
 * @return
 */
function pemToBase64(pem: string): string {
  return pem
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith('-----'))
    .join('');
}

/**
 * Base64转字节数组
 * @param base64
 * @return
 */
function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * 字节数组转Base64
 * @param bytes
 * @return
 */
function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) {
    binary += String.fromCharCode(b);
  }
  return btoa(binary);
}

/**
 * 简易ASN.1 TLV读取
 * @param bytes DER字节
 * @param offset 起始偏移
 * @return 标签、内容起始与长度、下一元素偏移
 */
function readTlv(bytes: Uint8Array, offset: number): { tag: number; contentStart: number; contentLength: number; next: number } {
  const tag = bytes[offset];
  let pos = offset + 1;
  let length = bytes[pos++];
  if (length & 0x80) {
    const numBytes = length & 0x7f;
    length = 0;
    for (let i = 0; i < numBytes; i++) {
      length = length * 256 + bytes[pos++];
    }
  }
  return { tag, contentStart: pos, contentLength: length, next: pos + length };
}

/**
 * 解析SPKI格式公钥中的模数与指数
 * @param pem
 * @return 模数与指数
 */
function parseRsaPublicKey(pem: string): { modulus: bigint; exponent: bigint } {
  const der = base64ToBytes(pemToBase64(pem));
  const outer = readTlv(der, 0);
  const algorithm = readTlv(der, outer.contentStart);
  const bitString = readTlv(der, algorithm.next);
  // BIT STRING内容首字节为未使用位数，跳过后是公钥SEQUENCE
  const sequence = readTlv(der, bitString.contentStart + 1);
  const modulusTlv = readTlv(der, sequence.contentStart);
  const exponentTlv = readTlv(der, modulusTlv.next);
  const modulus = bytesToBigInt(der.subarray(modulusTlv.contentStart, modulusTlv.contentStart + modulusTlv.contentLength));
  const exponent = bytesToBigInt(der.subarray(exponentTlv.contentStart, exponentTlv.contentStart + exponentTlv.contentLength));
  return { modulus, exponent };
}

/**
 * 字节数组转大整数
 * @param bytes
 * @return
 */
function bytesToBigInt(bytes: Uint8Array): bigint {
  let result = 0n;
  for (const b of bytes) {
    result = (result << 8n) | BigInt(b);
  }
  return result;
}

/**
 * 大整数转定长字节数组
 * @param value
 * @param length
 * @return
 */
function bigIntToBytes(value: bigint, length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  for (let i = length - 1; i >= 0; i--) {
    bytes[i] = Number(value & 0xffn);
    value >>= 8n;
  }
  return bytes;
}

/**
 * 大整数模幂运算
 * @param base 底数
 * @param exponent 指数
 * @param modulus 模数
 * @return 结果
 */
function modPow(base: bigint, exponent: bigint, modulus: bigint): bigint {
  let result = 1n;
  let b = base % modulus;
  let e = exponent;
  while (e > 0n) {
    if (e & 1n) {
      result = (result * b) % modulus;
    }
    b = (b * b) % modulus;
    e >>= 1n;
  }
  return result;
}

/**
 * RSA PKCS#1 v1.5加密
 * @param plainText 明文
 * @param publicKeyPem 公钥PEM
 * @return Base64密文
 */
function rsaEncrypt(plainText: string, publicKeyPem: string): string {
  const { modulus, exponent } = parseRsaPublicKey(publicKeyPem);
  const keyLength = (modulus.toString(2).length + 7) >> 3;
  const message = new TextEncoder().encode(plainText);
  const paddingLength = keyLength - message.length - 3;
  const padded = new Uint8Array(keyLength);
  padded[0] = 0x00;
  padded[1] = 0x02;
  // PS填充为非零随机字节
  for (let i = 2; i < 2 + paddingLength; i++) {
    let random = 0;
    while (random === 0) {
      random = Math.floor(Math.random() * 256);
    }
    padded[i] = random;
  }
  padded[2 + paddingLength] = 0x00;
  padded.set(message, 3 + paddingLength);
  const encrypted = modPow(bytesToBigInt(padded), exponent, modulus);
  return bytesToBase64(bigIntToBytes(encrypted, keyLength));
}

/**
 * 加密登录凭证，空值原样返回
 * @param plainText 明文凭证
 * @return rsa:前缀密文
 */
export function encryptCredential(plainText: string | null | undefined): string {
  if (!plainText) {
    return plainText ?? '';
  }
  return CIPHER_PREFIX + rsaEncrypt(plainText, RSA_PUBLIC_KEY);
}
