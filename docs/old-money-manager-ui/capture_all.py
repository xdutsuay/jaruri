"""ADB UI Automator: open drawer items and capture old Money Manager screens."""
import re
import subprocess
import sys
import time
from pathlib import Path

SERIAL = "RZCY81HVMQR"
PKG = "money.expense.budget.wallet.manager.track.finance.tracker"
SPLASH = f"{PKG}/meevii.beatles.moneymanage.ui.activity.SplashActivity"
OUT = Path(r"E:\codes\jaruri\docs\old-money-manager-ui")
ADB = Path(r"C:\Users\kaust\AppData\Local\Android\Sdk\platform-tools\adb.exe")
UI = OUT / "ui.xml"


def adb(*args):
    cmd = [str(ADB), "-s", SERIAL, *args]
    return subprocess.run(cmd, capture_output=True)


def shell(*args):
    return adb("shell", *args)


def dump():
    shell("uiautomator", "dump", "/sdcard/Download/ui.xml")
    adb("pull", "/sdcard/Download/ui.xml", str(UI))


def xml_text():
    return UI.read_text(encoding="utf-8", errors="ignore")


def nodes(xml: str):
    # each node is <node ... />
    for m in re.finditer(r"<node\b([^>]*)/?>", xml):
        attrs = m.group(1)
        def attr(name):
            mm = re.search(rf'{name}="([^"]*)"', attrs)
            return mm.group(1) if mm else ""
        b = attr("bounds")
        bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not bm:
            continue
        x1, y1, x2, y2 = map(int, bm.groups())
        yield {
            "text": attr("text"),
            "desc": attr("content-desc"),
            "clickable": attr("clickable") == "true",
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "y1": y1,
        }


def find_tap(label: str):
    dump()
    xml = xml_text()
    for n in nodes(xml):
        if n["text"] == label or n["desc"] == label:
            return n["cx"], n["cy"]
    # partial
    for n in nodes(xml):
        if label.lower() in (n["text"] or "").lower():
            return n["cx"], n["cy"]
    return None


def tap_xy(x, y):
    shell("input", "tap", str(x), str(y))
    time.sleep(1.0)


def tap_label(label: str) -> bool:
    pt = find_tap(label)
    if not pt:
        print(f"MISS {label}")
        return False
    print(f"tap {label} -> {pt}")
    tap_xy(*pt)
    return True


def cap(name: str):
    remote = f"/sdcard/Download/mm_{name}.png"
    local = OUT / f"{name}.png"
    shell("screencap", "-p", remote)
    adb("pull", remote, str(local))
    print(f"cap {name} -> {local} ({local.stat().st_size if local.exists() else 0})")


def open_drawer():
    # hamburger approx; also try content-desc Open navigation drawer
    if not tap_label("Open navigation drawer"):
        tap_xy(72, 155)
        time.sleep(0.8)


def back():
    shell("input", "keyevent", "4")
    time.sleep(0.6)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "start", "-n", SPLASH)
    time.sleep(2.5)
    cap("01_home")

    open_drawer()
    time.sleep(0.5)
    cap("02_drawer")
    dump()
    texts = [n["text"] for n in nodes(xml_text()) if n["text"]]
    print("drawer texts:", texts)

    for label, slug in [
        ("Chart", "03_chart"),
        ("Categories", "03_categories"),
        ("Export", "03_export"),
        ("Settings", "03_settings"),
        ("About", "03_about"),
        ("Sign In", "03_sign_in"),
        ("Rate Us", "03_rate_us"),
    ]:
        back()
        open_drawer()
        time.sleep(0.4)
        if tap_label(label):
            time.sleep(1.0)
            cap(slug)
            # capture settings toggles by scrolling if settings
            if label == "Settings":
                shell("input", "swipe", "540", "1600", "540", "600", "300")
                time.sleep(0.5)
                cap("03_settings_scrolled")

    # Add transaction via FAB (bottom-right)
    back(); back()
    adb("shell", "am", "start", "-n", SPLASH)
    time.sleep(2.0)
    dump()
    fab = None
    for n in nodes(xml_text()):
        d = (n["desc"] or "").lower()
        if "add" in d or n["desc"] == "+" or "floating" in d:
            fab = n
            break
    if fab:
        tap_xy(fab["cx"], fab["cy"])
    else:
        # typical FAB
        tap_xy(980, 2100)
    time.sleep(1.0)
    cap("04_add_transaction")
    dump()
    print("add texts:", [n["text"] for n in nodes(xml_text()) if n["text"]])

    # Try open category spinner / income-expense radios by tapping common labels
    for label, slug in [
        ("Expense", "04_add_expense"),
        ("Income", "04_add_income"),
    ]:
        if tap_label(label):
            time.sleep(0.5)
            cap(slug)

    print("done")


if __name__ == "__main__":
    main()
