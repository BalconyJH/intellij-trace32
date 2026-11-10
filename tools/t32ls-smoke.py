"""Exercise a real t32ls process without Lauterbach TRACE32® hardware or script execution.

Usage: python tools/t32ls-smoke.py --server PATH/TO/t32ls.exe
Only Python's standard library is required.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import time


class Client:
    def __init__(self, executable: Path, root: Path):
        self.proc = subprocess.Popen(
            [str(executable), f"--clientProcessId={os.getpid()}", "--trace=off",
             f"--t32SystemDir={root / 'system'}", f"--t32TempDir={root / 'temporary'}"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            cwd=root, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        self.messages: queue.Queue = queue.Queue()
        self.stderr: list[str] = []
        self.next_id = 0
        threading.Thread(target=self.read, daemon=True).start()
        threading.Thread(target=lambda: self.stderr.extend(
            line.decode("utf-8", errors="replace") for line in self.proc.stderr
        ), daemon=True).start()

    def read(self):
        try:
            while True:
                headers = {}
                while True:
                    line = self.proc.stdout.readline()
                    if not line:
                        raise EOFError("t32ls closed stdout")
                    if line in (b"\r\n", b"\n"):
                        break
                    name, value = line.decode("ascii").strip().split(":", 1)
                    headers[name.lower()] = value.strip()
                size = int(headers["content-length"])
                body = bytearray()
                while len(body) < size:
                    chunk = self.proc.stdout.read(size - len(body))
                    if not chunk:
                        raise EOFError("incomplete LSP message")
                    body.extend(chunk)
                self.messages.put(json.loads(body))
        except Exception as exc:
            self.messages.put(exc)

    def send(self, message):
        body = json.dumps({"jsonrpc": "2.0", **message}, ensure_ascii=False).encode("utf-8")
        self.proc.stdin.write(f"Content-Length: {len(body)}\r\n\r\n".encode("ascii") + body)
        self.proc.stdin.flush()

    def notify(self, method, params=None):
        self.send({"method": method, "params": params or {}})

    def request(self, method, params, timeout=15):
        self.next_id += 1
        request_id = self.next_id
        self.send({"id": request_id, "method": method, "params": params})
        deadline = time.monotonic() + timeout
        while True:
            wait = deadline - time.monotonic()
            if wait <= 0:
                raise TimeoutError(method)
            try:
                message = self.messages.get(timeout=wait)
            except queue.Empty as exc:
                raise TimeoutError(f"{method}; server stderr: {''.join(self.stderr)}") from exc
            if isinstance(message, Exception):
                raise message
            if "method" in message and "id" in message:
                # t32ls may request a work-done token while indexing.
                result = [] if message["method"] == "workspace/configuration" else None
                self.send({"id": message["id"], "result": result})
            elif message.get("id") == request_id:
                if "error" in message:
                    raise RuntimeError(f"{method}: {message['error']}")
                return message.get("result")

    def close(self):
        if self.proc.poll() is None:
            try:
                self.request("shutdown", None, timeout=5)
                self.send({"method": "exit"})
                self.proc.wait(timeout=5)
            finally:
                if self.proc.poll() is None:
                    self.proc.kill()
                    self.proc.wait(timeout=5)


def position(text, fragment, occurrence=0):
    start = -1
    for _ in range(occurrence + 1):
        start = text.index(fragment, start + 1)
    before = text[:start]
    return {"line": before.count("\n"), "character": len(before.rsplit("\n", 1)[-1])}


def locations(result):
    values = result if isinstance(result, list) else [result] if result else []
    return [value.get("uri", value.get("targetUri")) for value in values]


def eventually(client, method, params, predicate):
    deadline = time.monotonic() + 15
    last = None
    while time.monotonic() < deadline:
        last = client.request(method, params)
        if predicate(last):
            return last
        time.sleep(0.05)
    raise AssertionError(f"{method} did not produce expected result: {last!r}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--report", type=Path, default=Path(__file__).resolve().parents[1] / "build/t32ls-result.json")
    args = parser.parse_args()
    executable = args.server.resolve(strict=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    checks = []
    with tempfile.TemporaryDirectory(prefix="t32ls-fixture-", dir=args.report.parent) as folder:
        root = Path(folder).resolve()
        main_text = '; first comment\n; second comment\nPRIVATE &count\n&count=1.\nDO "child.cmm"\nDO "~~/system.cmm"\nDO "~~~/temporary.cmm"\nGOSUB helper\nENDDO\nhelper:\n(\n  PRINT &count\n  RETURN\n)\n'
        child_text = 'PRINT "child"\nENDDO\n'
        main_file = root / "main.cmm"
        child_file = root / "child.cmm"
        main_file.write_text(main_text, encoding="utf-8")
        child_file.write_text(child_text, encoding="utf-8")
        (root / "system").mkdir()
        (root / "temporary").mkdir()
        system_file = root / "system/system.cmm"
        temporary_file = root / "temporary/temporary.cmm"
        system_file.write_text('ENDDO\n', encoding="utf-8")
        temporary_file.write_text('ENDDO\n', encoding="utf-8")
        main_uri, child_uri = main_file.as_uri(), child_file.as_uri()
        client = Client(executable, root)
        try:
            result = client.request("initialize", {
                "processId": os.getpid(), "rootUri": root.as_uri(),
                "workspaceFolders": [{"uri": root.as_uri(), "name": "cmm-smoke"}],
                "capabilities": {"window": {"workDoneProgress": True}, "textDocument": {
                    "definition": {"linkSupport": True},
                    "semanticTokens": {"requests": {"full": True}, "tokenTypes": [
                        "namespace", "type", "class", "enum", "interface", "struct", "typeParameter", "parameter",
                        "variable", "property", "enumMember", "event", "function", "method", "macro", "keyword",
                        "modifier", "comment", "string", "number", "regexp", "operator", "decorator"
                    ], "tokenModifiers": ["declaration", "definition", "readonly", "static", "deprecated", "abstract", "async", "modification", "documentation", "defaultLibrary"], "formats": ["relative"]}
                }},
                "clientInfo": {"name": "Toolkit for Lauterbach TRACE32® protocol smoke", "version": "1"},
            })
            capabilities = result["capabilities"]
            for capability in ("definitionProvider", "referencesProvider", "foldingRangeProvider", "semanticTokensProvider"):
                assert capability in capabilities and capabilities[capability] is not False and capabilities[capability] is not None, capability
            checks.append("initialize advertises definitions/references/folding/semantic tokens")
            client.notify("initialized")
            for uri, text in ((main_uri, main_text), (child_uri, child_text)):
                client.notify("textDocument/didOpen", {"textDocument": {"uri": uri, "languageId": "practice", "version": 1, "text": text}})
            macro = {"textDocument": {"uri": main_uri}, "position": position(main_text, "&count", 2)}
            definitions = eventually(client, "textDocument/definition", macro, lambda value: main_uri in locations(value))
            checks.append("macro definition resolves within source")
            script = {"textDocument": {"uri": main_uri}, "position": position(main_text, "child.cmm")}
            eventually(client, "textDocument/definition", script, lambda value: child_uri in locations(value))
            checks.append("DO path definition resolves across files")
            for prefix, target in (("~~/system.cmm", system_file), ("~~~/temporary.cmm", temporary_file)):
                prefixed_path = {"textDocument": {"uri": main_uri}, "position": position(main_text, prefix)}
                eventually(client, "textDocument/definition", prefixed_path, lambda value, uri=target.as_uri(): uri in locations(value))
            checks.append("configured Lauterbach TRACE32® system/temp directory prefixes resolve")
            references = eventually(client, "textDocument/references", {**macro, "context": {"includeDeclaration": True}}, lambda value: isinstance(value, list) and len(value) >= 2)
            checks.append("macro references include declaration and uses")
            folds = eventually(client, "textDocument/foldingRange", {"textDocument": {"uri": main_uri}}, lambda value: isinstance(value, list) and len(value) >= 1)
            checks.append("PRACTICE block/comment folding returns ranges")
            tokens = eventually(client, "textDocument/semanticTokens/full", {"textDocument": {"uri": main_uri}}, lambda value: isinstance(value, dict) and len(value.get("data", [])) > 0)
            checks.append("semantic token data is returned")
            updated = main_text.replace("&count", "&total")
            client.notify("textDocument/didChange", {"textDocument": {"uri": main_uri, "version": 2}, "contentChanges": [{
                "range": {"start": {"line": 0, "character": 0}, "end": {"line": main_text.count("\n"), "character": 0}},
                "text": updated
            }]})
            updated_macro = {"textDocument": {"uri": main_uri}, "position": position(updated, "&total", 2)}
            eventually(client, "textDocument/definition", updated_macro, lambda value: main_uri in locations(value))
            checks.append("didChange updates document analysis")
            for uri in (main_uri, child_uri):
                client.notify("textDocument/didClose", {"textDocument": {"uri": uri}})
            client.close()
            assert client.proc.returncode == 0, client.stderr
            checks.append("shutdown/exit completes with exit code zero")
            report = {"status": "PASS", "server": executable.name, "checks": checks,
                      "referenceCount": len(references), "foldCount": len(folds), "semanticTokenCount": len(tokens["data"]) // 5,
                      "serverInfo": result.get("serverInfo"), "exitCode": client.proc.returncode}
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(json.dumps(report, ensure_ascii=False, indent=2))
        finally:
            client.close()


if __name__ == "__main__":
    main()
