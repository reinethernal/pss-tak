#!/usr/bin/env python3
"""Bump Android versionCode (+1) and minor versionName (x.Y.z -> x.(Y+1).0) in a Gradle file."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def bump_name(name: str) -> str:
    # Strip suffix like -psr for bumping core, then reattach
    suffix = ""
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)(.*)$", name.strip())
    if not m:
        # try x.Y only
        m2 = re.match(r"^(\d+)\.(\d+)(.*)$", name.strip())
        if not m2:
            return name
        major, minor, suffix = m2.group(1), int(m2.group(2)), m2.group(3)
        return f"{major}.{minor + 1}.0{suffix}"
    major, minor, patch, suffix = m.group(1), int(m.group(2)), m.group(3), m.group(4)
    return f"{major}.{minor + 1}.0{suffix}"


def bump_file(path: Path) -> tuple[int, str]:
    text = path.read_text()
    vc_m = re.search(r"versionCode\s*=\s*(\d+)", text)
    vn_m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not vc_m or not vn_m:
        raise SystemExit(f"Could not find versionCode/versionName in {path}")
    old_vc = int(vc_m.group(1))
    old_vn = vn_m.group(1)
    new_vc = old_vc + 1
    new_vn = bump_name(old_vn)
    text2 = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_vc}", text, count=1)
    text2 = re.sub(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{new_vn}"',
        text2,
        count=1,
    )
    path.write_text(text2)
    return new_vc, new_vn


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("gradle_file", type=Path)
    args = ap.parse_args()
    vc, vn = bump_file(args.gradle_file)
    print(f"{args.gradle_file}: versionName={vn} versionCode={vc}")


if __name__ == "__main__":
    main()
