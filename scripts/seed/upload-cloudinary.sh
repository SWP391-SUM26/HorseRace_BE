#!/usr/bin/env bash
#
# Upload ảnh/video demo lên Cloudinary cho bộ seed test (db/seed_demo.sql).
#
# - Dùng Cloudinary *signed upload* API trực tiếp bằng curl + openssl (không cần SDK/node/python).
# - Dùng *remote fetch*: truyền URL nguồn cho Cloudinary tự tải về, máy local không phải tải ảnh.
# - public_id CỐ ĐỊNH + overwrite=true  => idempotent. Chạy lại bao nhiêu lần cũng ra đúng URL đó,
#   không sinh asset rác. Đây là lý do seed_demo.sql hardcode được URL trước khi upload.
#
# Credentials đọc từ app.cloudinary.url trong application-local.properties (file đã gitignore),
# hoặc từ biến môi trường CLOUDINARY_URL. KHÔNG hardcode secret vào file này.
#
#   Usage:  bash scripts/seed/upload-cloudinary.sh
#
# Ảnh nguồn do scripts/seed/build-seed-images.py dựng sẵn vào scripts/seed/build/ :
#   - ngựa & giải đấu: ẢNH ĐUA NGỰA THẬT (nguồn + license ghi ở image-credits.md)
#   - avatar / giấy tờ / photo-finish: tự vẽ bằng Pillow, không dùng mặt người thật
# (Trước đây toàn bộ lấy ngẫu nhiên từ picsum.photos nên không ăn nhập gì với đua ngựa.)

set -uo pipefail

BE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROPS="$BE_DIR/src/main/resources/application-local.properties"

# ---------- 1. Lấy credentials ----------
CLOUDINARY_URL="${CLOUDINARY_URL:-}"
if [[ -z "$CLOUDINARY_URL" && -f "$PROPS" ]]; then
    CLOUDINARY_URL="$(grep -E '^[[:space:]]*app\.cloudinary\.url[[:space:]]*=' "$PROPS" \
                      | head -1 | cut -d= -f2- | tr -d '[:space:]')"
fi

if [[ ! "$CLOUDINARY_URL" =~ ^cloudinary://([^:]+):([^@]+)@(.+)$ ]]; then
    echo "FATAL: không đọc được app.cloudinary.url (dạng cloudinary://key:secret@cloud)." >&2
    echo "       Đặt biến CLOUDINARY_URL hoặc thêm app.cloudinary.url vào $PROPS" >&2
    exit 1
fi
API_KEY="${BASH_REMATCH[1]}"
API_SECRET="${BASH_REMATCH[2]}"
CLOUD_NAME="${BASH_REMATCH[3]}"

echo "Cloud: $CLOUD_NAME   (api_key ${API_KEY:0:6}…, secret ẩn)"
echo

MANIFEST="$BE_DIR/scripts/seed/cloudinary-manifest.txt"
: > "$MANIFEST"

OK=0
FAIL=0

# ---------- 2. Hàm upload ----------
# upload_local <resource_type> <public_id> <local_path>
# Giống upload() nhưng gửi FILE THẬT (multipart) thay vì đưa URL cho Cloudinary tự tải.
# Chữ ký ký đúng các param như nhau; chỉ khác cách truyền `file`.
upload_local() {
    local kind="$1" public_id="$2" path="$3"
    local ts sig params resp url

    if [[ ! -f "$path" ]]; then
        printf 'FAIL  %-34s %s\n' "$public_id" "missing local file: $path"
        FAIL=$((FAIL + 1)); return
    fi

    ts="$(date +%s)"
    params="invalidate=true&overwrite=true&public_id=${public_id}&timestamp=${ts}"
    sig="$(printf '%s%s' "$params" "$API_SECRET" | openssl dgst -sha1 -hex | awk '{print $NF}')"

    resp="$(curl -s --max-time 120 \
        -X POST "https://api.cloudinary.com/v1_1/${CLOUD_NAME}/${kind}/upload" \
        -F "file=@${path}" \
        -F "public_id=${public_id}" \
        -F "timestamp=${ts}" \
        -F "overwrite=true" \
        -F "invalidate=true" \
        -F "api_key=${API_KEY}" \
        -F "signature=${sig}")"

    url="$(printf '%s' "$resp" | sed -n 's/.*"secure_url":"\([^"]*\)".*/\1/p' | sed 's/\\\//\//g')"

    if [[ -n "$url" ]]; then
        printf 'OK    %-34s %s\n' "$public_id" "$url"
        printf '%s\t%s\n' "$public_id" "$url" >> "$MANIFEST"
        OK=$((OK + 1))
    else
        printf 'FAIL  %-34s %s\n' "$public_id" \
            "$(printf '%s' "$resp" | sed -n 's/.*"message":"\([^"]*\)".*/\1/p')"
        FAIL=$((FAIL + 1))
    fi
}


# upload <resource_type> <public_id> <source_url>
upload() {
    local kind="$1" public_id="$2" src="$3"
    local ts sig params resp url

    ts="$(date +%s)"
    # Cloudinary ký các param (trừ file/api_key/resource_type/cloud_name) theo thứ tự alphabet.
    params="invalidate=true&overwrite=true&public_id=${public_id}&timestamp=${ts}"
    sig="$(printf '%s%s' "$params" "$API_SECRET" | openssl dgst -sha1 -hex | awk '{print $NF}')"

    resp="$(curl -s --max-time 90 \
        -X POST "https://api.cloudinary.com/v1_1/${CLOUD_NAME}/${kind}/upload" \
        --data-urlencode "file=${src}" \
        --data-urlencode "public_id=${public_id}" \
        --data-urlencode "timestamp=${ts}" \
        --data-urlencode "overwrite=true" \
        --data-urlencode "invalidate=true" \
        --data-urlencode "api_key=${API_KEY}" \
        --data-urlencode "signature=${sig}")"

    url="$(printf '%s' "$resp" | sed -n 's/.*"secure_url":"\([^"]*\)".*/\1/p' | sed 's/\\\//\//g')"

    if [[ -n "$url" ]]; then
        printf 'OK    %-34s %s\n' "$public_id" "$url"
        printf '%s\t%s\n' "$public_id" "$url" >> "$MANIFEST"
        OK=$((OK + 1))
    else
        printf 'FAIL  %-34s %s\n' "$public_id" \
            "$(printf '%s' "$resp" | sed -n 's/.*"message":"\([^"]*\)".*/\1/p')"
        FAIL=$((FAIL + 1))
    fi
}

img() { echo "https://picsum.photos/seed/$1/${2:-800}/${3:-600}"; }

# ---------- 3. Upload theo từng nhóm ----------
# Nguồn là file THẬT trong scripts/seed/build/ (do build-seed-images.py dựng).
# public_id giữ NGUYÊN như trước + overwrite=true -> URL trong seed_demo.sql không phải đổi.
BUILD="${SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}/build"
if [[ ! -d "$BUILD" ]]; then
    echo "Chưa có $BUILD — chạy: python3 scripts/seed/build-seed-images.py" >&2
    exit 1
fi

echo "── avatars (10) — app_user.avatar_url ──"
for i in $(seq -w 1 10); do
    upload_local image "avatars/seed-avatar-$i" "$BUILD/avatars/seed-avatar-$i.jpg"
done

echo "── horses (10) — horse.image_url ──"
for i in $(seq -w 1 10); do
    upload_local image "horses/seed-horse-$i" "$BUILD/horses/seed-horse-$i.jpg"
done

echo "── tournaments (10) — tournament.image_url ──"
for i in $(seq -w 1 10); do
    upload_local image "tournaments/seed-tournament-$i" "$BUILD/tournaments/seed-tournament-$i.jpg"
done

echo "── medical (10) — horse_medical_record.file_url ──"
for i in $(seq -w 1 10); do
    upload_local image "medical/seed-medical-$i" "$BUILD/medical/seed-medical-$i.jpg"
done

echo "── photofinish (10) — race.photofinish_url ──"
for i in $(seq -w 1 10); do
    upload_local image "photofinish/seed-photofinish-$i" "$BUILD/photofinish/seed-photofinish-$i.jpg"
done

echo "── licenses (5) — jockey_profile.jockey_license_url ──"
for i in 1 2 3 4 5; do
    upload_local image "licenses/seed-license-$i" "$BUILD/licenses/seed-license-$i.jpg"
done

echo "── certificates (5) — jockey_profile.fitness_certificate_url ──"
for i in 1 2 3 4 5; do
    upload_local image "certificates/seed-certificate-$i" "$BUILD/certificates/seed-certificate-$i.jpg"
done

echo "── applications (10) — membership_application.avatar_url ──"
for i in $(seq -w 1 10); do
    upload_local image "applications/seed-applicant-$i" "$BUILD/applications/seed-applicant-$i.jpg"
done

# Video: KHÔNG upload nữa. Trước đây 3 "feed đua trực tiếp" là clip con chó demo của Cloudinary,
# và FE lại nhúng .mp4 bằng <iframe> nên cũng không phát được. seed_demo.sql đặt
# race.video_feed_url = NULL và FE hiển thị empty state "No live video feed available".

# ---------- 4. Tổng kết ----------
echo
echo "══════════════════════════════════════════"
echo "  OK: $OK    FAIL: $FAIL"
echo "  Manifest: $MANIFEST"
echo "══════════════════════════════════════════"

[[ "$FAIL" -eq 0 ]] || { echo "Có asset upload lỗi -> ĐỪNG seed, seed sẽ trỏ vào URL chết." >&2; exit 1; }
