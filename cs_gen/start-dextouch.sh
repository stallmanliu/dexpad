#!/usr/bin/env bash
# Install DeX Pad and open it. The phone starts the touch service itself.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
APP="$REPO/dex-touchpad"
APK="$APP/build/app.apk"

pick_serial() {
  if [ -n "${DEX_SERIAL:-}" ]; then
    echo "$DEX_SERIAL"
    return
  fi
  adb devices -l | awk '/model:SM_S711W|device:r11q/{print $1; exit}'
}

SERIAL="$(pick_serial)"
if [ -z "$SERIAL" ]; then
  echo "没有找到 S23 FE。先执行 adb connect，或设置 DEX_SERIAL。" >&2
  exit 1
fi
echo "device $SERIAL"

if [ "${1:-}" = "install" ]; then
  if [ ! -f "$APK" ]; then
    echo "缺少 $APK，先运行 build-dextouch.sh --no-install" >&2
    exit 1
  fi
  adb -s "$SERIAL" install -r "$APK"
fi

adb -s "$SERIAL" shell pm grant com.daniel.dextouch android.permission.WRITE_SECURE_SETTINGS || true
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP || true
adb -s "$SERIAL" shell am start --display 0 --user 0 -n com.daniel.dextouch/.MainActivity
echo "已打开 DeX Pad。触控服务由手机自己启动，不需要这台电脑一直开着。"
