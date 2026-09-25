"""Capture remaining old Money Manager screens with known tap coords."""
import subprocess
import time
from pathlib import Path

SERIAL = "RZCY81HVMQR"
PKG = "money.expense.budget.wallet.manager.track.finance.tracker"
SPLASH = f"{PKG}/meevii.beatles.moneymanage.ui.activity.SplashActivity"
OUT = Path(r"E:\codes\jaruri\docs\old-money-manager-ui")
ADB = Path(r"C:\Users\kaust\AppData\Local\Android\Sdk\platform-tools\adb.exe")


def sh(*args):
    subprocess.run([str(ADB), "-s", SERIAL, "shell", *args], capture_output=True)


def pull(remote, local):
    subprocess.run([str(ADB), "-s", SERIAL, "pull", remote, str(local)], capture_output=True)


def cap(name):
    remote = f"/sdcard/Download/mm_{name}.png"
    sh("screencap", "-p", remote)
    pull(remote, OUT / f"{name}.png")
    print("cap", name)


def tap(x, y, wait=1.0):
    sh("input", "tap", str(x), str(y))
    time.sleep(wait)


def back(n=1):
    for _ in range(n):
        sh("input", "keyevent", "4")
        time.sleep(0.5)


def open_drawer():
    # Open navigation drawer content-desc bounds ~[0,100][158,258]
    tap(79, 179, 0.9)


def main():
    subprocess.run([str(ADB), "-s", SERIAL, "shell", "am", "start", "-n", SPLASH], capture_output=True)
    time.sleep(2.5)
    cap("01_home")

    open_drawer()
    cap("02_drawer")

    # Drawer item Y positions from earlier successful Categories tap cy=825
    # Approximate list: Sign In ~450, Chart ~580, Categories ~710/825, Export ~900, Settings ~1030, Rate Us ~1160, About ~1290
    items = [
        ("Sign In", "03_sign_in", 394, 560),
        ("Chart", "03_chart", 394, 690),
        ("Categories", "03_categories", 394, 825),
        ("Export", "03_export", 394, 960),
        ("Settings", "03_settings", 394, 1095),
        ("About", "03_about", 394, 1360),
    ]
    for label, slug, x, y in items:
        back(2)
        subprocess.run([str(ADB), "-s", SERIAL, "shell", "am", "start", "-n", SPLASH], capture_output=True)
        time.sleep(2.0)
        open_drawer()
        print("tap", label, x, y)
        tap(x, y, 1.2)
        cap(slug)
        if slug == "03_settings":
            sh("input", "swipe", "540", "1700", "540", "700", "350")
            time.sleep(0.6)
            cap("03_settings_scrolled")

    # FAB add
    back(2)
    subprocess.run([str(ADB), "-s", SERIAL, "shell", "am", "start", "-n", SPLASH], capture_output=True)
    time.sleep(2.0)
    # FAB yellow bottom-right — from home screenshot roughly
    tap(980, 2140, 1.2)
    cap("04_add_chooser")
    # If category grid, tap Food
    tap(200, 450, 1.0)
    cap("04_add_form")

    print("done")


if __name__ == "__main__":
    main()
