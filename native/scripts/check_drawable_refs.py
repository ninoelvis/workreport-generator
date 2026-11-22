#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验并修复源码里的 R.drawable.xxx 引用，保证与 res/drawable 实际文件名一致。

Android 资源名只允许小写字母/数字/下划线，camelCase 名字会在 AAPT2 阶段报错。
"""
import glob, os, re, sys

RES = "/workspace/native/app/src/main/res"
SRC = "/workspace/native/app/src"

actual = set()
for d in ("drawable", "drawable-night", "mipmap-anydpi-v26"):
    p = os.path.join(RES, d)
    if os.path.isdir(p):
        for f in os.listdir(p):
            actual.add(f.split(".")[0])

def snake(name: str) -> str:
    n = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name)
    n = re.sub(r"(?<=[A-Z])([A-Z])(?=[a-z])", r"_\1", n)
    return n.lower()

def main():
    fix = "--fix" in sys.argv
    refs = {}
    unknown = []
    for f in glob.glob(SRC + "/**/*.kt", recursive=True):
        s = open(f, encoding="utf-8").read()
        for m in re.finditer(r"R\.drawable\.(\w+)", s):
            refs.setdefault(m.group(1), set()).add(f)
    bad = 0
    for name in sorted(refs):
        if name in actual:
            continue
        cand = snake(name)
        if cand in actual:
            bad += 1
            print("  ⚠️  %-24s → %s" % (name, cand))
            if fix:
                for f in refs[name]:
                    s = open(f, encoding="utf-8").read()
                    s = s.replace("R.drawable.%s" % name, "R.drawable.%s" % cand)
                    open(f, "w", encoding="utf-8").write(s)
        else:
            unknown.append(name)
    if unknown:
        print("\n❌ 找不到对应资源文件（需人工处理）:")
        for n in unknown:
            print("   ", n, "→", ", ".join(os.path.basename(x) for x in refs[n]))
    if bad and not fix:
        print("\n加 --fix 可自动修正 %d 处" % bad)
    if not bad and not unknown:
        print("✅ 所有 R.drawable 引用与资源文件一致（共 %d 个资源）" % len(actual))
    return 1 if unknown else 0

if __name__ == "__main__":
    sys.exit(main())
