# Kiểm tra pack Axemen — 2026-10-21

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Compile exporter | Java 17 target, không thư viện ngoài | `javac --release 17` thành công | PASS |
| Axemen preview | Poland Axemen, 4 điểm, rìu và glyph X đọc được | Đã mở xem `preview-poland.png` | PASS |
| Native images | 80×120, góc transparent, thân opaque, tối đa 16 màu | 7/7 qua exporter assertions | PASS |
| PNG 4× | 320×480; mỗi pixel thành khối 4×4 không pha màu | 7/7 qua so sánh từng pixel | PASS |
| Determinism | Hai lần render giống pixel | 7/7 qua assertions | PASS |
| File checksums | SHA-256 khớp manifest | 14/14 PNG khớp | PASS |
| Catalog snapshot | 35 Units, 10 Spells, 5 class; 7 Axemen của 7 nation, mỗi lá 4 điểm | Đúng tất cả điều kiện | PASS |
| Scope | Catalog live không bị sửa | SHA-256 bằng catalog đã dùng ở pack v1 | PASS |
| Retained artwork | 6 thiết kế không đổi giống bản v1 | 6/6 native PNG có cùng SHA-256 | PASS |
| Regression game `mvn test` | Không fail/error/skip | 150 tests, 0 failures, 0 errors, 0 skipped; 28.203 s | PASS |
| Documents | Link ảnh tồn tại, code fence đóng đủ, không có `.class` trong pack | Đã kiểm | PASS |

Export chạy OpenJDK 26.0.1 trên macOS arm64, source target Java 17. Regression suite có
quyền JavaFX/localhost. Artwork mới được kiểm bởi exporter độc lập, chưa được đưa vào
Maven source set/game client. Không đóng JAR mới, không đổi catalog runtime, không đo performance.
Phiên bản snapshot chỉ phục vụ lần tích hợp sau; pack có artwork Axemen cho Poland, chưa có
artwork Axemen cho sáu nation còn lại.
