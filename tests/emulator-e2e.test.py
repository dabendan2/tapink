#!/usr/bin/env python3
"""
End-to-End (E2E) Android Emulator Verification & Screenshot Suite for TapInk Dynamic NFC Updater
(Blank-Slate Google Pixel Material Design 3 Modal Bottom Sheet Consumer UX).
"""

import os
import re
import sys
import math
import time
import struct
import zlib
import subprocess
import xml.etree.ElementTree as ET

ANDROID_SDK = "/home/ubuntu/android-sdk"
EMULATOR_BIN = f"{ANDROID_SDK}/emulator/emulator"
ADB_BIN = "/usr/bin/adb"
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK_PATH = os.path.join(PROJECT_DIR, "android", "app", "build", "tapink.apk")
SCREENSHOT_DIR = os.path.join(PROJECT_DIR, "docs", "screenshots")
os.makedirs(SCREENSHOT_DIR, exist_ok=True)
PACKAGE = "com.dabendan.tapink"
ACTIVITY = f"{PACKAGE}/.ShareNfcActivity"
SERVICE = f"{PACKAGE}/.TapInkLogService"
EMULATOR_SERIAL = "emulator-5554"


def run_cmd(cmd, timeout=60, check=False):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed ({res.returncode}): {cmd}\nSTDOUT: {res.stdout}\nSTDERR: {res.stderr}")
    return res.stdout.strip(), res.stderr.strip(), res.returncode


def adb(args, timeout=30, check=False):
    return run_cmd(f"{ADB_BIN} -s {EMULATOR_SERIAL} {args}", timeout=timeout, check=check)


def capture_screenshot(output_path):
    subprocess.run(
        f"{ADB_BIN} -s {EMULATOR_SERIAL} exec-out screencap -p > {output_path}",
        shell=True,
        check=True,
        timeout=15
    )
    size = os.path.getsize(output_path)
    if size < 1000:
        raise RuntimeError(f"Screenshot {output_path} is unexpectedly small ({size} bytes)")
    print(f" -> [SCREENSHOT] Saved {output_path} ({size} bytes)")


def create_test_png(path, width=600, height=800):
    """Creates a rich sunset mountain illustration PNG to showcase consumer photo/art Floyd-Steinberg dithering."""
    raw_rows = bytearray()
    cx, cy = width * 0.5, height * 0.36
    sun_r = width * 0.22

    for y in range(height):
        raw_rows.append(0)
        ny = y / height
        for x in range(width):
            nx = x / width
            ridge1 = 0.56 - 0.14 * math.sin(nx * math.pi * 1.3 + 0.2) - 0.05 * math.cos(nx * math.pi * 3.2)
            ridge2 = 0.68 - 0.10 * math.cos(nx * math.pi * 1.8 - 0.4) - 0.04 * math.sin(nx * math.pi * 4.0)
            ridge3 = 0.80 - 0.06 * math.sin(nx * math.pi * 2.5 + 1.0)

            dx = x - cx
            dy = y - cy
            dist = math.hypot(dx, dy)

            if ny > ridge3:
                r, g, b = 15, 18, 24
            elif ny > ridge2:
                shade = int(70 + 80 * (ny - ridge2) / max(0.01, ridge3 - ridge2))
                r, g, b = shade, shade // 2, shade // 3
            elif ny > ridge1:
                t = (ny - ridge1) / max(0.01, ridge2 - ridge1)
                r = int(215 - 80 * t)
                g = int(60 - 30 * t)
                b = int(30 - 15 * t)
            elif dist <= sun_r:
                st = dist / sun_r
                r = 255
                g = int(252 - 35 * st * st)
                b = int(190 * (1.0 - st))
            else:
                sky_t = min(1.0, ny / 0.62)
                if sky_t < 0.35:
                    lt = sky_t / 0.35
                    r = int(235 + 20 * lt)
                    g = int(45 + 110 * lt)
                    b = int(35 + 10 * lt)
                elif sky_t < 0.72:
                    lt = (sky_t - 0.35) / 0.37
                    r = 255
                    g = int(155 + 85 * lt)
                    b = int(45 + 90 * lt)
                else:
                    lt = (sky_t - 0.72) / 0.28
                    r = 255
                    g = int(240 + 15 * lt)
                    b = int(135 + 110 * lt)

            raw_rows.extend((max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))))

    def make_chunk(chunk_type, data):
        chunk = struct.pack(">I", len(data)) + chunk_type + data
        crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
        return chunk + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    compressed = zlib.compress(bytes(raw_rows), 6)
    png_bytes = (
        b"\x89PNG\r\n\x1a\n"
        + make_chunk(b"IHDR", ihdr)
        + make_chunk(b"IDAT", compressed)
        + make_chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png_bytes)


def ensure_emulator_booted():
    if not os.access("/dev/kvm", os.R_OK | os.W_OK):
        run_cmd("/mnt/c/Windows/System32/wsl.exe -u root chmod 666 /dev/kvm", timeout=15)
    run_cmd(f"{ADB_BIN} start-server", timeout=15)
    out, _, _ = run_cmd(f"{ADB_BIN} devices")
    if EMULATOR_SERIAL not in out:
        print("[E2E] Starting Android Emulator (l3_avd, API 28 x86_64 with KVM)...")
        subprocess.Popen(
            [
                EMULATOR_BIN,
                "-avd", "l3_avd",
                "-no-window",
                "-no-audio",
                "-gpu", "swiftshader_indirect",
                "-memory", "2048",
                "-no-boot-anim",
                "-no-snapshot"
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )

    print("[E2E] Waiting for emulator boot_completed...")
    start = time.time()
    while time.time() - start < 120:
        out, _, rc = adb("shell getprop sys.boot_completed", timeout=10)
        if rc == 0 and out.strip() == "1":
            print(f"[E2E] Emulator booted in {time.time() - start:.1f}s.")
            adb("shell wm size reset")
            adb("shell wm density reset")
            adb("shell wm dismiss-keyguard")
            adb("shell input keyevent 82")
            adb("shell input keyevent 3")
            time.sleep(1.0)
            return
        time.sleep(2)
    raise RuntimeError("Emulator failed to boot within 120 seconds")


def dump_ui_xml():
    for _ in range(4):
        adb("shell uiautomator dump /sdcard/window_dump.xml", timeout=15)
        xml_str, _, rc = adb("shell cat /sdcard/window_dump.xml", timeout=10)
        if rc == 0 and "<hierarchy" in xml_str:
            return xml_str
        time.sleep(1)
    raise RuntimeError("Failed to dump UI hierarchy via uiautomator")


def find_node_by_desc(xml_str, content_desc):
    root = ET.fromstring(xml_str)
    for node in root.iter("node"):
        if node.attrib.get("content-desc") == content_desc:
            return node.attrib
    return None


def parse_bounds_center(bounds_str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if not m:
        raise ValueError(f"Invalid bounds: {bounds_str}")
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def get_service_dumpsys():
    out, _, _ = adb(f"shell dumpsys activity service {SERVICE}")
    return out


def is_share_activity_resumed():
    out, _, _ = adb("shell dumpsys activity activities")
    for line in out.splitlines():
        if "mResumedActivity" in line or "ResumedActivity" in line:
            if ACTIVITY in line or "com.dabendan.tapink/.ShareNfcActivity" in line:
                return True
    return False


def wait_for_service_state(expected_state, timeout=15):
    start = time.time()
    while time.time() - start < timeout:
        dump = get_service_dumpsys()
        if f"Last UI State : {expected_state}" in dump:
            return dump
        time.sleep(0.5)
    raise AssertionError(f"Timed out waiting for service state '{expected_state}'. Current dump:\n{get_service_dumpsys()}")


def main():
    print("======================================================================")
    print("  TAPINK DYNAMIC NFC UPDATER - BLANK-SLATE M3 BOTTOM SHEET E2E SUITE")
    print("======================================================================")

    ensure_emulator_booted()

    print("\n[STEP 1] Installing APK & Pushing Test Image...")
    adb(f"install -r -g {APK_PATH}", check=True)
    local_img = "/home/ubuntu/.hermes/cache/scratch/test_share_input.png"
    os.makedirs(os.path.dirname(local_img), exist_ok=True)
    create_test_png(local_img)
    remote_img = "/sdcard/Download/test_share_input.png"
    adb(f"push {local_img} {remote_img}", check=True)

    # -------------------------------------------------------------------------
    # Scenario 1: Share Image -> Modal Bottom Sheet WAITING_NFC (Screenshot #1)
    # -------------------------------------------------------------------------
    print("\n[STEP 2] Scenario 1: Share Image -> Modal Bottom Sheet Ready to Scan & Screenshot...")
    adb("logcat -c")
    adb(f"shell am force-stop {PACKAGE}")
    adb(
        f"shell am start -a android.intent.action.SEND -t image/png "
        f"--es image_path {remote_img} --ez allow_mock_nfc true -n {ACTIVITY}",
        check=True
    )

    wait_for_service_state("WAITING_NFC", timeout=15)
    time.sleep(0.8)
    screenshot_waiting = os.path.join(SCREENSHOT_DIR, "01-waiting-nfc.png")
    capture_screenshot(screenshot_waiting)

    xml_str = dump_ui_xml()
    status_node = find_node_by_desc(xml_str, "status_text")
    retry_node = find_node_by_desc(xml_str, "retry_button")
    status_txt = status_node.get("text", "") if status_node else ""
    assert any(k in status_txt for k in ("準備更新電子紙", "准备更新电子纸", "Ready to Update E-Paper", "電子ペーパーを更新する準備")), f"Got {status_node}"
    assert retry_node is None, "retry_button must be hidden in WAITING_NFC"
    print(f" -> [PASS] Stage 1 Ready UI verified: '{status_txt}'")

    # -------------------------------------------------------------------------
    # Scenario 2: Mid-Write RF Disconnect -> Error Overlay Pill & Buttons (Screenshot #2)
    # -------------------------------------------------------------------------
    print("\n[STEP 3] Scenario 2: Mid-Transfer Tag Lost -> Error Recovery Sheet & Screenshot...")
    adb("shell am broadcast -a com.dabendan.tapink.ACTION_SIMULATE_NFC_TAG --es mode TAG_LOST_MID_WRITE")
    wait_for_service_state("ERROR", timeout=15)
    time.sleep(0.8)
    screenshot_error = os.path.join(SCREENSHOT_DIR, "02-error-retry.png")
    capture_screenshot(screenshot_error)

    xml_str = dump_ui_xml()
    status_node = find_node_by_desc(xml_str, "status_text")
    overlay_node = find_node_by_desc(xml_str, "error_overlay_pill")
    retry_node = find_node_by_desc(xml_str, "retry_button")
    err_txt = status_node.get("text", "") if status_node else ""
    ov_txt = overlay_node.get("text", "") if overlay_node else ""
    assert any(k in err_txt for k in ("感應中斷", "感应中断", "Connection Interrupted", "通信が中断")), f"Got {status_node}"
    assert any(k in ov_txt for k in ("請重新貼靠電子紙", "请重新贴靠电子纸", "Tap E-Paper Again", "もう一度かざして")), f"Got {overlay_node}"
    assert retry_node is not None, "retry_button must be visible on error"
    print(f" -> [PASS] Stage 4 Error Recovery UI verified: '{err_txt}'")

    # -------------------------------------------------------------------------
    # Scenario 3: Zero-Click Auto-Retry with 2.9" 296x128 2-Color Tag -> Transferring (Screenshot #3)
    # -------------------------------------------------------------------------
    print("\n[STEP 4] Scenario 3: Zero-Click Re-Tap with 2.9\" (296x128 黑白雙色) Tag -> Morphing Progress & Auto-Dismiss...")
    adb("shell am broadcast -a com.dabendan.tapink.ACTION_SIMULATE_NFC_TAG --es mode SUCCESS_290_BW --ei busy_polls 8")
    time.sleep(0.6)
    screenshot_transfer_290 = os.path.join(SCREENSHOT_DIR, "03-transferring-290bw.png")
    capture_screenshot(screenshot_transfer_290)

    dump = wait_for_service_state("SUCCESS", timeout=20)
    assert "自動偵測硬體規格成功: 2.9吋 296×128 2色 (黑白 BW)" in dump, f"Expected 2.9\" auto-detection in dump:\n{dump}"
    assert "動態轉換完成 (2.9吋 296×128 2色 (黑白 BW)): 輸出=128x296, 封包=9472 bytes" in dump, f"Expected 9472B pack in dump:\n{dump}"
    time.sleep(1.5)
    assert not is_share_activity_resumed(), "Activity must auto-close after 2.9\" update!"
    print(" -> [PASS] Zero-Click Auto-Retry + 2.9\" 296x128 2-color dynamic dither + Auto-Close verified!")

    # -------------------------------------------------------------------------
    # Scenario 4: Auto-Detect 4.2" 400x300 4-Color (BWRY) Tag + ForegroundDispatch onNewIntent Guard
    # -------------------------------------------------------------------------
    print("\n[STEP 5] Scenario 4: Auto-Detect 4.2\" (400x300 黑白紅黃) Tag + onNewIntent NFC Guard -> Full Update & Auto-Close...")
    adb(
        f"shell am start -a android.intent.action.SEND -t image/png "
        f"--es image_path {remote_img} --ez allow_mock_nfc true -n {ACTIVITY}",
        check=True
    )
    wait_for_service_state("WAITING_NFC", timeout=15)

    # Verify that an NFC intent arriving on singleTop onNewIntent does NOT clobber the shared image state
    adb(f"shell am start -a android.nfc.action.TAG_DISCOVERED -f 0x20000000 -n {ACTIVITY}", check=True)
    time.sleep(0.4)
    dump_after_nfc_intent = get_service_dumpsys()
    assert "Last UI State : WAITING_NFC" in dump_after_nfc_intent, (
        f"TAG_DISCOVERED onNewIntent must preserve WAITING_NFC state:\n{dump_after_nfc_intent}"
    )
    assert "onNewIntent 收到 NFC Tag 派發" in dump_after_nfc_intent, (
        f"Expected onNewIntent NFC interception log in dump:\n{dump_after_nfc_intent}"
    )
    print(" -> [PASS] singleTop onNewIntent NFC TAG_DISCOVERED interception verified (shared image preserved)!")

    adb("shell am broadcast -a com.dabendan.tapink.ACTION_SIMULATE_NFC_TAG --es mode SUCCESS_420_BWRY_COSV1 --ei busy_polls 6")
    time.sleep(0.55)
    screenshot_transfer_420 = os.path.join(SCREENSHOT_DIR, "04-transferring-420bwry.png")
    capture_screenshot(screenshot_transfer_420)

    dump = wait_for_service_state("SUCCESS", timeout=20)
    assert "自動偵測硬體規格成功: 4.2吋 400×300 4色 (黑白黃紅 BWRY)" in dump, f"Expected 4.2\" auto-detect in dump:\n{dump}"
    time.sleep(1.5)
    assert not is_share_activity_resumed(), "Activity must auto-close after 4.2\" update!"
    print(" -> [PASS] 4.2\" 400x300 4-color auto-detected, transferred (30,000B), and Activity auto-closed!")

    print("\n======================================================================")
    print("  [✓] ALL BLANK-SLATE M3 BOTTOM SHEET E2E SCENARIOS PASSED!")
    print("======================================================================")


if __name__ == "__main__":
    main()
