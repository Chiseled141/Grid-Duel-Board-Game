# Hướng dẫn agent: vẽ card pixel art bằng Java trong Deck Duel

Tài liệu triển khai, đối chiếu source ngày **2026-10-21**. Đọc `AGENTS.md` trước khi làm;
file đó là nguồn quy tắc chính của project. Hướng dẫn này giải thích cách tiếp tục phong cách
card đã được người dùng xem và đánh giá tốt, không thay thế luật game.

## 1. Mục tiêu và phạm vi

Vẽ card bằng code Java trên một lưới pixel nhỏ, ghép các lớp hình, chữ và huy hiệu; đưa kết quả
qua renderer thật của game rồi xuất ảnh cho người dùng xem. Người dùng đã yêu cầu cách làm
này sau khi xem bản ImageGen và nhận xét rằng hình đó trông quá giống ảnh AI.

- Dùng Java2D để tạo sprite runtime; JavaFX hiển thị sprite trên bàn game. Với Spell và mặt lưng,
  soạn bằng claude-pxl rồi xuất Java theo `art/cards/pxl/README.md`; không sửa tay `PxlCardArt.java`.
- Tiếp tục phong cách pixel rõ khối, ít màu, bố cục dễ đọc như các mẫu hiện có.
- Không dùng ImageGen, ảnh AI thu nhỏ hoặc ảnh AI phủ bộ lọc pixel để thay công việc vẽ bằng code.
- Tạo hình, palette và huy hiệu nguyên bản. Các tool Balatro là nguồn tham khảo về kỹ thuật.
- Chỉ thêm hình cho card đã có trong catalog và nằm trong phạm vi task hiện tại.
- Mở rộng toàn bộ thư viện là công việc tương lai; file này không tự yêu cầu làm ngay 45 lá.

### Trạng thái đang chạy

Cập nhật claude-pxl (2026-10-21): **35 Units + 10 Spells = 45 mặt card**, cùng mặt lưng,
được vẽ từ code Java. Giữ nguyên 35 hình Unit đã chọn. Mười Spell và mặt lưng được soạn bằng
claude-pxl tại `art/cards/pxl/spells.px.js`, xuất thành `PxlCardArt.java` bằng
`node tools/export-pxl-cards.mjs`; không sửa tay file Java được sinh. `PixelSpellArt` ghép tên
và caption từ `CardDef`; tên dài dùng chữ bitmap 3×5 để không tràn viền. Không cần Node hay đọc
PNG trong runtime. Xem `art/cards/pxl/README.md` và `docs/verification/2026-10-21-pxl-spells/`.
JPEG Spell cũ vẫn giữ làm fallback cho card không thuộc thiết kế đã hỗ trợ.

- `PixelCardFace.supports()` kiểm ID prefix/suffix khớp nation/class từ `CardDef`.
- Năm silhouette: Pikemen, Bowmen, Riders, Knights, Axemen. Mỗi silhouette dùng đủ bảy
  palette và bảy crest riêng. Các crest là diễn họa game, không phải phục dựng lịch sử.
- Áo, nền khiên, cờ và dấu trang trí dùng cùng palette nation. Không hard-code khiên đỏ
  hoặc crest Poland trong methods của các class khác.
- Axemen và Riders đều 4 điểm nhưng là hai class khác nhau. Mọi điểm/label lấy từ catalog.
- Nation tối đa bảy glyph nằm giữa điểm và crest; Transylvania chia `TRANSYL` / `VANIA`.
- Packs `unit-pixel-v1` / `unit-pixel-v2-axemen` là snapshot lịch sử. Renderer hiện hành trong
  `src/main/java/deckduel/client/ui/PixelCardFace.java` là source để tiếp tục.

Ảnh mới được xuất qua renderer JavaFX thật tại
`docs/verification/2026-10-21-all-unit-pixel/`: `nations-preview.png`, `axemen-preview.png`,
`all-units.png`, `match-table.png`. PNG chỉ là bằng chứng xem trước, không phải input game.

Bowmen/Riders được làm lại sau đó để rõ hơn khi card nhỏ: Bowmen có tư thế kéo cung,
dây chéo và đầu tên lớn; Riders có thân ngựa nằm ngang, chân và người cưỡi tách rõ.
Mẫu hiện hành và so sánh trước/sau ở `docs/verification/2026-10-21-bowmen-riders/`.
Giữ khoảng trống phân cách các hình chính; xem cả native và cỡ card 104×156 khi chỉnh tiếp.

Bowmen được sửa tiếp theo phản hồi người dùng: **tay áo kín tới cổ tay, găng rõ**, chỉ mặt
hiện màu da; mũi tên là nét một pixel với đầu nhỏ, không dùng thanh ngang dày/đầu kim loại lớn
dễ bị nhìn thành dụng cụ. Ảnh của lần sửa đó: `docs/verification/2026-10-21-bowmen-clothed/`.

**Bản được duyệt sau cùng (2026-10-21):** người dùng chọn Pikemen, Bowmen và Riders từ
`art/cards/drafts/knight-style-v1/`, áp dụng đủ bảy nation. Pikemen dùng mũ vành rộng,
giáp lớn và khiên cao; Bowmen cầm cung đứng, có hood, bao tên và tay áo kín; Riders là
góc cận người cưỡi cùng đầu ngựa. **Giữ nguyên Knights và Axemen cũ.** Axemen cầm rìu
chéo trong folder draft không được chọn. Bộ cuối ở
`docs/verification/2026-10-21-approved-unit-art/`; các ảnh trước đó là lịch sử.

Viền Unit được tô thêm theo nation ở `PixelCardFace.frame()`: màu chính bao quanh nền
kem, cạnh trên/trái sáng và cạnh dưới/phải tối, hai vạch phân cách cùng màu nation.
Tái sử dụng palette áo/khiên để không tăng số màu; hình nhân vật và nền minh họa giữ
nguyên. Đủ 35 Units dùng chung viền này, Spells giữ thiết kế trung lập. Ảnh hiện tại:
`docs/verification/2026-10-21-nation-card-borders/`.

Viền đã tăng tiếp từ 3 lên 4 pixel màu trên lưới native theo yêu cầu người dùng;
crest góc phải dịch trái 1 pixel để còn nằm trọn trên nền kem. Hình nhân vật giữ nguyên.
Ảnh bản viền dày: `docs/verification/2026-10-21-thicker-nation-borders/`.

Axemen được sửa riêng hai tay sau đó: tay áo kín theo nation, giáp cổ tay và găng kim
loại; khuỷu tay gập và hai găng nắm cán rìu ở hai độ cao tách biệt. Không dùng màu gỗ/da
để tô cẳng tay. Giữ đầu, lưỡi/cán rìu, thân và viền 4 pixel đã chọn. Bản hiện tại:
`docs/verification/2026-10-21-axemen-arms/`.

**Hand-pixel v2 (thay hình nhân vật, tích hợp 2026-10-21):** người dùng thấy các hình
polygon trông "giống AI" (cạnh chéo răng cưa, mặt là khối màu gỗ không mắt, xám đục, nền
chấm). Từ bản này mỗi nhân vật là **bản đồ ký tự 30×38 ô vẽ tay, mỗi ô = 2×2 pixel native**,
đặt tại `SPRITE_X/SPRITE_Y = (10, 26)`; viền tối 1 ô, sáng từ trên trái, 2–3 sắc độ mỗi chất
liệu, da `#E8B48A`/`#B97A57` có mắt. Ký tự: `O` viền, `s/S` da, `m/M/n` thép, `c/C/d` vải
nation, `w/W` gỗ, `g` vàng, `.` để lộ nền giấy. Huy hiệu nation màu giấy trên khiên Knights
và ngực Pikemen/Bowmen (`badge()`); bóng phẳng dưới chân (`shadow()`). Bỏ class icon
dưới số điểm và nền chấm. Khung viền 4px, header, crest góc và nhãn class giữ nguyên.
Bản nháp và so sánh trước/sau: `art/cards/drafts/hand-pixel-v2/`; khi sửa hình, sửa bản đồ
ký tự thay vì thêm polygon.

**Axemen (sửa sau phản hồi):** bản rìu hai tay ở lưới 2×2 bị chê "lol" — nắm tay chỉ 2–3 ô nên
thành thanh ngang hoặc lẫn vào cán. Theo nguồn tham khảo (rìu Dane hai tay không dùng cùng
khiên; lính rìu phổ biến dùng rìu một tay + khiên tròn), Axemen giờ giơ **rìu râu một tay**
(lưỡi xoè ra ngoài, mũi chúc xuống) và cầm **khiên tròn** màu nation có huy hiệu ở giữa.
Không vẽ lại cách cầm hai tay ở độ phân giải này.

## 2. Đọc đúng source trước khi sửa

Các đường dẫn dưới đây tính từ root repository.

| File | Trách nhiệm |
| --- | --- |
| `AGENTS.md` | Luật, giới hạn kiến trúc, yêu cầu test và nội dung đã loại bỏ |
| `src/main/resources/cards/catalog.txt` | ID, tên, điểm, nation, class và effect thật |
| `src/main/java/deckduel/core/card/CardDef.java` | Hợp đồng dữ liệu card |
| `src/main/java/deckduel/core/card/Effect.java` | Kiểu effect và mô tả effect |
| `src/main/java/deckduel/client/ui/PixelCardFace.java` | Chọn card được hỗ trợ, vẽ Knights, frame, crest, chữ bitmap |
| `src/main/java/deckduel/client/ui/PixelSpellArt.java` | Nhận diện 10 Spell, ghép pxl art và nhãn từ catalog |
| `src/main/java/deckduel/client/ui/CardFace.java` | Chọn pixel/art/fallback, cache sprite theo kích thước, shadow và disable overlay |
| `src/main/java/deckduel/client/ui/PxlCardArt.java` | Dữ liệu Java sinh từ pxl: 10 Spell và mặt lưng, không I/O runtime |
| `src/main/java/deckduel/client/ui/CardImages.java` | Tải JPEG fallback bằng worker; catalog hiện hành dùng code art |
| `src/main/java/deckduel/client/ui/FxKit.java` | Chuyển Java2D image sang JavaFX và vẽ card với sampling phù hợp |
| `src/main/java/deckduel/client/ui/CardTableView.java` | Hand, Unit rows, hover preview và các mặt card trên canvas |
| `src/main/java/deckduel/client/ui/CardDetails.java` | Inspector chỉ đọc cho card đã được Spy reveal |
| `src/main/java/deckduel/client/ui/TableEffects.java` | ImageView của card khi bay, lật, vỡ và chạy hiệu ứng |
| `tools/RenderPixelCard.java` | Export native sprite, ảnh phóng lớn, gallery và bàn game mẫu |
| `src/test/java/deckduel/client/ui/PixelCardFaceTest.java` | Palette, alpha, scaling, cache và phạm vi hỗ trợ |
| `docs/verification/2026-10-21-pixel-card.md` | Bằng chứng kiểm tra và nguồn tham khảo đã đọc |

Trước khi sửa, chạy `git status --short` và xem diff liên quan. Working tree có thể có chỉnh
sửa của người dùng hoặc task khác; giữ nguyên các chỉnh sửa đó. Không reset cả file để áp patch.

## 3. Dữ liệu game phải đúng trước khi hình đẹp

Catalog hiện tại là `cards-neutral-4`: **35 Units + 10 neutral Spells**, một bản mỗi lá trong
deck 45 card. Đọc lại catalog khi làm task; không dùng bảng ghi nhớ để ghi đè dữ liệu mới hơn.

| Class | Điểm in ban đầu | ID suffix |
| --- | ---: | --- |
| Pikemen | 2 | `pikemen` |
| Bowmen | 3 | `bowmen` |
| Riders | 4 | `riders` |
| Knights | 5 | `knight` |
| Axemen | 4 | `axemen` |

Axemen thay Battering Ram từ 2026-10-21. Riders và Axemen cùng 4 điểm nhưng khác class.

Nation prefixes: `hu` Hungary, `pl` Poland, `bo` Bohemia, `au` Austria, `st` Styria,
`cr` Croatia, `tr` Transylvania. Mỗi Unit chỉ có một nation và một class.

Spell IDs đang có: `disable-tags`, `disable-points`, `destroy-unit`, `random-discard`,
`random-steal`, `draw-two`, `exchange`, `reconnaissance`, `spy`, `clear-disable`.

- Dùng `CardDef.points()`, `nation()`, `unitClass()`, `name()` và `effect()` để lấy nội dung.
- Chỉ gọi `nation()`/`unitClass()` trên Unit; Spell không có nation/class.
- Spell không in điểm Unit, giá mua, mana, rarity hay cờ quốc gia.
- Số lá rút/reveal lấy từ record effect. Renderer kiểm đúng ID, effect, target và count của cả 10 Spell trước khi dùng art; effect mới phải có thiết kế phù hợp.
- Mô tả rút gọn trên mặt card phải giữ đúng hướng và ý nghĩa effect. Tooltip/caption đầy đủ
  dùng `CardDef.text()` hoặc `Effect.text()`.
- Hình Spy chỉ vẽ biểu tượng ba mặt lưng; renderer không đọc bài ẩn để minh họa.
- Không thêm ability vào Unit, sửa balance, phát minh Spell thứ 11 hay giải quyết các luật còn mở.
- Không khôi phục Landmarks, Sabotage, Chaplain, Envoy, deck builder hoặc các phần đã bị bỏ.

## 4. Luồng render hiện tại

```text
CardDef / dữ liệu recipient được phép thấy
    → PixelCardFace.supports(card)
        ├─ true: PixelCardFace.render(card)
        │           ├─ Unit: frame + backdrop + cape + armour + helmet + sword + shield + text
        │           └─ Spell: PixelSpellArt.paint(g, card)
        └─ false: ảnh CardImages hoặc procedural fallback hiện có
    → CardFace.sprite(...): kích thước hiển thị + device scale + shadow + overlay
    → FxKit.face(...): convert và cache ảnh JavaFX
    → FxKit.drawFace(...): Canvas của bàn game
      hoặc ImageView của inspector / animation
```

`PixelCardFace` tạo `BufferedImage`; đây là logic trình bày trong `client/ui`, không phải core.
Core tiếp tục thuần logic. Không đưa AWT, JavaFX, file I/O hay asset loading vào `core/card`.
Không tạo renderer khác chỉ dùng cho screenshot mà bỏ qua đường đi này.

Sprite native hiện được tạo khi cache cần render một biến thể mới. Frame thường dùng lại
sprite đã cache; không vẽ lại từng polygon trong mỗi pulse JavaFX. Khi mở rộng nhiều card,
giữ cơ chế này và đo trước khi thêm cache hoặc tối ưu khác.

## 5. Quy cách pixel và bố cục

### Lưới và bảng màu

- Native: **80 × 120**, tỉ lệ **2:3**, `BufferedImage.TYPE_INT_ARGB`.
- Vẽ ở tọa độ nguyên trên lưới native. Dùng `fillRect` và polygon với đỉnh nguyên.
- Tắt antialiasing khi tạo native art. Alpha native chỉ là 0 hoặc 255.
- Giữ tối đa **16 màu tính cả transparent** theo giới hạn test hiện tại.
- Tạo khối sáng/tối bằng vài màu phẳng; tránh gradient, blur và nhiễu ngẫu nhiên.
- Các góc card có transparency; nội thất card kín, không thủng.
- Shadow, selection glow và disable shade là lớp UI sau native art; chúng có thể có alpha mềm.

Palette nền Unit hiện tại:

| Vai trò | Màu |
| --- | --- |
| Ink | `#232B3C` |
| Paper | `#F4E9CE` |
| Edge | `#CBBE9E` |
| Highlight | `#FFF6DF` |
| Steel / light / shade | `#8DABAE` / `#C8D8CC` / `#506777` |
| Gold | `#D7A95B` |
| Poland cloth | `#C54846` / `#EC7460` / `#793949` |
| Hungary cloth | `#397E69` / `#75B787` / `#28514C` |
| Austria cloth | `#4D5065` / `#87859A` / `#343345` |
| Bohemia cloth | `#80538C` / `#B787B8` / `#513954` |
| Styria cloth | `#637C3C` / `#A1BB62` / `#3A512E` |
| Croatia cloth | `#426C9D` / `#83B4CC` / `#31455E` |
| Transylvania cloth | `#B27B35` / `#E0B45E` / `#715033` |

Không tăng palette chỉ vì bảng này liệt kê nhiều màu: một sprite chỉ dùng tập con cần thiết.
Spell có palette riêng trong `PixelSpellArt`.

### Vùng bố cục Unit đang dùng

| Thành phần | Vùng / tọa độ native |
| --- | --- |
| Điểm | `(7, 8)`, chữ 5×7 phóng 2× |
| Nation | `y=11`, căn để vừa giữa điểm và crest |
| Crest góc | Gần `(66, 9)` |
| Nhân vật | Bản đồ 30×38 ô × 2px từ `(10, 26)`, tức `x=10..70`, `y=26..102` |
| Class label | `y=106`, Knights bắt đầu tại `x=19` |

Đây là bố cục Unit hiện tại. `nationLabel()` đã chia Transylvania thành hai dòng giữa điểm
và crest. Với tên mới dài hơn, đo độ rộng, chia dòng hoặc điều chỉnh layout có chủ đích. Không
để chữ đè crest, thu nhỏ đến không đọc được, hoặc đổi tên catalog để vừa khung.

### Chữ bitmap

`GLYPHS` trong `PixelCardFace` chứa các ký tự đang cần, mỗi glyph là bảy hàng, mỗi hàng năm bit;
`/` ngăn các hàng. `1` vẽ một ô, `0` bỏ qua. Advance hiện là 6 pixel nhân scale.

```text
A = 01110/10001/10001/11111/10001/10001/10001
width(text) = (text.length() * 6 - 1) * scale  // chuỗi không rỗng, font hiện tại
```

Trước khi thêm tên/label mới, kiểm tra **mọi ký tự** đã có trong `GLYPHS`, kể cả dấu cách,
chữ số và dấu câu. Hàm `text()` hiện truy cập trực tiếp glyph và sẽ lỗi nếu thiếu. Không giả
định đã có cả alphabet. Mở rộng glyph tối thiểu cần thiết, giữ cùng độ dày và chiều cao.

## 6. Quy trình vẽ thêm Unit

1. Chốt ID trong task và lấy `CardDef` từ catalog thật. Xác nhận class, nation và điểm.
2. Nếu cùng class Knights, tái sử dụng silhouette/layer hiện tại, thêm palette và crest nation.
   Không sao chép toàn bộ class chỉ để thay ba màu.
3. Với class mới, xây silhouette nhận ra được ở 1×: ví dụ pike cho Pikemen, bow cho Bowmen,
   rider/horse cho Riders, rìu hai tay cho Axemen. Đây là hướng mỹ thuật, không thêm effect.
4. Vẽ theo lớp: nền → áo/phần sau → thân/thiết bị → đầu/phần trước → vũ khí → crest → chữ.
5. Tách method theo thành phần dễ hiểu; tham số hóa palette/crest nơi cần. Nếu renderer lớn
   lên vì nhiều class, tách lớp trình bày có trách nhiệm rõ ràng; không thêm framework/registry
   hoặc inheritance chỉ để làm hệ thống phức tạp.
6. Mở rộng `supports()` bằng ID và metadata đúng. Chỉ nhận các thiết kế thực sự đã vẽ xong.
   Hàm phải an toàn với `null`, vì mặt lưng/animation có thể không có `CardDef`.
7. Kiểm tra chữ, overlap, palette và alpha. Thêm card vào gallery và danh sách coverage.
8. Xuất ảnh bằng renderer thật, xem 1×, 3×/4× và kích thước trong bàn game.

Ví dụ cấu trúc lớp vẽ, dùng helper hiện có; đây là minh họa, không phải định nghĩa card mới:

```java
private static void paintClassArt(Graphics2D g, CardDef card) {
    // Các tọa độ đều thuộc canvas native 80×120.
    rect(g, new Color(0x232B3C), 30, 40, 12, 18);
    polygon(g, new Color(0xC8D8CC), 31,40, 40,40, 44,47, 31,47);
    text(g, Integer.toString(card.points()), 7, 8, 2, new Color(0x793949));
}
```

## 7. Quy trình vẽ thêm Spell

1. Đọc tên, kiểu effect, target, count và duration từ catalog, `Effect` và `AGENTS.md`.
2. Chọn một biểu tượng rõ cho effect đó, vẽ trong `art/cards/pxl/spells.px.js`. Giữ frame Spell
   tối để phân biệt nhanh với Unit nền kem. Bố cục: tên ở y 8, cửa sổ tranh y 17–91, hai dòng
   caption ở y 96 và y 103, tránh hoa văn góc (y 5–7 và 112–114). Ngôn ngữ hình chung: ổ khóa
   đóng = bị disable, ổ khóa mở = gỡ disable; lá úp dùng part `_minicard`/`_bigcard`.
3. Thêm nhận diện vào `PixelSpellArt.supports()` bằng đúng ID và effect shape cần hỗ trợ.
   `PixelCardFace.supports()` đã ủy quyền nhận diện Spell sang lớp này.
4. Thêm phương thức vẽ và nhánh dispatch trong `paint()`. Lấy count từ record effect như mẫu
   `Effect.Draw` và `Effect.Spy`; không để hình/label nói khác effect thật.
5. Dùng mô tả ngắn đủ đọc trên card; giữ mô tả đầy đủ ở caption/inspector. Đừng tạo rule text
   mới chỉ vì có chỗ trống hoặc tự giải quyết edge case chưa chốt.
6. Spell không dùng disable trạng thái của Unit trong gameplay. Test có thể kiểm tra khả năng
   compose overlay, nhưng ảnh minh họa gameplay phải dùng trạng thái hợp lệ.
7. Thêm mẫu vào Spell gallery rồi kiểm tra với full effect caption lấy từ `card.text()`.

## 8. Nối vào game và giữ nét pixel

### Thứ tự ưu tiên và cache

Nhánh pixel trong `CardFace.renderFace()` phải chạy **trước** `CardImages.face(id)`. JPEG cũ
của năm card mẫu vẫn tồn tại; ảnh load xong không được đổi một card pixel trở lại ảnh JPEG.

`CardFace.sprite()` hiện bỏ qua trạng thái ảnh JPEG cho card được hỗ trợ, và thêm marker
pixel + điểm vào cache key. Width, height, emphasis, suppression và device scale cũng có trong
key. Nếu bổ sung theme/variant hoặc dữ liệu khác làm đổi hình trong cùng process, thêm yếu tố
đó vào key hoặc dùng invalidation rõ ràng. Không chỉ sửa hàm vẽ rồi giả định cache sẽ biết.
Hiện catalog không reload giữa match.

### Hai lần sampling

Native sprite được scale qua Java2D rồi ảnh kết quả được JavaFX hiển thị; phải giữ nét ở cả hai:

- Java2D: `RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR` khi scale pixel art.
- Canvas JavaFX: dùng `FxKit.drawFace(...)`; helper tạm tắt image smoothing cho card pixel,
  rồi khôi phục trạng thái cũ bằng `finally`.
- `CardDetails`: `ImageView.setSmooth(false)` cho card pixel.
- `TableEffects`: cùng thiết lập cho card bay/lật và mảnh vỡ. Việc constructor mặc định smooth
  không đủ để kết luận có lỗi; xem cả chỗ tạo node nơi nó được chỉnh theo card.
- `CardFace.paint(...)`: giữ nearest-neighbour cho đường vẽ Swing/Java2D.

Không tắt smoothing toàn bộ background hoặc mọi ảnh trong game. Vẫn giữ selection border,
shadow, suppressed shade, hover preview và animation hiện có. Ghi nhận rằng resize lẻ và
rotation có thể tạo pixel rộng không đều; test khối pixel chính xác ở scale nguyên.

### Thread và thông tin hiển thị

JavaFX scene graph chỉ cập nhật trên JavaFX Application Thread; `ClientModel` vẫn thuộc EDT.
Không thêm file I/O hoặc download trong phương thức vẽ. Chỉ vẽ dữ liệu recipient được phép
thấy. Không bỏ qua điều kiện Spy reveal hoặc dùng rendering task để đọc bài đối thủ.

## 9. Build và xuất screenshot có thể tái lập

Các lệnh sau chạy từ root repository trên macOS/Linux, dùng classpath separator `:`.
Windows dùng `;`. `javac` và `java` cần tương thích với class đã compile và native JavaFX của máy.

Sau khi sửa code:

```sh
mvn test
mvn -DskipTests package
javac -cp target/classes:target/deck-duel.jar -d target/pixel-preview tools/RenderPixelCard.java
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard art/cards/concepts/java-pixel
```

Chỉ skip test ở bước đóng gói nếu test vừa chạy thành công trên cùng code. Với migration hoàn
chỉnh, chạy thêm `mvn clean verify` theo `AGENTS.md`; nhớ compile lại preview tool sau `clean`.

Các chế độ tool:

```sh
# Chỉ export Java2D, không khởi động toolkit JavaFX.
java -Djava.awt.headless=true -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard art/cards/concepts/java-pixel --native

# Export mọi ảnh rồi giữ cửa sổ preview Poland mở.
java -cp target/pixel-preview:target/classes:target/deck-duel.jar deckduel.client.ui.RenderPixelCard art/cards/concepts/java-pixel --show
```

**Thư mục output phải là đối số đầu tiên**, sau đó mới đặt flag. Tool hiện mở preview Poland
khi dùng `--show`; các gallery nation/Spell được export ra file, không tự mở thành cửa sổ riêng.

Output hiện tại:

| File | Nội dung |
| --- | --- |
| `<id>-native.png` | Sprite 80×120 cho mọi card mà `supports()` nhận |
| `<id>-rendered.png` | Card 320×480 cộng margin shadow của `CardFace` |
| `javafx-preview.png` | Poland 1×, 2×, 4×, selected và tags-disabled |
| `nations-preview.png` | Knights cả bảy nation, 2× |
| `axemen-preview.png` | Axemen cả bảy nation, 2× |
| `bowmen-preview.png` / `riders-preview.png` | Hai class được làm lại, cả bảy nation, 2× |
| `before-after.png` | Nếu folder output có hai native PNG cũ trong `before/`, so sánh 3× và 104×156 |
| `all-units.png` | Lưới đủ 35 Units, 2× |
| `spells-preview.png` | Draw Two/Spy, 3×, caption từ catalog |
| `match-table.png` | Bàn JavaFX 1280×800 với một lượt chơi Poland Knights hợp lệ |

Export PNG là bằng chứng xem trước; game không load các file đó làm art. Tool dùng core state
có seed để dựng bàn mẫu, không phải screenshot của một match network đang chạy. Nó không
tạo account hay lưu settings. Nếu thêm card mới, export native tự động tăng theo `supports()`,
nhưng danh sách nation/class của gallery hiện được chọn trong tool: phải cập nhật riêng để card mới xuất hiện.
Mẫu bàn hiện cũng chỉ chọn Poland Knights; đổi scenario có chủ đích nếu cần chứng minh card khác.

JavaFX snapshot cần session đồ họa; `--native` không thay thế được kiểm tra màn chơi. Nếu
sandbox chặn GUI hoặc localhost test, dùng cơ chế cấp quyền của môi trường để chạy lại.
Không coi crash toolkit hoặc lỗi bind socket là test pass và không bỏ test để báo xanh.

## 10. Kiểm tra trước khi bàn giao

Mở ảnh xuất ra và kiểm tra trực tiếp; file tồn tại chưa chứng minh hình đúng.

| Kiểm tra | Kết quả cần đạt |
| --- | --- |
| Native size / alpha | 80×120; góc trong suốt; thân card kín; không có alpha trung gian trong art |
| Palette | Mỗi sprite trong giới hạn 16 màu, tính cả transparent |
| Integer scaling | Mỗi pixel interior thành khối màu đồng nhất ở 3×; không có màu trộn |
| Ảnh JPEG load sau | Mặt pixel không bị thay; cache vẫn dùng đúng sprite |
| Dữ liệu | ID, tên, nation, class, điểm, count và hướng effect đúng catalog |
| Chữ | Đủ glyph, không tràn, không đè crest, đọc được ở kích thước thật |
| Nation / class | Nhìn rõ crest và class icon; cùng class giữ thiết kế nhất quán |
| Spell | Không có điểm/nation giả; hình không ngụ ý effect khác |
| UI | Selection, points/tags disable trên Unit, hover và Spy inspector vẫn hoạt động |
| Privacy / back | Mặt lưng không lộ card; inspector chỉ dùng card đã reveal |
| Animation | Card bay/lật/vỡ không chuyển sang ảnh khác hoặc bị smoothing ngoài ý muốn |
| Scope | `supports()` nhận đúng tập ID đã triển khai; card khác giữ đường vẽ hiện tại |

Mở rộng `PixelCardFaceTest` khi tăng tập card: tập 35 Unit ID trong catalog cộng hai Spell được assert rõ; giữ assertion
này khớp phạm vi mới. Test hiện kiểm palette/scaling trên mọi card được hỗ trợ và so việc đổi
điểm Poland 5→6 chỉ đổi vùng numeral. Giữ các test artwork, inspector và bàn game hiện có.

Bằng chứng test gần nhất trước tài liệu này: **150 test, 0 failures, 0 errors, 0 skipped** sau
batch năm card, ghi trong `docs/verification/2026-10-21-pixel-card.md`. Đây là kết quả lịch sử,
không phải số phải hard-code cho lần chạy sau. Luôn báo actual count của lần mình thực sự chạy.

Lưu ghi chép kiểm tra với input, expected, actual, pass/fail, môi trường và đường dẫn screenshot.
Không đưa DB cá nhân, credential, log hoặc `target/` vào commit. Không tuyên bố cải thiện FPS,
latency hay bộ nhớ nếu không có workload và phép đo trước/sau.

## 11. Những lỗi nên tránh

- Cài browser tool, node framework hay cloud service vào runtime chỉ để dựng card pixel.
- Dùng artwork Balatro hoặc asset từ repo khác mà chưa kiểm tra quyền sử dụng.
- Vẽ illustration quá chi tiết rồi chỉ kiểm ở 4×; mất khả năng đọc ở hand/row.
- Dùng `drawString()` với font hệ thống cho chữ trên native card, tạo nét khác và khó tái lập.
- Cho `supports()` nhận cả class/nation khi mới hoàn thành một vài hình.
- Quên thêm glyph cho tên dài hoặc ký tự mới.
- Đổi ID/tên/điểm/effect trong catalog để né vấn đề layout.
- Xuất gallery đẹp nhưng game vẫn đi nhánh JPEG; phải kiểm qua `CardFace` và `FxKit` thật.
- Xóa artwork cũ hoặc thay toàn bộ deck khi task chỉ yêu cầu vài mẫu.
- Copy test count cũ hoặc gọi ảnh dựng từ core state là match live/network.

## 12. Cách bàn giao cho người dùng và agent tiếp theo

Người dùng thích xem screenshot trước. Với batch mới, đưa ảnh ngay sau khi render và kiểm tra;
nói rõ những ID đã làm, mẫu nào là snapshot bàn game và mẫu nào là gallery. Dùng ảnh inline
trong chat với đường dẫn tuyệt đối. Sau đó báo ngắn kết quả test/build và phần còn thiếu.
Không hỏi lại những lựa chọn mỹ thuật nhỏ đã được phạm vi task cho phép.

Mẫu prompt giao việc tiếp theo:

> Đọc `AGENTS.md` và `CARD_RENDERING_GUIDE.md`. Tiếp tục phong cách Java-rendered pixel card
> đã có cho các ID: [liệt kê ID catalog]. Vẽ bằng code trên native 80×120; giữ nội dung catalog.
> Nối qua `CardFace`/`FxKit`, cập nhật `supports()`, glyph, test coverage và preview gallery.
> Xuất screenshot bằng JavaFX cho tôi xem; chạy test, kiểm ảnh và báo phạm vi thực tế.

Nguồn tham khảo kỹ thuật đã đọc trong task gốc:

- [Card Generator — CardBuilder.tsx](https://github.com/DFATPUNK/balatro-card-generator/blob/main/src/components/CardBuilder.tsx)
- [Card Generator — layerPositions.json](https://github.com/DFATPUNK/balatro-card-generator/blob/main/src/config/layerPositions.json)
- [Joker Forge — BalatroCard.tsx](https://github.com/Jaydchw/joker-forge/blob/b30c7ed9d9ed51fcda6795a1c6e232ac76af23fb/src/components/generic/BalatroCard.tsx)

Các link trên giải thích cách ghép lớp ở tọa độ nhỏ và hiển thị pixelated. Source Java trong
repo và screenshot đã kiểm là điểm bắt đầu thực tế để tiếp tục; không cần phụ thuộc các web tool.
