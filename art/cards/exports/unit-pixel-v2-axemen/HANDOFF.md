# Claude: thay Battering Ram bằng Axemen khi tích hợp sau

Người dùng đã chốt **Axemen — 4 điểm** trong cuộc hội thoại ngày 2026-10-21.
Người dùng đồng thời yêu cầu giữ card trong folder để giao Claude thêm vào game sau.
Pack này ghi nhận lựa chọn đã chốt, chưa thực hiện migration gameplay. Đọc `AGENTS.md`,
`CARD_RENDERING_GUIDE.md` và source/diff mới nhất trước khi nhận task tích hợp.

## Đổi catalog đồng bộ, không thêm class thứ sáu

| ID cũ | ID mới | Tên mới | Điểm / class |
| --- | --- | --- | --- |
| `hu-battering-ram` | `hu-axemen` | Hungary Axemen | 4 / Axemen |
| `pl-battering-ram` | `pl-axemen` | Poland Axemen | 4 / Axemen |
| `bo-battering-ram` | `bo-axemen` | Bohemia Axemen | 4 / Axemen |
| `au-battering-ram` | `au-axemen` | Austria Axemen | 4 / Axemen |
| `st-battering-ram` | `st-axemen` | Styria Axemen | 4 / Axemen |
| `cr-battering-ram` | `cr-axemen` | Croatia Axemen | 4 / Axemen |
| `tr-battering-ram` | `tr-axemen` | Transylvania Axemen | 4 / Axemen |

Deck vẫn 35 Units + 10 neutral Spells. Năm class đích: Pikemen 2, Bowmen 3, Riders 4,
Knights 5, Axemen 4. **Hai class cùng điểm vẫn khác class**, pair combo / formation phải dựa
trên tag class, không trên điểm in. Không thêm ability, cost hoặc điều kiện mới cho Axemen.

`catalog-axemen-preview.txt` minh họa đầy đủ dữ liệu đích. Trước khi merge vào catalog live,
so với catalog mới nhất và chỉ áp phần thay thế đã chốt. Chọn `rules_version`/catalog version
production mới đúng với repo hiện tại, không đổi metadata của historical match đã lưu.
Nội dung `AGENTS.md` hiện mô tả implementation trước migration; khi thực sự tích hợp cần
cập nhật nó và docs hiện hành theo yêu cầu mới của người dùng.

## Art và code

- `source/UnitCardRenderer.java` có hình Axemen mới và helper của v1, dùng default package.
  Khi đưa vào game phải adapt sang package `deckduel.client.ui` hoặc lấy các method cần thiết;
  không import default-package class trực tiếp từ packaged class.
- Axemen dùng `axeman(...)`, nhánh `classIcon("Axemen")` và glyph `X` mới. Mặt card in số 4
  từ metadata, áo đỏ Poland, đại bàng, mũ hở mặt, chainmail và rìu hai tay.
- Chỉ Poland Axemen có artwork trong pack. Nếu migration cả bảy nation, vẽ tiếp hoặc dùng
  fallback đúng tên/điểm; không đổi tên ảnh ram cũ thành axemen rồi coi là artwork hoàn chỉnh.
- `supports()` của pack nhận bảy ID asset; không coi snapshot có bảy Axemen nghĩa là đã có
  hình cho cả bảy. Các Knights/Spells pixel của game trước đó phải giữ lại.
- Ưu tiên dùng code render để text/điểm tiếp tục lấy từ `CardDef`. Nếu người dùng chọn PNG,
  loader hiện ưu tiên JPEG nên cần thêm PNG/alpha có test. Giữ nearest-neighbour, không rename
  file `.png` thành `.jpg` hoặc convert JPEG làm nhòe pixel.
- Dùng chung `CardFace` → `FxKit` → `CardTableView`/inspector/animation. Không thêm renderer
  chỉ chạy trong ảnh preview mà không xuất hiện trong client thật.

## Các điểm cần kiểm khi nhận task tích hợp

1. Tìm các tham chiếu Battering Ram trong source, test, catalog và docs hiện hành; phân biệt
   lịch sử verification với hướng dẫn hiện tại. Giữ dữ liệu DB/history cũ.
2. Catalog phải có đúng bảy Axemen, mỗi nation một lá, không còn ram trong deck đích.
3. Unit points của Axemen là 4. Test combo/formation nên có Riders 4 và Axemen 4 để chứng minh
   hệ thống không nhóm nhầm class chỉ vì trùng điểm. Giữ Royal Line là năm class khác nhau.
4. Điều chỉnh test artwork nếu Axemen dùng code/fallback; không yêu cầu JPEG ram cũ cho ID mới.
5. Cập nhật guide/handoff và tập ID pixel tương ứng với artwork thực sự đã tích hợp.
6. Chạy `mvn test`; với migration hoàn chỉnh chạy `mvn clean verify`, xuất JavaFX screenshot
   có Axemen và xác minh các luồng play, replace, disable, Spy, bot/rematch thích hợp.
7. Không claim cả 35 card đã có pixel art khi mới có các mẫu trong pack này.

## Prompt ngắn để giao việc

> Đọc AGENTS.md, CARD_RENDERING_GUIDE.md và art/cards/exports/unit-pixel-v2-axemen/HANDOFF.md.
> Tích hợp lựa chọn đã chốt: thay Battering Ram bằng Axemen 4 điểm cho cả 7 quốc gia;
> dùng artwork/code từ pack v2, giữ các pixel Spells hiện có. Không thêm ability hoặc luật khác.
> Cập nhật catalog/version/tests/docs, xuất screenshot JavaFX và báo phạm vi art còn thiếu.
