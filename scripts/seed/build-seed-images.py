#!/usr/bin/env python3
"""
Builds every seed image for the HorseRace demo, into scripts/seed/build/.

Two kinds of asset:

  1. REAL PHOTOS (horses, tournaments) — curated horse-racing photographs, centre-cropped to the
     aspect ratio the UI actually renders them at. Sources and licences are recorded in
     image-credits.md; nothing here is a random stock photo.

  2. GENERATED (avatars, documents, photo-finish) — drawn here with Pillow, so they are
     licence-clean and contain no real person's face. Jockey avatars use racing SILKS (cap +
     pattern + initials), which is how riders are actually identified on a racecourse — it fits the
     domain far better than a stock headshot, and avoids attaching a real human's photograph to a
     fabricated account.

Deterministic: same inputs -> byte-identical outputs, so re-running never churns the CDN.

Usage:  python3 scripts/seed/build-seed-images.py [--src-root /path/to/SWP391]
"""
from __future__ import annotations

import argparse
import hashlib
import os
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
except ImportError:
    sys.exit("Pillow is required:  python3 -m pip install pillow")

HERE = Path(__file__).resolve().parent
OUT = HERE / "build"

# ─────────────────────────── fonts ───────────────────────────
FONT_DIRS = ["/System/Library/Fonts/Supplemental", "/System/Library/Fonts",
             "/usr/share/fonts/truetype/dejavu", "/Library/Fonts"]
def _font(names, size):
    for d in FONT_DIRS:
        for n in names:
            p = Path(d) / n
            if p.exists():
                try:
                    return ImageFont.truetype(str(p), size)
                except OSError:
                    pass
    return ImageFont.load_default()

def bold(sz):    return _font(["Arial Bold.ttf", "Helvetica.ttc", "DejaVuSans-Bold.ttf"], sz)
def regular(sz): return _font(["Arial.ttf", "Helvetica.ttc", "DejaVuSans.ttf"], sz)

# ─────────────────────────── helpers ───────────────────────────
def cover(img: Image.Image, w: int, h: int) -> Image.Image:
    """Centre-crop to the target aspect then resize — never distorts, matches CSS object-cover."""
    img = img.convert("RGB")
    tr, sr = w / h, img.width / img.height
    if sr > tr:                      # too wide -> trim sides
        nw = int(img.height * tr)
        img = img.crop(((img.width - nw) // 2, 0, (img.width + nw) // 2, img.height))
    else:                            # too tall -> trim top/bottom, biased slightly high
        nh = int(img.width / tr)
        top = int((img.height - nh) * 0.35)
        img = img.crop((0, top, img.width, top + nh))
    return img.resize((w, h), Image.LANCZOS)

def save(img: Image.Image, rel: str, fmt="JPEG", quality=88):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    if fmt == "JPEG":
        img.convert("RGB").save(p, "JPEG", quality=quality, optimize=True)
    else:
        img.save(p, fmt)
    return p

def text_center(d, box, s, font, fill):
    x0, y0, x1, y1 = box
    l, t, r, b = d.textbbox((0, 0), s, font=font)
    d.text(((x0 + x1 - (r - l)) / 2 - l, (y0 + y1 - (b - t)) / 2 - t), s, font=font, fill=fill)

# ═══════════════════════ 1. real photos ═══════════════════════
# (relative to --src-root). Verified by eye before being listed here.
HORSE_SOURCES = [
    "img/horse/horse-3.jpg",
    "img/horse/unnamed.png",
    "img/horse/Jan_2025_-_How_Much_Does_a_Horse_Weigh.webp",
    "img/horse/custom_resized_aa61a8d0-f1dd-45de-9761-29b89220f3e6.webp",
    "sources/ov/o00.jpg", "sources/ov/o07.jpg", "sources/ov/o10.jpg", "sources/ov/o12.jpg", "sources/ov/o09.jpg",
    "sources/wm/t00.jpg",
]
TOURNAMENT_SOURCES = [
    "img/tournament/hms-1533175527655610167738.webp",
    "img/tournament/dn-1759142395760710997478.jpeg",
    "img/tournament/duanga-1533202487-6788-1533202852.webp",
    "img/tournament/images (1).jpeg",
    "img/tournament/images.jpeg",
    "sources/wm/c04.jpg", "sources/wm/c08.jpg", "sources/wm/t06.jpg",
    "sources/ov/o01.jpg", "sources/ov/o03.jpg",
]

def _resolve(src_root: Path, rel: str) -> Path:
    """`sources/...` ships with the repo; `img/...` is the folder the user supplied at the root."""
    return (HERE / rel) if rel.startswith("sources/") else (src_root / rel)

def build_photos(src_root: Path):
    made = 0
    for i, rel in enumerate(HORSE_SOURCES, start=1):
        p = _resolve(src_root, rel)
        if not p.exists():
            print(f"  !! missing horse source: {rel}"); continue
        save(cover(Image.open(p), 1200, 900), f"horses/seed-horse-{i:02d}.jpg"); made += 1
    for i, rel in enumerate(TOURNAMENT_SOURCES, start=1):
        p = _resolve(src_root, rel)
        if not p.exists():
            print(f"  !! missing tournament source: {rel}"); continue
        save(cover(Image.open(p), 1200, 600), f"tournaments/seed-tournament-{i:02d}.jpg"); made += 1
    return made

# ═══════════════════════ 2. jockey silks avatars ═══════════════════════
# Real racing silk palettes. Each rider gets a distinct combination so the avatars are
# distinguishable at the 26–64px the UI renders them at.
SILKS = [
    ("#c81e1e", "#ffffff", "stripes"),   ("#0b6bcb", "#ffd400", "quarters"),
    ("#0f7a3d", "#ffffff", "chevron"),   ("#f5a300", "#22223b", "spots"),
    ("#6b21a8", "#ffffff", "sash"),      ("#0e7490", "#f8fafc", "stripes"),
    ("#b91c47", "#facc15", "quarters"),  ("#1e293b", "#f97316", "chevron"),
    ("#15803d", "#fde047", "spots"),     ("#7c2d12", "#fed7aa", "sash"),
]

def silk_avatar(size, base, accent, pattern, initials=None, ring="#0f172a"):
    S = size * 4                                     # supersample for clean edges
    im = Image.new("RGB", (S, S), base)
    d = ImageDraw.Draw(im)
    if pattern == "stripes":
        for x in range(0, S, S // 7):
            d.rectangle([x, 0, x + S // 14, S], fill=accent)
    elif pattern == "quarters":
        d.rectangle([0, 0, S // 2, S // 2], fill=accent)
        d.rectangle([S // 2, S // 2, S, S], fill=accent)
    elif pattern == "chevron":
        for k in range(-S, S, S // 5):
            d.polygon([(k, S), (k + S // 10, S), (k + S // 2 + S // 10, 0), (k + S // 2, 0)], fill=accent)
    elif pattern == "spots":
        r = S // 14
        for gy in range(4):
            for gx in range(4):
                cx, cy = S * (gx + .5) / 4, S * (gy + .5) / 4
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=accent)
    elif pattern == "sash":
        d.polygon([(0, S), (0, int(S * .62)), (int(S * .38), 0), (S, 0),
                   (S, int(S * .38)), (int(S * .62), S)], fill=accent)
    # jockey cap across the top third — reads as racing kit even at 26px
    cap = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cap)
    cd.pieslice([S * .16, S * .10, S * .84, S * .78], 180, 360, fill=(11, 18, 32, 70))
    im = Image.alpha_composite(im.convert("RGBA"), cap).convert("RGB")
    # circular crop + rim
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, S, S], fill=255)
    out = Image.new("RGB", (S, S), "#ffffff")
    out.paste(im, (0, 0), mask)
    ImageDraw.Draw(out).ellipse([2, 2, S - 3, S - 3], outline=ring, width=max(3, S // 90))
    return out.resize((size, size), Image.LANCZOS)

# Names mirror seed_demo.sql so an avatar's initials match the account it lands on.
MEMBER_NAMES = ["Lý Tuấn Kiệt", "Trịnh Gia Huy", "Cao Minh Nhật", "Đỗ Thành Đạt", "Hồ Anh Khoa",
                "Đinh Bá Lộc", "Lâm Chí Dũng", "Tô Hoàng Sơn", "Phan Công Lý", "Mai Chính Trực"]
APPLICANT_NAMES = ["Nguyễn Văn Hùng", "Trần Thị Mai", "Phạm Quốc Bảo", "Võ Thành Nam", "Hoàng Gia Bảo",
                   "Bùi Khánh Linh", "Đặng Minh Chủ", "Vũ Thanh Trại", "Ngô Kim Long", "Lý Tuấn Kiệt"]

def _initials(name):
    parts = [w for w in name.split() if w]
    if not parts:
        return "?"
    return (parts[0][0] + parts[-1][0]).upper()

def build_avatars():
    for i, nm in enumerate(MEMBER_NAMES, start=1):
        b, a, p = SILKS[(i - 1) % len(SILKS)]
        save(silk_avatar(400, b, a, p, _initials(nm)), f"avatars/seed-avatar-{i:02d}.jpg")
    for i, nm in enumerate(APPLICANT_NAMES, start=1):
        b, a, p = SILKS[(i + 4) % len(SILKS)]          # offset palette: applicants read differently
        save(silk_avatar(400, b, a, p, _initials(nm), ring="#475569"),
             f"applications/seed-applicant-{i:02d}.jpg")
    return len(MEMBER_NAMES) + len(APPLICANT_NAMES)

# ═══════════════════════ 3. documents ═══════════════════════
INK, MUTED, RULE, BRAND = "#0f172a", "#64748b", "#cbd5e1", "#155e3f"

def _doc_base(w, h, title, subtitle):
    im = Image.new("RGB", (w, h), "#ffffff")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, int(h * .13)], fill=BRAND)
    d.text((int(w * .05), int(h * .035)), title, font=bold(int(h * .045)), fill="#ffffff")
    d.text((int(w * .05), int(h * .085)), subtitle, font=regular(int(h * .028)), fill="#d1fae5")
    d.rectangle([int(w * .04), int(h * .17), w - int(w * .04), h - int(h * .06)],
                outline=RULE, width=2)
    return im, d

def _fields(d, x, y, w, rows, lh, fs):
    for k, v in rows:
        d.text((x, y), k, font=regular(fs), fill=MUTED)
        d.text((x + int(w * .42), y), v, font=bold(fs), fill=INK)
        d.line([x, y + lh - 8, x + w, y + lh - 8], fill=RULE, width=1)
        y += lh
    return y

def _stamp(d, cx, cy, r, line1, line2):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=BRAND, width=4)
    d.ellipse([cx - r + 9, cy - r + 9, cx + r - 9, cy + r - 9], outline=BRAND, width=1)
    text_center(d, (cx - r, cy - r // 2, cx + r, cy), line1, bold(int(r * .30)), BRAND)
    text_center(d, (cx - r, cy, cx + r, cy + r // 2), line2, regular(int(r * .22)), BRAND)

def build_documents():
    n = 0
    # jockey licences — landscape ID card
    for i in range(1, 6):
        W, H = 1200, 750
        im, d = _doc_base(W, H, "GIẤY PHÉP HÀNH NGHỀ NÀI", "Hiệp hội Đua ngựa Việt Nam")
        d.rectangle([int(W * .07), int(H * .26), int(W * .30), int(H * .70)], outline=RULE, width=2)
        text_center(d, (int(W * .07), int(H * .26), int(W * .30), int(H * .70)),
                    "ẢNH", regular(int(H * .05)), "#94a3b8")
        _fields(d, int(W * .35), int(H * .28), int(W * .56), [
            ("Số giấy phép", f"JK-20{20 + i}-{i:04d}"),
            ("Hạng", ["A", "A", "B", "B", "C"][i - 1]),
            ("Ngày cấp", f"0{i}/03/2024"),
            ("Có hiệu lực đến", f"0{i}/03/2027"),
            ("Nơi cấp", "Trường đua Phú Thọ"),
        ], int(H * .085), int(H * .038))
        _stamp(d, int(W * .82), int(H * .78), int(H * .13), "ĐÃ CẤP", "HĐVN")
        save(im, f"licenses/seed-license-{i}.jpg"); n += 1

    # fitness certificates — portrait A4-ish
    for i in range(1, 6):
        W, H = 900, 1150
        im, d = _doc_base(W, H, "CHỨNG NHẬN THỂ LỰC", "Trung tâm Y học Thể thao")
        y = _fields(d, int(W * .09), int(H * .24), int(W * .82), [
            ("Số hồ sơ", f"FIT-2026-{i:03d}"),
            ("Đối tượng", "Nài đua chuyên nghiệp"),
            ("Chiều cao", f"{158 + i * 2} cm"),
            ("Cân nặng", f"{49 + i} kg"),
            ("Huyết áp", "118/76 mmHg"),
            ("Thị lực", "10/10"),
            ("Kết luận", "ĐỦ ĐIỀU KIỆN"),
        ], int(H * .062), int(H * .028))
        d.text((int(W * .09), y + int(H * .04)),
               "Chứng nhận có giá trị 12 tháng kể từ ngày ký.",
               font=regular(int(H * .024)), fill=MUTED)
        d.line([int(W * .55), int(H * .88), int(W * .91), int(H * .88)], fill=INK, width=2)
        text_center(d, (int(W * .55), int(H * .88), int(W * .91), int(H * .93)),
                    "Bác sĩ phụ trách", regular(int(H * .022)), MUTED)
        _stamp(d, int(W * .27), int(H * .855), int(H * .075), "ĐẠT", "TTYT")
        save(im, f"certificates/seed-certificate-{i}.jpg"); n += 1

    # horse medical records — portrait
    kinds = [("PHIẾU TIÊM PHÒNG", "Vaccine cúm ngựa (EI)"), ("PHIẾU KHÁM SỨC KHOẺ", "Khám định kỳ"),
             ("CHỨNG NHẬN Y TẾ", "Đủ điều kiện thi đấu"), ("PHIẾU TIÊM PHÒNG", "Vaccine uốn ván"),
             ("BIÊN BẢN CHẤN THƯƠNG", "Theo dõi phục hồi"), ("PHIẾU KHÁM SỨC KHOẺ", "Kiểm tra trước giải"),
             ("CHỨNG NHẬN COGGINS", "Xét nghiệm EIA âm tính"), ("PHIẾU TIÊM PHÒNG", "Nhắc lại mũi 2"),
             ("CHỨNG NHẬN KIỂM DỊCH", "Sau vận chuyển"), ("PHIẾU KHÁM SỨC KHOẺ", "Tổng quát")]
    for i, (title, sub) in enumerate(kinds, start=1):
        W, H = 900, 1150
        im, d = _doc_base(W, H, title, f"Thú y Trường đua — {sub}")
        _fields(d, int(W * .09), int(H * .24), int(W * .82), [
            ("Mã hồ sơ", f"MED-2026-{i:03d}"),
            ("Mã ngựa", f"HRS{i:04d}"),
            ("Ngày thực hiện", f"{(i % 28) + 1:02d}/0{(i % 9) + 1}/2026"),
            ("Thân nhiệt", "37.6 °C"),
            ("Nhịp tim", f"{34 + i} bpm"),
            ("Bác sĩ", "BS. Trần Thú Y"),
            ("Kết luận", "BÌNH THƯỜNG"),
        ], int(H * .062), int(H * .028))
        _stamp(d, int(W * .72), int(H * .80), int(H * .085), "XÁC NHẬN", "THÚ Y")
        save(im, f"medical/seed-medical-{i:02d}.jpg"); n += 1
    return n

# ═══════════════════════ 4. photo finish ═══════════════════════
def build_photofinish():
    """Finish-line strip. Silhouettes are LIGHT on a dark track — drawn dark-on-dark first time
    round, they were invisible."""
    W, H = 1200, 500
    for i in range(1, 11):
        im = Image.new("RGB", (W, H), "#0b1220")
        d = ImageDraw.Draw(im)
        for y in range(0, H, 4):                      # strip-camera scan lines
            d.line([0, y, W, y], fill="#0e1729")
        d.rectangle([0, int(H * .80), W, H], fill="#16233d")     # track apron
        ground = int(H * .80)
        n = 3 + (i % 3)
        for k in range(n):
            shade = ["#cbd5e1", "#94a3b8", "#e2e8f0", "#7c8da5"][k % 4]
            off = int(W * .30) + k * 150 - (i % 4) * 18
            top = int(H * .34) + (k % 2) * 22
            d.ellipse([off, top, off + 170, top + 96], fill=shade)                 # body
            d.polygon([(off + 150, top + 20), (off + 214, top - 32),
                       (off + 232, top - 6), (off + 172, top + 52)], fill=shade)   # neck+head
            d.polygon([(off + 214, top - 32), (off + 222, top - 52),
                       (off + 228, top - 30)], fill=shade)                          # ear
            for lx, sw in ((34, -16), (72, 10), (116, -8), (150, 16)):             # legs
                d.line([off + lx, top + 88, off + lx + sw, ground], fill=shade, width=11)
            d.polygon([(off - 6, top + 18), (off - 54, top + 4),
                       (off - 42, top + 54)], fill=shade)                           # tail
            d.ellipse([off + 74, top - 46, off + 126, top + 2], fill="#334155")     # rider
            d.ellipse([off + 88, top - 74, off + 126, top - 40], fill="#475569")    # helmet
            d.text((off + 60, top + 34), str(k + 1), font=bold(30), fill="#0b1220")
        d.line([int(W * .70), 0, int(W * .70), H], fill="#ef4444", width=4)
        d.text((int(W * .71), 16), "FINISH", font=bold(28), fill="#ef4444")
        d.text((20, 16), f"RACE {i:02d}  \u2022  PHOTO FINISH", font=bold(28), fill="#e2e8f0")
        d.text((20, H - 46), f"0{1 + i % 2}:{(20 + i) % 60:02d}.{(37 * i) % 100:02d}",
               font=bold(32), fill="#38bdf8")
        save(im, f"photofinish/seed-photofinish-{i:02d}.jpg")
    return 10


# ═══════════════════════ 5. local attachment files ═══════════════════════
# seed_demo.sql references uploads/attachments/seed-dossier-NNN.pdf and
# uploads/restricted/seed-jockey-license-0N.* — but nothing ever created those files, so every
# document download in seeded data failed. These stay on LOCAL DISK and never reach the CDN:
# AttachmentServiceImpl pins @Qualifier(localFileStorage) so RESTRICTED papers are auth-gated.
import re as _re

def build_attachments(seed_sql: Path, uploads: Path):
    if not seed_sql.exists():
        print(f"  !! seed not found: {seed_sql}"); return 0
    keys = sorted(set(_re.findall(r"'((?:attachments|restricted)/[^']+)'", seed_sql.read_text(encoding="utf-8"))))
    made = 0
    for k in keys:
        dest = uploads / k
        dest.parent.mkdir(parents=True, exist_ok=True)
        stem = dest.stem
        if dest.suffix.lower() == ".pdf":
            W, H = 900, 1160                      # A4-ish portrait
            im, d = _doc_base(W, H, "HỒ SƠ ĐĂNG KÝ THI ĐẤU", "Hồ sơ ngựa & giấy tờ kèm theo")
            _fields(d, int(W * .09), int(H * .24), int(W * .82), [
                ("Mã hồ sơ", stem.replace("seed-dossier-", "DOSSIER-")),
                ("Loại tài liệu", "Giấy chứng nhận sức khoẻ"),
                ("Cơ quan cấp", "Thú y Trường đua"),
                ("Ngày nộp", "2026"),
                ("Trạng thái", "ĐÃ TIẾP NHẬN"),
            ], int(H * .062), int(H * .028))
            _stamp(d, int(W * .72), int(H * .80), int(H * .085), "TIẾP NHẬN", "BTC")
            im.convert("RGB").save(dest, "PDF", resolution=150)
        elif dest.suffix.lower() == ".png":
            W, H = 1200, 750
            im, d = _doc_base(W, H, "GIẤY PHÉP HÀNH NGHỀ NÀI", "Bản lưu nội bộ — RESTRICTED")
            _fields(d, int(W * .09), int(H * .30), int(W * .82), [
                ("Mã lưu trữ", stem.upper()),
                ("Mức nhạy cảm", "RESTRICTED"),
                ("Lưu tại", "disk nội bộ, không lên CDN"),
            ], int(H * .095), int(H * .038))
            im.save(dest, "PNG")
        else:
            dest.write_text(f"{stem}\nTài liệu seed cho môi trường demo.\n", encoding="utf-8")
        made += 1
    return made

# ═══════════════════════ main ═══════════════════════
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-root", default=str(HERE.parents[2]),
                    help="workspace root, for the img/ photos you supplied (default: repo parent)")
    a = ap.parse_args()
    root = Path(a.src_root)
    print(f"source root : {root}")
    print(f"output      : {OUT}")
    OUT.mkdir(parents=True, exist_ok=True)
    p = build_photos(root)
    av = build_avatars()
    dc = build_documents()
    pf = build_photofinish()
    at = build_attachments(HERE.parents[1] / "src/main/resources/db/seed_demo.sql",
                           HERE.parents[1] / "uploads")
    print(f"\n  real photos     : {p}")
    print(f"  silks avatars   : {av}")
    print(f"  documents       : {dc}")
    print(f"  photo finish    : {pf}")
    print(f"  local documents : {at}  (uploads/, never CDN)")
    total = sum(1 for _ in OUT.rglob("*.jpg"))
    print(f"  TOTAL on disk   : {total}")

if __name__ == "__main__":
    main()
