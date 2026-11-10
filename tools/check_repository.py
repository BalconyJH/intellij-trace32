"""Check public source and optional distribution archives for publication hygiene.

This is a focused safeguard, not a comprehensive secret scanner. It deliberately
prints rule names and relative locations, never matching sensitive content.
"""
from __future__ import annotations

import argparse
import io
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
AUTHOR_EMAIL = "73932916+BalconyJH@users.noreply.github.com"
GENERATED = {".idea", ".vscode", ".tools", ".gradle", ".sandbox", ".codex_tmp", "build", "backups", "__pycache__"}
BINARY_SUFFIXES = {".class", ".jar", ".zip", ".exe", ".dll", ".so", ".dylib", ".pfx", ".p12", ".pem", ".jks", ".keystore"}
RULES = (
    ("user-home-path", re.compile(r"(?i)(?:[a-z]:(?:\\{1,2}|/)(?:users|documents and settings)(?:\\{1,2}|/)[^\\/\s\"'<>]+|/(?:home|Users)/[\w.-]+)")),
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("access-token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})\b")),
)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+")
WINDOWS_PATH = re.compile(r"(?<![A-Za-z0-9])[A-Za-z]:(?:/|\\)")
NON_ENGLISH = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")


def issues_in_text(name: str, text: str, *, binary: bool = False) -> list[str]:
    problems = []
    synthetic_test = name.startswith(("src/test/", "src/smoke/"))
    for number, line in enumerate(text.splitlines(), 1):
        for rule, pattern in RULES:
            if pattern.search(line):
                problems.append(f"{name}:{number}: {rule}")
        if any(match.group() != AUTHOR_EMAIL for match in EMAIL.finditer(line)):
            problems.append(f"{name}:{number}: unapproved-email-address")
        if WINDOWS_PATH.search(line) and not synthetic_test:
            problems.append(f"{name}:{number}: absolute-windows-path-outside-test-fixture")
        if not binary and NON_ENGLISH.search(line):
            problems.append(f"{name}:{number}: non-English-prose-or-unescaped-fixture")
        if not binary and line.rstrip(" \t") != line:
            problems.append(f"{name}:{number}: trailing-whitespace")
    return problems


def candidate_files(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=root, check=True, stdout=subprocess.PIPE,
    )
    return sorted(set(result.stdout.decode("utf-8").rstrip("\0").split("\0")) - {""})


def check_source(root: Path) -> tuple[list[str], int]:
    problems = []
    files = candidate_files(root)
    for name in files:
        relative = PurePosixPath(name)
        if any(part in GENERATED for part in relative.parts) or relative.name.startswith(".env") and relative.name != ".env.example":
            problems.append(f"{name}: generated-or-private-file")
            continue
        if relative.suffix.lower() in BINARY_SUFFIXES:
            problems.append(f"{name}: binary-or-credential-file-in-source")
            continue
        file = root / name
        if file.is_symlink():
            problems.append(f"{name}: symlink-requires-explicit-review")
            continue
        if not file.is_file():  # A tracked deletion is not part of the working-tree publication.
            continue
        try:
            content = file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            problems.append(f"{name}: non-UTF-8-file")
            continue
        if content.startswith("\ufeff"):
            problems.append(f"{name}: UTF-8-BOM")
        problems.extend(issues_in_text(name, content))
    return problems, len(files)


def check_archive(data: bytes, name: str, *, depth: int = 0) -> list[str]:
    if depth > 2:
        return [f"{name}: unexpected-archive-nesting"]
    problems = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if sum(entry.file_size for entry in archive.infolist()) > 64 * 1024 * 1024:
            return [f"{name}: distribution-exceeds-audit-size-limit"]
        for entry in archive.infolist():
            relative = PurePosixPath(entry.filename.replace("\\", "/"))
            label = f"{name}!{entry.filename}"
            if relative.is_absolute() or ".." in relative.parts or re.match(r"^[A-Za-z]:", entry.filename):
                problems.append(f"{label}: unsafe-archive-path")
                continue
            if entry.is_dir():
                continue
            content = archive.read(entry)
            if relative.suffix.lower() in {".jar", ".zip"}:
                problems.extend(check_archive(content, label, depth=depth + 1))
            else:
                if any(part in {".idea", ".tools", "backups", "kotlin", "kotlinx"} for part in relative.parts) or relative.name.endswith("Test.class"):
                    problems.append(f"{label}: unexpected-runtime-test-or-local-file")
                problems.extend(issues_in_text(label, content.decode("utf-8", errors="ignore"), binary=relative.suffix == ".class"))
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, help="Also audit a plugin ZIP, including nested JARs.")
    args = parser.parse_args()
    problems, count = check_source(ROOT)
    if args.artifact:
        try:
            problems.extend(check_archive(args.artifact.read_bytes(), args.artifact.name))
        except (OSError, zipfile.BadZipFile) as error:
            problems.append(f"{args.artifact.name}: cannot-audit-archive ({type(error).__name__})")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"PASS publication hygiene: {count} repository files" + (" and plugin archive" if args.artifact else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
