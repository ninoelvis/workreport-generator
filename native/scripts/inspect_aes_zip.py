#!/usr/bin/env python3
"""解析 WinZip AES 加密 ZIP 的 extra field，验证 AE 版本 / 密钥强度 / 压缩方法。"""
import struct, sys

def parse(path):
    data = open(path, 'rb').read()
    # 找 local file header
    idx = data.find(b'PK\x03\x04')
    if idx < 0:
        print('❌ 不是 zip'); return
    sig, ver, flags, method, mtime, mdate, crc, csize, usize, nlen, elen = struct.unpack('<IHHHHHIIIHH', data[idx:idx+30])
    name = data[idx+30:idx+30+nlen].decode('utf-8', 'replace')
    extra = data[idx+30+nlen:idx+30+nlen+elen]
    print(f'文件: {name}')
    print(f'通用标志位: 0x{flags:04x} (bit0=加密: {bool(flags & 1)})')
    print(f'压缩方法字段: {method} (99=AES_INTERNAL_ONLY 标记)')

    off = 0
    found = False
    while off + 4 <= len(extra):
        hid, size = struct.unpack('<HH', extra[off:off+4])
        body = extra[off+4:off+4+size]
        if hid == 0x9901:
            found = True
            print(f'AES Extra Field (0x9901), data size={size}')
            if len(body) >= 7:
                ver_num, vendor, strength, comp = struct.unpack('<H2sBH', body[:7])
                ae = {1: 'AE-1 (HMAC-SHA1)', 2: 'AE-2 (HMAC-SHA256)'}.get(ver_num, f'未知({ver_num})')
                st = {1: 'AES-128', 2: 'AES-192', 3: 'AES-256'}.get(strength, f'未知({strength})')
                cm = {0: 'Store', 8: 'Deflate'}.get(comp, f'未知({comp})')
                print(f'  AE 版本: {ae}')
                print(f'  密钥强度: {st}')
                print(f'  实际压缩: {cm}')
            else:
                print(f'  ⚠️ 非标准：data 只有 {len(body)} 字节（zip.js 的简化写法）')
                if len(body) >= 1:
                    st = {1:'AES-128',2:'AES-192',3:'AES-256'}.get(body[0], f'未知({body[0]})')
                    print(f'  密钥强度: {st}')
        else:
            print(f'  (其他 extra field 0x{hid:04x}, size={size})')
        off += 4 + size
    if not found:
        print('❌ 未找到 AES extra field')

if __name__ == '__main__':
    parse(sys.argv[1])
