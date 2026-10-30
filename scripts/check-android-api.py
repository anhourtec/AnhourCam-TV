#!/usr/bin/env python3
"""Fail if the pure-JVM modules call a JDK method that is newer than the app's minSdk.

Android Lint only checks the Android module; `:pairing`, `:ipcam`, `:layouts` and
`:bitvision-client` compile against the desktop JDK, so a call such as
ByteArrayOutputStream.toString(Charset) (API 33) builds fine but crashes older TVs with
NoSuchMethodError. This reads the compiled classes (javap) and checks every java.* / javax.*
method and field reference against the SDK's api-versions.xml.

Usage: scripts/check-android-api.py <min-sdk> <jar>...
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

SDK = os.path.expanduser(os.environ.get("ANDROID_HOME", "~/Library/Android/sdk"))


def api_db():
    platforms = sorted(
        (d for d in os.listdir(os.path.join(SDK, "platforms")) if os.path.exists(os.path.join(SDK, "platforms", d, "data", "api-versions.xml"))),
        key=lambda d: [int(x) if x.isdigit() else 0 for x in re.split(r"[-.]", d)],
    )
    root = ET.parse(os.path.join(SDK, "platforms", platforms[-1], "data", "api-versions.xml")).getroot()
    classes = {}
    for c in root.iter("class"):
        since = float(c.get("since", "1"))
        members = {}
        for m in list(c.iter("method")) + list(c.iter("field")):
            members[m.get("name")] = float(m.get("since", since))
        supers = [e.get("name") for e in c if e.tag in ("extends", "implements")]
        classes[c.get("name")] = (since, members, supers)
    return classes


def since(classes, owner, member, seen=None):
    """API level of owner.member, following super classes; None if unknown."""
    seen = seen or set()
    if owner in seen or owner not in classes:
        return None
    seen.add(owner)
    cls_since, members, supers = classes[owner]
    if member in members:
        return members[member]
    found = [s for s in (since(classes, sup, member, seen) for sup in supers) if s is not None]
    return min(found) if found else None


REF = re.compile(r"// (?:Method|InterfaceMethod|Field) ((?:java|javax)/[\w/$]+)\.(\w+|\"<init>\"):(\S+)")


def main():
    min_sdk = int(sys.argv[1])
    classes = api_db()
    problems = set()
    for jar in sys.argv[2:]:
        out = subprocess.run(["javap", "-c", "-p", "-cp", jar] + list_classes(jar), capture_output=True, text=True).stdout
        current = "?"
        for line in out.splitlines():
            if line.startswith(("public ", "final ", "class ", "abstract ", "interface ")) or " class " in line:
                current = line.strip()
            m = REF.search(line)
            if not m:
                continue
            owner, name, desc = m.group(1), m.group(2).strip('"'), m.group(3)
            key = name + (desc if "(" in desc else "")
            level = since(classes, owner, key)
            if level is not None and level > min_sdk:
                problems.add(f"{os.path.basename(jar)}: {owner.replace('/', '.')}.{name}{desc if '(' in desc else ''} needs API {level:g}")
    for p in sorted(problems):
        print(p)
    if problems:
        print(f"check-android-api: {len(problems)} call(s) newer than minSdk {min_sdk}")
        sys.exit(1)
    print(f"check-android-api: OK (minSdk {min_sdk})")


def list_classes(jar):
    names = subprocess.run(["jar", "tf", jar], capture_output=True, text=True).stdout.split()
    return [n[:-6].replace("/", ".") for n in names if n.endswith(".class")]


if __name__ == "__main__":
    main()
