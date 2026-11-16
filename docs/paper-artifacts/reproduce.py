#!/usr/bin/env python3
"""Reproduce the paper's recorded experiment or existing test selection.

Requires Python 3.9+, Git, and a full OpenJDK 21. The source checkout is
read-only to this driver. Every execution requires a new output directory.
"""
from __future__ import annotations

import argparse
import csv
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

ARTIFACTS = Path(__file__).resolve().parent


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_package():
    manifest = ARTIFACTS / "SHA256SUMS"
    checked = []
    for line in manifest.read_text(encoding="utf-8").splitlines():
        expected, relative = line.split("  ", 1)
        name = Path(relative)
        path = ARTIFACTS / name
        if name.is_absolute() or ".." in name.parts or path.is_symlink():
            raise ValueError("Unsafe manifest path: " + relative)
        if not path.is_file() or digest(path) != expected:
            raise ValueError("Package checksum mismatch: " + relative)
        checked.append(relative)
    actual = {p.relative_to(ARTIFACTS).as_posix()
              for p in ARTIFACTS.rglob("*") if p.is_file()
              and p != manifest and "__pycache__" not in p.parts}
    if actual != set(checked) or len(checked) != len(set(checked)):
        raise ValueError("Package file list differs from SHA256SUMS")
    return len(checked)


def capture(command, cwd):
    return subprocess.check_output(command, cwd=cwd, text=True,
                                   stderr=subprocess.STDOUT).strip()


def prepare(args):
    verify_package()
    repo = Path(args.repo).expanduser().resolve()
    out = Path(args.out).expanduser().resolve()
    if out.exists():
        raise ValueError("Output directory already exists. Choose a new --out path: " + str(out))
    if repo == out or repo in out.parents:
        raise ValueError("Keep --out outside the source checkout")
    if ARTIFACTS == out or ARTIFACTS in out.parents:
        raise ValueError("Keep --out outside the artifact directory")
    provenance = load_json(ARTIFACTS / "provenance.json")
    expected = provenance[args.mode + "_revision"]
    actual = capture(["git", "rev-parse", "HEAD"], repo)
    if actual != expected:
        raise ValueError("Wrong source revision. Expected " + expected + ", found " + actual)
    if capture(["git", "status", "--porcelain"], repo):
        raise ValueError("Source checkout must be clean, including untracked files")
    version = capture([args.java, "-version"], repo)
    match = re.search(r'version "(\d+)(?:[.\-"]|$)', version)
    if not match or int(match.group(1)) != 21:
        raise ValueError("Use a full OpenJDK 21 runtime. Reported: " + version)
    modules = capture([args.java, "--list-modules"], repo)
    if not any(line.startswith("jdk.compiler@") for line in modules.splitlines()):
        raise ValueError("The selected Java installation lacks jdk.compiler")
    for value in [str(repo), str(out), str(ARTIFACTS)]:
        if "\n" in value or "\r" in value:
            raise ValueError("Paths containing line breaks are not supported")
    out.mkdir(parents=True, exist_ok=False)
    (out / "classes").mkdir()
    (out / "java-version.txt").write_text(version + "\n", encoding="utf-8")
    write_json(out / "run-environment.json", {
        "mode": args.mode, "source_revision": actual,
        "started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "platform": platform.platform(), "python": sys.version,
        "java_executable": args.java, "java_version": version,
        "source_checkout": str(repo), "output_directory": str(out),
        "dedicated_cpu_allocation": "not established by this driver",
    })
    return repo, out


def run(label, command, repo, out, stdout_name=None, split_stderr=False):
    print("Running " + label, flush=True)
    write_json(out / (label + ".command.json"), {"cwd": str(repo), "argv": command})
    started = time.monotonic()
    log_path = out / (stdout_name or label + ".log")
    with log_path.open("w", encoding="utf-8") as log:
        if split_stderr:
            with (out / (label + ".stderr.log")).open("w", encoding="utf-8") as error:
                result = subprocess.run(command, cwd=repo, stdout=log, stderr=error)
        else:
            result = subprocess.run(command, cwd=repo, stdout=log,
                                    stderr=subprocess.STDOUT)
    write_json(out / (label + ".status.json"), {
        "returncode": result.returncode, "elapsed_seconds": time.monotonic() - started})
    if result.returncode:
        raise RuntimeError(label + " failed. Inspect " + str(log_path))


def argfile(out, name, paths):
    target = out / name
    def quote(path):
        return '"' + str(path).replace("\\", "\\\\").replace('"', '\\"') + '"'
    target.write_text("\n".join(quote(path) for path in paths) + "\n", encoding="utf-8")
    return "@" + str(target)


def compiler(java):
    return [java, "-m", "jdk.compiler/com.sun.tools.javac.Main",
            "-source", "17", "-target", "17", "-encoding", "UTF-8"]


def summarize_cost(path):
    with path.open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(line for line in handle if not line.startswith("#")))
    if len(rows) != 44:
        raise ValueError("Expected 44 recorded benchmark samples")
    result = {}
    for peers in [2, 4, 6, 10]:
        selected = [row for row in rows if int(row["peers"]) == peers]
        if sorted(int(row["sample"]) for row in selected) != list(range(11)):
            raise ValueError("Unexpected benchmark sample set for " + str(peers))
        item = {"samples": len(selected)}
        for field in ["proof_wall_ms", "proof_cpu_ms", "verify_wall_ms", "verify_cpu_ms"]:
            values = [float(row[field]) for row in selected]
            item[field] = {"median": statistics.median(values),
                           "minimum": min(values), "maximum": max(values)}
        for field in ["binary_bytes", "bundle_command_bytes"]:
            values = {int(row[field]) for row in selected}
            if len(values) != 1:
                raise ValueError("Unexpected variable payload size")
            item[field] = values.pop()
        item["bundle_KiB"] = item["bundle_command_bytes"] / 1024
        result[str(peers)] = item
    return result


def benchmark(args, repo, out):
    source = ARTIFACTS / "ProtocolCost.java"
    run("compile-benchmark", compiler(args.java) + [
        "-sourcepath", str(repo / "modules/coronapoker-core/src/main/java"),
        "-d", str(out / "classes"), str(source)], repo, out)
    home = out / "home-benchmark"
    home.mkdir()
    run("benchmark", [args.java, "-Xms256m", "-Xmx2g", "-Djava.awt.headless=true",
        "-Duser.home=" + str(home), "-cp", str(out / "classes"), "ProtocolCost"],
        repo, out, stdout_name="results.csv", split_stderr=True)
    write_json(out / "summary.json", summarize_cost(out / "results.csv"))
    write_json(out / "input-sha256.json", {"ProtocolCost.java": digest(source)})
    print("Benchmark completed: 44 recorded samples and 20 warmups, all bundles accepted.")


def regression(args, repo, out):
    selection = load_json(ARTIFACTS / "test-selection.json")
    jars = []
    (out / "deps").mkdir()
    for dependency in load_json(ARTIFACTS / "dependencies.json"):
        target = out / "deps" / dependency["file"]
        print("Downloading " + dependency["file"], flush=True)
        with urllib.request.urlopen(dependency["url"], timeout=120) as response:
            with target.open("wb") as handle:
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    handle.write(block)
        if digest(target) != dependency["sha256"]:
            raise ValueError("Dependency checksum mismatch: " + target.name)
        jars.append(target)
    write_json(out / "dependency-sha256.json", {p.name: digest(p) for p in jars})
    qa = repo / "tools/qa/src/test/java"
    core_tests = repo / "modules/coronapoker-core/src/test/java"
    package = qa / "com/tonikelope/coronapoker"
    network = core_tests / "com/tonikelope/coronapoker/core/network"
    core = sorted((repo / "modules/coronapoker-core/src/main/java").rglob("*.java"))
    tests = [package / "crypto" / (name + ".java") for name in selection["crypto_classes"]]
    tests += [package / (name + ".java") for name in selection["state_classes"]]
    tests += [network / (name + ".java") for name in
              ["NetworkHumanGameTableIntegrationTest", "NetworkLobbyGatewayTest"]]
    hashes = {p.relative_to(repo).as_posix(): digest(p) for p in core + tests}
    if hashes != load_json(ARTIFACTS / "recorded/regression-source-sha256.json"):
        raise ValueError("Selected source files differ from the recorded regression inputs")
    write_json(out / "source-sha256.json", hashes)
    (out / "test-classes").mkdir()
    (out / "reports").mkdir()
    compile_cp = os.pathsep.join(map(str, [out / "classes"] + jars))
    for label, files, destination in [("core", core, "classes"),
                                       ("tests", tests, "test-classes")]:
        command = compiler(args.java) + ["-cp", compile_cp, "-d", str(out / destination)]
        if label == "tests":
            command += ["-sourcepath", os.pathsep.join(map(str, [qa, core_tests]))]
        run("compile-" + label, command + [argfile(out, label + "-sources.txt", files)], repo, out)
    cp = os.pathsep.join(map(str, [out / "classes", out / "test-classes"] + jars))
    def classes(names, prefix):
        return [item for name in names for item in ["--select-class", prefix + name]]
    lanes = {
        "crypto": classes(selection["crypto_classes"], "com.tonikelope.coronapoker.crypto."),
        "state": classes(selection["state_classes"], "com.tonikelope.coronapoker."),
        "normal-game": ["--select-method", selection["normal_game_method"]],
        "gateway": [item for method in selection["gateway_methods"] for item in
                    ["--select-method", selection["gateway_class"] + "#" + method]],
    }
    summaries = {}
    for label, selectors in lanes.items():
        home = out / ("home-" + label)
        home.mkdir()
        reports = out / "reports" / label
        run(label, [args.java, "-Xmx2g", "-Djava.awt.headless=true",
            "-Duser.home=" + str(home), "-cp", cp,
            "org.junit.platform.console.ConsoleLauncher", "execute"] + selectors + [
            "--disable-banner", "--disable-ansi-colors", "--details", "summary",
            "--reports-dir", str(reports)], repo, out)
        result = ET.parse(reports / "TEST-junit-jupiter.xml").getroot()
        summaries[label] = {key: result.get(key, "0") for key in
                            ["tests", "failures", "errors", "skipped", "time"]}
        write_json(out / "test-summary.json", summaries)
        if int(result.get("tests", "0")) != selection["expected_tests"][label]:
            raise ValueError("Unexpected number of tests in " + label)
        if any(int(result.get(key, "0")) for key in ["failures", "errors", "skipped"]):
            raise RuntimeError("A selected test did not pass in " + label)
    print("193 selected tests passed. Logs, command records and JUnit XML are in " + str(out))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    commands.add_parser("verify-package", help="Check package contents against SHA256SUMS")
    for name in ["benchmark", "regression"]:
        command = commands.add_parser(name)
        command.add_argument("--repo", required=True, help="Clean checkout of the recorded revision")
        command.add_argument("--out", required=True, help="New output directory outside the checkout")
        command.add_argument("--java", default="java", help="OpenJDK 21 java executable")
    args = parser.parse_args()
    if args.mode == "verify-package":
        print("Verified " + str(verify_package()) + " files against SHA256SUMS")
        return 0
    repo, out = prepare(args)
    if args.mode == "benchmark":
        benchmark(args, repo, out)
    else:
        regression(args, repo, out)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        sys.exit(1)
