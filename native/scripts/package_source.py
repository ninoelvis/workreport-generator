#!/usr/bin/env python3
"""打包原生工程源码（排除私钥、构建产物、缓存）。"""
import os, sys, zipfile, hashlib

SRC = "/workspace/native"
OUT = "/workspace/output"
NAME = "工作报告生成器_原生版_工程源码_v1.3.0.zip"

EXCLUDE_DIRS = {"build", ".gradle", ".idea", "keystore", ".git", "captures", ".kotlin"}
EXCLUDE_FILES = {"local.properties"}
EXCLUDE_EXT = {".keystore", ".jks", ".log", ".iml"}

os.makedirs(OUT, exist_ok=True)
target = os.path.join(OUT, NAME)
if os.path.exists(target):
    os.remove(target)

count = 0
with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(SRC):
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        for f in files:
            ext = os.path.splitext(f)[1].lower()
            if f in EXCLUDE_FILES or ext in EXCLUDE_EXT:
                continue
            full = os.path.join(root, f)
            rel = os.path.relpath(full, SRC)
            z.write(full, os.path.join("native", rel))
            count += 1

size = os.path.getsize(target)
print(f"✅ 已生成 {target}")
print(f"   {count} 个文件, {size/1024:.0f} KB")

# 私钥检查
with zipfile.ZipFile(target) as z:
    bad = [n for n in z.namelist()
           if any(k in n.lower() for k in ("keystore", ".jks", ".p12", ".pem", "id_rsa", "local.properties"))]
    if bad:
        print("❌ 包内含有疑似私钥/敏感文件:")
        for b in bad:
            print("   ", b)
        sys.exit(1)
    print("✅ 私钥检查通过：无 keystore / .jks / .pem / local.properties")
    print(f"   sha256 = {hashlib.sha256(open(target,'rb').read()).hexdigest()}")
