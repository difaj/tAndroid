"""Compare glass shader output on an Android 13+ device, without installing an app.

Run: python tests/graphics/check_liquid_glass.py --sdk <Android SDK directory>
Requires adb, javac and java on PATH (or the Android SDK's platform-tools/adb).
"""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Specify --sdk or set ANDROID_HOME")
    sdk = Path(args.sdk)
    here = Path(__file__).resolve().parent
    root = here.parent.parent
    android_jar = sdk / "platforms/android-36/android.jar"
    d8_jar = sdk / "build-tools/36.0.0/lib/d8.jar"
    adb = str(sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    prefix = f"/data/local/tmp/yehuda-glass-check-{os.getpid()}"
    device_files = [prefix + suffix for suffix in (".dex", "-original.agsl", "-optimized.agsl")]

    def run(*command):
        subprocess.run(command, check=True)

    with tempfile.TemporaryDirectory(prefix="yehuda-glass-check-") as temp:
        temp = Path(temp)
        classes, dex = temp / "classes", temp / "dex"
        classes.mkdir()
        dex.mkdir()
        run("javac", "--release", "8", "-cp", str(android_jar), "-d", str(classes),
            str(here / "LiquidGlassShaderCheck.java"))
        run("java", "-cp", str(d8_jar), "com.android.tools.r8.D8", "--min-api", "33",
            "--lib", str(android_jar), "--output", str(dex),
            str(classes / "LiquidGlassShaderCheck.class"))
        try:
            for source, target in zip((dex / "classes.dex", here / "liquid_glass_reference.agsl",
                                       root / "TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl"),
                                      device_files):
                run(adb, "push", str(source), target)
            run(adb, "shell", f"CLASSPATH={device_files[0]}", "app_process", "/system/bin",
                "LiquidGlassShaderCheck", device_files[1], device_files[2])
        finally:
            # Only remove this invocation's three temporary test files.
            run(adb, "shell", "rm", "-f", *device_files)


if __name__ == "__main__":
    main()
