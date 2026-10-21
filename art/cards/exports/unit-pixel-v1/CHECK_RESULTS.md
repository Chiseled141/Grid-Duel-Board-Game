# Kiểm tra bàn giao — 2026-10-21

| Check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Compile source độc lập | Java 17, không dependency game/JavaFX | `javac --release 17` thành công | PASS |
| Export metadata | 7 ID từ catalog thật | 7 card; điểm/class/nation khớp catalog | PASS |
| Native / palette / alpha | 80×120, tối đa 16 màu, góc transparent, thân opaque | 7/7 qua assertion exporter, chi tiết trong VERIFICATION.md | PASS |
| Enlargement | 320×480; mỗi pixel thành khối 4×4 cùng màu | 7/7 qua so sánh từng pixel | PASS |
| Deterministic | Render lần hai giống lần đầu | 7/7 giống pixel | PASS |
| PNG integrity | Checksum manifest trùng file | 14/14 SHA-256 trùng | PASS |
| Visual QA | Hình và label rõ, đủ năm class Poland và hai Knights bổ sung | Đã mở xem preview-poland.png và preview-all.png | PASS |
| Handoff links | Code fence đóng đủ, ảnh tham chiếu tồn tại | Đã kiểm README/HANDOFF | PASS |
| Regression `mvn test` | Không fail/error/skip | 150 tests, 0 failures, 0 errors, 0 skipped; 29.230 s | PASS |
| Whitespace | `git diff --check` sạch | Không báo lỗi | PASS |

Export chạy OpenJDK 26.0.1 trên macOS arm64; source target Java 17. Maven regression suite
chạy với quyền localhost/JavaFX sau lần sandbox đầu chặn các khả năng đó. Bộ pack không nằm
trong Maven source set; assertion của exporter kiểm artwork mới, Maven kiểm hồi quy game hiện tại.
Không đo performance và không thay đổi source/runtime resource của game trong batch này.
Không đóng gói JAR mới vì pack được giữ riêng để người dùng giao Claude tích hợp sau.
