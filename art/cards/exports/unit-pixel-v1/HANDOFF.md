# Claude: thêm pack này vào game khi được yêu cầu

> **Handoff v1 đã được thay thế.** Đọc [HANDOFF của v2 Axemen](../unit-pixel-v2-axemen/HANDOFF.md).
> Người dùng đã chọn Axemen 4 điểm thay Battering Ram; các chỉ dẫn v1 bên dưới là lịch sử.

## Trạng thái bàn giao

Đây là **pack để dành**, không phải yêu cầu triển khai tự động. Người dùng sẽ giao việc
tích hợp sau. Chỉ bắt đầu sửa game khi có yêu cầu đó. Trước khi sửa, đọc `AGENTS.md`,
`CARD_RENDERING_GUIDE.md` và diff hiện tại. Batch này chỉ tạo file trong
`art/cards/exports/unit-pixel-v1/`; giữ nguyên các thay đổi khác đang có trong working tree.

## Cách ưu tiên: giữ game vẽ bằng Java

1. Mở `preview-poland.png`, `preview-nations.png` để xem đúng hình đã bàn giao. Đọc
   `manifest.json` để lấy chính xác bảy ID, class, nation, điểm và checksum ảnh.
2. Xem `source/UnitCardRenderer.java`: lớp này là bản export độc lập của mẫu Knights trước,
   thêm bốn class Poland. Record `Unit` chỉ mang metadata do exporter lấy từ catalog; nó
   không định nghĩa gameplay. Code vẽ không có I/O.
3. Source trong pack dùng **default package** để dễ compile độc lập. Không copy nguyên xi vào
   `src/main/java` rồi import từ `deckduel.client.ui`: Java không cho packaged class truy cập
   default-package class. Chuyển phần vẽ cần dùng vào package UI, hoặc chuyển/adapt renderer
   có chủ đích; không di chuyển core hay thêm framework.
4. Khuyến nghị gộp/reuse helper hiện có của `PixelCardFace`, tách artwork Unit nếu cần để giữ
   class gọn. Tránh để hai bản glyph/frame/Knights trùng nhau trong runtime. Không chỉnh source
   pack nếu chưa cần; pack phải còn tái xuất được độc lập.
5. Dùng `CardDef` thực tế thay cho metadata snapshot. Mở rộng `PixelCardFace.supports()` cho
   đúng bốn ID Poland mới; giữ ba Knights và hai Spell đã có. Không làm mất `draw-two`/`spy`
   khi lấy code từ pack này, vì renderer độc lập ở đây chỉ chứa Units.
6. Chuyển dispatch của năm class từ `unit(...)` và các methods `pikeman`, `bowman`, `rider`,
   `ram`, `classIcon`, `wheel`. Giữ hai dòng `BATTERING` / `RAM` để vừa khung; không đổi tên card.
7. Giữ `CardFace` cache, shadow, selection và disable overlays. Card mới đi qua cùng
   `FxKit.drawFace`, Spy inspector và animation. Kiểm cả Java2D sampling và JavaFX smoothing.
8. Cập nhật tập ID được hỗ trợ trong test và gallery. Hiện game có năm mẫu pixel (ba Knights
   và hai Spells); sau khi thêm bốn Unit mới từ pack sẽ có **chín** mẫu, nếu không có batch khác.
   Đọc lại source thay vì mặc định count đó còn đúng.
9. Chạy `mvn test`, đóng gói và xuất JavaFX screenshot. Kiểm thật trên hand/row, preview,
   selected/disabled Unit, Spy reveal và animation. Khi xong, cập nhật handoff/verification.

## Nếu người dùng chọn dùng PNG

PNG đã in điểm, nation, class; không đặt thêm text trùng. `native/` giữ lưới gốc; `png-4x/`
cho cùng hình phóng 4×. Cả hai là mặt card 2:3 có alpha ở góc, không có margin shadow.

Loader `CardImages` hiện đọc `.jpg` trong resource runtime. **Chỉ copy PNG vào đó sẽ chưa
được đọc**; đổi đuôi PNG thành `.jpg` cũng không phải tích hợp đúng. Nếu được giao dùng PNG,
thêm đường load PNG/alpha rõ ràng và tests, giữ fallback cho card khác, kiểm ưu tiên pixel
renderer đã có. Không chuyển sang JPEG vì mất alpha và tạo nhiễu quanh pixel.

Ảnh PNG chứa text theo metadata lúc export. Nếu balance/catalog thay đổi phải tái xuất hoặc
chuyển text về runtime. Không sửa điểm gameplay cho khớp ảnh.

## Yêu cầu không đổi

Deck vẫn 35 Units + 10 neutral Spells. Không thêm ability, card mới, Landmark, Sabotage,
deck builder hoặc chỉnh scoring. Không xóa artwork cũ trong task chỉ thêm pack. Giữ privacy:
artwork không được đọc bài ẩn, seed hoặc deck order của match live.

## Prompt để người dùng giao việc sau

> Đọc AGENTS.md, CARD_RENDERING_GUIDE.md và art/cards/exports/unit-pixel-v1/HANDOFF.md.
> Tích hợp các Unit trong pack unit-pixel-v1 bằng renderer Java, giữ Spells pixel hiện có.
> Lấy metadata từ catalog, không sửa luật; xuất screenshot JavaFX cho tôi xem và chạy test.
