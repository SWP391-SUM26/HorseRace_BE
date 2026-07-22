# Nguồn & bản quyền ảnh seed

Ảnh seed được dựng bằng `scripts/seed/build-seed-images.py` rồi đẩy lên Cloudinary (cloud
`qtpgbwsh`) bằng `scripts/seed/upload-cloudinary.sh`.

Trước đây **toàn bộ** ảnh seed được kéo ngẫu nhiên từ picsum.photos — không ảnh nào liên quan tới
đua ngựa, và 3 "video đua trực tiếp" là clip con chó demo của Cloudinary. File này ghi lại nguồn
thật của bộ ảnh thay thế.

---

## 1. Ảnh có license rõ ràng — BẮT BUỘC ghi công

CC BY và CC BY-SA yêu cầu ghi công tác giả. Giữ nguyên phần này khi phát hành.

### Flickr, qua Openverse — CC BY 2.0

| Dùng cho | Tiêu đề gốc | Tác giả | Link gốc |
|---|---|---|---|
| `horses/seed-horse-05` | Overturn Race Horse | Paolo Camera | https://www.flickr.com/photos/81265351@N00/6798026010 |
| `horses/seed-horse-06` | Horse racing | Paolo Camera | https://www.flickr.com/photos/81265351@N00/3309223161 |
| `horses/seed-horse-07` | AP McCoy Black & White Horse Racing Photo | Paolo Camera | https://www.flickr.com/photos/81265351@N00/3910559910 |
| `horses/seed-horse-08` | Horse Racing at Mornington | Jessica M. Cross | https://www.flickr.com/photos/76682361@N00/3169240521 |
| `horses/seed-horse-09` | Horse racing | Paolo Camera | https://www.flickr.com/photos/81265351@N00/3309224777 |
| `tournaments/seed-tournament-09` | Horse Racing at Mornington | Jessica M. Cross | https://www.flickr.com/photos/76682361@N00/3169240519 |
| `tournaments/seed-tournament-10` | Horse racing | Paolo Camera | https://www.flickr.com/photos/81265351@N00/3309217425 |

Giấy phép: https://creativecommons.org/licenses/by/2.0/

### Wikimedia Commons

| Dùng cho | Tiêu đề gốc | Tác giả | License |
|---|---|---|---|
| `horses/seed-horse-10` | Happy Zero 20091213.jpg | (tác giả tự tải lên) | CC0 — miễn ghi công, vẫn ghi cho đủ |
| `tournaments/seed-tournament-06` | Horse racing @ Tokyo Race Course @ Fuchu | Guilhem Vellut | CC BY 2.0 |
| `tournaments/seed-tournament-07` | Horse racing @ Tokyo Race Course @ Fuchu | Guilhem Vellut | CC BY 2.0 |
| `tournaments/seed-tournament-08` | ShaTinRaceTrack.JPG | Elgaard | CC BY-SA 3.0 |

Ảnh gốc tải qua API thumbnail của Wikimedia (đúng cách họ yêu cầu bot dùng, không kéo file gốc).

---

## 2. Ảnh do người dùng cung cấp — LICENSE CHƯA XÁC MINH

Nằm sẵn trong `img/` của workspace và được dùng theo yêu cầu.

| Dùng cho | File gốc | Ghi chú |
|---|---|---|
| `horses/seed-horse-01` | `img/horse/horse-3.jpg` | chưa rõ nguồn |
| `horses/seed-horse-02` | `img/horse/unnamed.png` | chưa rõ nguồn |
| `horses/seed-horse-03` | `img/horse/Jan_2025_-_How_Much_Does_a_Horse_Weigh.webp` | **có watermark** của bên thứ ba |
| `horses/seed-horse-04` | `img/horse/custom_resized_*.webp` | chưa rõ nguồn |
| `tournaments/seed-tournament-01` | `img/tournament/hms-*.webp` | tên file dạng báo điện tử VN |
| `tournaments/seed-tournament-02` | `img/tournament/dn-*.jpeg` | tên file dạng báo điện tử VN |
| `tournaments/seed-tournament-03` | `img/tournament/duanga-*.webp` | tên file dạng báo điện tử VN |
| `tournaments/seed-tournament-04` | `img/tournament/images (1).jpeg` | ảnh cổng xuất phát Saratoga |
| `tournaments/seed-tournament-05` | `img/tournament/images.jpeg` | chưa rõ nguồn |

> **Cảnh báo.** Đây gần như chắc chắn là ảnh báo chí / ảnh thương mại, **không có giấy phép cho
> phép đăng lại**. Dùng trong bài tập trên lớp thì thường không ai truy cứu, nhưng nếu sản phẩm
> được công bố rộng (đưa lên mạng, nộp dự thi, thương mại hoá) thì phải thay.
>
> Thay rất dễ: Openverse (`https://api.openverse.org/v1/images/`) trả về ảnh đua ngựa CC BY, không
> cần API key và không bị chặn tần suất. Chỉ cần đổi đường dẫn trong `HORSE_SOURCES` /
> `TOURNAMENT_SOURCES` của `build-seed-images.py`, chạy lại script rồi upload lại.

`img/jockey/` **cố tình không dùng**: đó là ảnh báo chí chụp nài có thật, nhận diện được mặt (tên
file cho thấy nguồn BBC / Alamy). Gán mặt người có thật cho hồ sơ bịa ("Lý Tuấn Kiệt", "Trịnh Gia
Huy") là xuyên tạc danh tính của họ, chứ không chỉ là vấn đề bản quyền.

---

## 3. Ảnh tự vẽ — không vướng bản quyền

Vẽ bằng Pillow trong `build-seed-images.py`, deterministic, không dùng ảnh của ai:

| Nhóm | Số lượng | Nội dung |
|---|---|---|
| `avatars/`, `applications/` | 20 | Áo đấu nài (mũ + hoa văn sọc/chevron/chấm/chia tư + chữ cái tên). Đây là cách nhận diện nài thật trên trường đua — hợp chủ đề hơn ảnh chân dung stock, và tránh gán mặt người thật vào tài khoản giả. |
| `licenses/`, `certificates/`, `medical/` | 20 | Giấy phép hành nghề, chứng nhận thể lực, phiếu y tế ngựa |
| `photofinish/` | 10 | Dải ảnh về đích có vạch đích + số áo + thời gian |

---

## Dựng lại

```bash
python3 scripts/seed/build-seed-images.py     # -> scripts/seed/build/ (70 ảnh)
bash    scripts/seed/upload-cloudinary.sh     # -> Cloudinary, public_id giữ nguyên
```

`public_id` cố định + `overwrite=true`, nên **URL trong `seed_demo.sql` không bao giờ phải sửa** —
chạy lại chỉ thay bytes. `scripts/seed/build/` không commit (dựng lại được);
`scripts/seed/sources/` thì commit để máy khác dựng lại được y hệt.
