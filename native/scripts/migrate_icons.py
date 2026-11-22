#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把项目里的 Material Icons 全部换成自绘的矢量图标（res/drawable/ic_*.xml）。

三类用法形态：
  Icon(Icons.Filled.X, ...)          → Icon(painterResource(R.drawable.ic_x), ...)
  imageVector = Icons.Filled.X        → painter = painterResource(R.drawable.ic_x)
  icon = Icons.Filled.X（值参数）     → icon = R.drawable.ic_x
"""
import glob, os, re, sys

ROOT = "/workspace/native/app/src/main/java/com/workreport/app"

MAP = {
    "Description": "ic_document",
    "History": "ic_nav_history",
    "Person": "ic_nav_profile",
    "ArrowBackIosNew": "ic_arrowBackIos",
    "ChevronRight": "ic_chevronRight",
    "Info": "ic_info",
    "Close": "ic_close",
    "Refresh": "ic_refresh",
    "Add": "ic_add",
    "Bolt": "ic_bolt",
    "ExpandMore": "ic_expandMore",
    "KeyboardArrowUp": "ic_arrowUp",
    "KeyboardArrowDown": "ic_arrowDown",
    "PlaylistAdd": "ic_playlistAdd",
    "Delete": "ic_delete",
    "Download": "ic_download",
    "Upload": "ic_upload",
    "Edit": "ic_edit",
    "Fullscreen": "ic_fullscreen",
    "LockOpen": "ic_lockOpen",
    "Lock": "ic_lock",
    "Mail": "ic_mail",
    "Search": "ic_search",
    "Share": "ic_share",
    "Visibility": "ic_eye",
    "VisibilityOff": "ic_visibilityOff",
    "Send": "ic_send",
    "Code": "ic_code",
    "Folder": "ic_folder",
    "Star": "ic_star",
    "Restore": "ic_restore",
    "Check": "ic_check",
    "ContentCopy": "ic_contentCopy",
    "ContentPaste": "ic_contentPaste",
}

ICON_RE = re.compile(r"Icons\.(?:AutoMirrored\.)?Filled\.([A-Za-z]+)")


def main():
    changed_files = []
    total = 0
    for f in glob.glob(ROOT + "/**/*.kt", recursive=True):
        s = open(f, encoding="utf-8").read()
        orig = s

        # --- 1) imageVector = <Icons...>  →  painter = painterResource(<drawable>) ---
        def fix_image_vector(m):
            return "painter = painterResource(%s)" % m.group(1)

        # 先处理 imageVector = if (...) A else B
        def fix_ternary(m):
            a = m.group(1)
            b = m.group(2)
            return "painter = painterResource(if (%s) %s else %s)" % (a, b, b)

        # --- 2) Icons.Filled.X  →  R.drawable.ic_x ---
        def sub_icon(m):
            name = m.group(1)
            if name not in MAP:
                print("  ⚠️ 未映射的图标:", name, "in", os.path.basename(f))
                return m.group(0)
            return "R.drawable.%s" % MAP[name]

        s = ICON_RE.sub(sub_icon, s)

        # --- 3) Icon(R.drawable.x, ...) → Icon(painterResource(R.drawable.x), ...) ---
        s = re.sub(r"Icon\(\s*(R\.drawable\.\w+)\s*,", r"Icon(painterResource(\1),", s)

        # --- 4) imageVector = R.drawable.x → painter = painterResource(R.drawable.x) ---
        s = re.sub(r"imageVector\s*=\s*(R\.drawable\.\w+)", r"painter = painterResource(\1)", s)
        # imageVector = if (...) A else B
        s = re.sub(
            r"imageVector\s*=\s*if\s*\(([^)]+)\)\s*(R\.drawable\.\w+)\s*else\s*(R\.drawable\.\w+)",
            r"painter = painterResource(if (\1) \2 else \3)", s)

        if s != orig:
            n = len(ICON_RE.findall(orig))
            total += n
            # 确保 import
            if "R.drawable." in s and "import com.workreport.app.R" not in s:
                lines = s.split("\n")
                for i, l in enumerate(lines):
                    if l.startswith("import com.workreport.app") and not l.endswith(".R"):
                        lines.insert(i, "import com.workreport.app.R")
                        break
                s = "\n".join(lines)
            # 确保 painterResource import
            if "painterResource(" in s and "import androidx.compose.ui.res.painterResource" not in s:
                lines = s.split("\n")
                for i, l in enumerate(lines):
                    if l.startswith("import androidx.compose.ui."):
                        lines.insert(i, "import androidx.compose.ui.res.painterResource")
                        break
                s = "\n".join(lines)
            open(f, "w", encoding="utf-8").write(s)
            changed_files.append((os.path.basename(f), n))
    for name, n in changed_files:
        print("  %-28s %d 处" % (name, n))
    print("✅ 共替换 %d 处 Material Icons" % total)


if __name__ == "__main__":
    main()
