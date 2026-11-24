"""Compare cold and warm AuraPlay scrolling without changing app data.

Run against the optimized build installed by install-optimized.ps1. Gestures stop
if another window takes focus. Requires Python 3 and Android platform-tools.
"""
import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--output", type=Path, default=Path("app/build/reports/startup-scroll/check"))
    args = parser.parse_args()
    if not args.adb or args.runs < 1:
        parser.error("Supply --adb PATH and a positive --runs value.")
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])

    def run(*command):
        result = subprocess.run(adb + list(command), capture_output=True, text=True, check=True)
        return result.stdout

    if not args.serial:
        devices = re.findall(r"^(\S+)\s+device\s*$", run("devices"), re.MULTILINE)
        if len(devices) != 1:
            parser.error("Connect one authorized device, or select one with --serial.")
        adb.extend(["-s", devices[0]])
    dimensions = re.findall(r"(\d+)x(\d+)", run("shell", "wm", "size"))
    width, height = map(int, dimensions[-1])
    x, top, bottom = str(width // 2), str(height * 30 // 100), str(height * 78 // 100)
    args.output.mkdir(parents=True, exist_ok=True)
    package = "com.example.auraplay"

    def scroll(reverse=False):
        for _ in range(6):
            focus = re.search(r"mCurrentFocus=(.*)", run("shell", "dumpsys", "window"))
            if focus is None or package + "/" not in focus.group(1):
                raise RuntimeError("Stopped gestures because AuraPlay lost focus. Close overlays and retry.")
            run("shell", "input", "swipe", x, top if reverse else bottom, x, bottom if reverse else top, "250")

    def capture(label):
        report = run("shell", "dumpsys", "gfxinfo", package, "framestats")
        (args.output / (label + ".txt")).write_text(report, encoding="utf-8")
        frames = re.search(r"Total frames rendered: (\d+)", report)
        jank = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", report)
        p95 = re.search(r"95th percentile: (\d+)ms", report)
        if not all((frames, jank, p95)):
            raise RuntimeError("Android did not return frame metrics. Ensure the library is visible.")
        if int(frames[1]) < 60:
            raise RuntimeError("Too few frames for a scrolling comparison. Open a populated music library and retry.")
        return {"frames": int(frames[1]), "janky_frames": int(jank[1]), "jank_percent": float(jank[2]), "p95_ms": int(p95[1])}

    package_info = run("shell", "dumpsys", "package", package)
    if re.search(r"^\s*(?:pkgFlags|flags)=\[.*\bDEBUGGABLE\b", package_info, re.MULTILINE):
        raise RuntimeError("Install the optimized performance build before measuring scrolling.")
    compilation = re.findall(r"^.*\[status=.*$", package_info, re.MULTILINE)
    results = {"compilation": [line.strip() for line in compilation], "runs": []}
    for index in range(args.runs):
        run("shell", "am", "force-stop", package)
        launch = run("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
        (args.output / f"{index + 1}-launch.txt").write_text(launch, encoding="utf-8")
        scroll()
        cold = capture(f"{index + 1}-cold")
        scroll(reverse=True)
        run("shell", "dumpsys", "gfxinfo", package, "reset")
        scroll()
        warm = capture(f"{index + 1}-warm")
        result = {"run": index + 1, "cold": cold, "warm": warm}
        results["runs"].append(result)
        print(json.dumps(result), flush=True)
        (args.output / "summary.json").write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
