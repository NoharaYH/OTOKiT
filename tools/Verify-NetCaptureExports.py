"""Verify exported capture evidence without printing HTML, titles, scores or credentials.

Usage: python tools/Verify-NetCaptureExports.py <regression-root>
The root contains maimai/report.json, chunithm/report.json and optional archives/html/.
Only Python's standard library is required.
"""
from html.parser import HTMLParser
from pathlib import Path
import argparse
import hashlib
import json
import re


class Fields(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.depth, self.pending, self.values = 0, [], {}

    def handle_starttag(self, tag, attrs):
        if tag == "div":
            self.depth += 1
            classes = dict(attrs).get("class", "").split()
            for name in ("music_title", "music_name_block", "musiclist_worldsend_title", "play_musicdata_highscore", "music_score_block"):
                if name in classes:
                    self.pending.append([name, self.depth, []])

    def handle_data(self, data):
        for field in self.pending:
            field[2].append(data)

    def handle_endtag(self, tag):
        if tag == "div":
            for field in list(self.pending):
                if field[1] == self.depth:
                    # A real chart title can be the ideographic space U+3000.
                    self.values.setdefault(field[0], []).append("".join(field[2]))
                    self.pending.remove(field)
            self.depth -= 1


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="iso-8859-1").splitlines()
                if line and not line.startswith("#") and "=" in line)


def verify(root, games=("maimai", "chunithm")):
    output = {"games": {}, "businessPages": 0, "loginDiagnostics": 0, "failureDiagnostics": 0, "canonicalMatches": 0}
    for game in games:
        report = json.loads((root / game / "report.json").read_text(encoding="utf-8-sig"))
        expected_rounds = report["requestedRounds"]
        if "stoppedAfterCompletedRound" in report:
            assert report.get("stopReason") in ("USER_REQUEST", "SCOPE_REDUCED")
            expected_rounds = report["stoppedAfterCompletedRound"]
            assert 0 <= expected_rounds < report["requestedRounds"]
        assert len(report["rounds"]) == expected_rounds
        baseline, batches = {}, []
        for result in report["rounds"]:
            batch = result["batch"]
            assert re.fullmatch(r"[0-9]+-[a-f0-9]{8}", batch)
            directory = root / game / f'round-{result["round"]}' / batch
            manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8-sig"))
            traces = manifest.get("httpTrace", [])
            assert len(traces) <= 32
            for trace in traces:
                assert set(trace) <= {"method", "endpoint", "status", "protocol", "tlsVersion", "redirectEndpoint", "elapsedMs"}
                assert trace["method"] in ("GET", "POST")
                for key in ("endpoint", "redirectEndpoint"):
                    if trace.get(key):
                        assert re.fullmatch(r"[A-Z_]+(?::[A-Za-z/]*)?", trace[key]), "Trace contains an arbitrary endpoint"
            callbacks = [trace for trace in traces if trace["endpoint"] == "CALLBACK_" + game.upper()]
            if result["passed"]:
                assert manifest["stage"] == "SAVED_FOR_INSPECTION" and len(manifest["pages"]) == len(report["difficulties"]) + 2
                assert manifest["proxyEvents"]["callback"] == 1
                if traces and len(traces) < 32:
                    assert len(callbacks) == 1, "Unexpected number of callback exchanges"
            canonical = root / "archives" / "html" / "unidentified" / game / batch
            index = properties(canonical / "batch.properties") if canonical.exists() else None
            if index is not None:
                assert index["identity"] == "UNIDENTIFIED"
                assert index["status"] == "COMPLETED" if result["passed"] else index["status"] != "COMPLETED"
            pages = []
            diagnostics = [manifest[key] for key in ("loginDiagnostic", "failedResponse") if manifest.get(key)]
            for page in manifest["pages"] + diagnostics:
                assert re.fullmatch(r"(?:player|recent|login_response|failed_response|difficulty_(?:[0-5]|10))\.html", page["file"])
                raw = (directory / page["file"]).read_bytes()
                digest = hashlib.sha256(raw).hexdigest()
                assert len(raw) == page["bytes"] and digest == page["sha256"]
                body = raw.decode("utf-8")
                if page["inspection"] in ("VALID", "VALID_EMPTY_SCORES"):
                    assert "</html>" in body
                if index is not None:
                    name = page["sourceFileName"]
                    prefix = "MaiRecord" if game == "maimai" else "ChuniRecord"
                    assert re.fullmatch(prefix + r"_HTML_[0-9]{14}_[A-Za-z]+\.html", name)
                    assert index[f'page.{page["kind"]}.file'] == name
                    assert index[f'page.{page["kind"]}.sha256'] == digest
                    assert hashlib.sha256((canonical / name).read_bytes()).hexdigest() == digest
                    output["canonicalMatches"] += 1
                if page["file"].startswith("difficulty_") and page["inspection"] in ("VALID", "VALID_EMPTY_SCORES"):
                    parser = Fields()
                    parser.feed(body)
                    parser.close()
                    assert not parser.pending
                    title_field = "music_name_block" if game == "maimai" else "musiclist_worldsend_title" if page["file"] == "difficulty_5.html" else "music_title"
                    titles = parser.values.get(title_field, [])
                    assert titles and all(titles)
                    if game == "maimai":
                        scores = [match.group() for text in parser.values.get("music_score_block", [])
                                  for match in re.finditer(r"[0-9]{1,3}\.[0-9]{4}%", re.sub(r"\s", "", text))]
                    else:
                        scores = []
                        for text in parser.values.get("play_musicdata_highscore", []):
                            match = re.search(r"分数[:：]\s*([0-9][0-9,]*)", text)
                            assert match, "A score element has no numeric score"
                            scores.append(match.group(1).replace(",", ""))
                    assert page["chartCount"] == len(titles) and page["scoreCount"] == len(scores)
                    expected = "VALID" if scores else "VALID_EMPTY_SCORES"
                    assert page["inspection"] == expected
                    content_hash = hashlib.sha256(json.dumps([titles, scores], ensure_ascii=False).encode()).hexdigest()
                    previous = baseline.setdefault(page["kind"], content_hash)
                    assert previous == content_hash, "Chart/score content changed across rounds"
                    pages.append({"kind": page["kind"], "charts": len(titles), "scores": len(scores), "contentSha256": content_hash})
                output["loginDiagnostics" if page["file"] == "login_response.html" else "failureDiagnostics" if page["file"] == "failed_response.html" else "businessPages"] += 1
            batches.append({"batch": batch, "passed": result["passed"], "failureType": result.get("captureFailureType"),
                            "failureClass": result.get("captureFailureClass"), "failureLocation": result.get("captureFailureLocation"),
                            "protocolIssue": result.get("protocolIssue"), "httpTrace": traces, "callbackResponses": callbacks, "pages": pages})
        times = sorted(r["afterCallbackMs"] for r in report["rounds"] if r["passed"])
        middle = len(times) // 2
        median = (times[middle] if len(times) % 2 else (times[middle - 1] + times[middle]) / 2) if times else None
        passed = sum(bool(r["passed"]) for r in report["rounds"])
        output["games"][game] = {"attempted": len(report["rounds"]), "passed": passed, "failed": len(report["rounds"]) - passed,
                                  "networkRetries": sum(r.get("networkRetries") or 0 for r in report["rounds"]),
                                  "callbackToSavedMs": {"min": times[0] if times else None, "max": times[-1] if times else None, "median": median}, "batches": batches}
    (root / "verification.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in output.items() if key != "games"}))
    for game, result in output["games"].items():
        print(json.dumps({key: value for key, value in {"game": game, **result}.items() if key != "batches"}))


if __name__ == "__main__":
    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("root", type=Path)
    arguments.add_argument("--game", action="append", choices=("maimai", "chunithm"))
    options = arguments.parse_args()
    verify(options.root, options.game or ("maimai", "chunithm"))
