#!/bin/bash
# Build và ký bản release bằng khoá phát hành thật. Hỏi mật khẩu keystore khi chạy (không lưu ở đâu).
# Dùng: ./release.sh            (khoá mặc định ~/my-release-key.jks, alias key0)
#       KEYSTORE=/đường/dẫn.jks KEY_ALIAS=alias ./release.sh
set -euo pipefail
cd "$(dirname "$0")"

KEYSTORE="${KEYSTORE:-$HOME/my-release-key.jks}"
KEY_ALIAS="${KEY_ALIAS:-key0}"
# Vân tay SHA-256 của chứng chỉ ký các bản đã phát hành (0.27, 0.28, 0.29). Khác vân tay này thì máy đang cài sẽ không cập nhật đè được.
EXPECTED_SHA256="853ecf48f9adf43e61ceacf7b921ac27a800e4546912b2b67ccfa4ed668106f4"

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
[ -f "$KEYSTORE" ] || { echo "Không thấy keystore: $KEYSTORE" >&2; exit 1; }

VL23_NO_GRADLE_SIGN=1 ./gradlew :app:assembleRelease   # bỏ qua ký tự động trong Gradle; ký ở bước dưới và hỏi mật khẩu
IN="$(ls app/build/outputs/apk/release/*-unsigned.apk 2>/dev/null | head -1)"
[ -n "$IN" ] || { echo "Không thấy APK chưa ký trong app/build/outputs/apk/release" >&2; exit 1; }
VER="$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | head -1 | cut -d'"' -f2)"
OUT="app/build/outputs/apk/release/VL23-Launcher-$VER.apk"

echo "Ký bằng $KEYSTORE (alias $KEY_ALIAS). Nhập mật khẩu keystore:"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --out "$OUT" "$IN"

GOT="$("$BT/apksigner" verify --print-certs "$OUT" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
if [ "$GOT" != "$EXPECTED_SHA256" ]; then
  rm -f "$OUT"
  echo "LỖI: vân tay chứng chỉ $GOT khác bản đã phát hành. Đã xoá $OUT. Không dùng khoá này." >&2
  exit 1
fi
echo "OK: $OUT (chứng chỉ khớp bản đã phát hành)"
"$BT/aapt" dump badging "$OUT" | head -1
