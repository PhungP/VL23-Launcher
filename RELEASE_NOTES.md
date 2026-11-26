### 0.30
**Cập nhật lớn: độ tin cậy dữ liệu xe, cảnh báo, chẩn đoán OBD và hồ sơ xe.**

#### 1. Độ tin cậy dữ liệu (Data Quality)
- Mỗi thông số có trạng thái: Trực tiếp, GPS, Ước tính, Tính toán, Quá hạn, Không có, Lỗi.
- Tốc độ ưu tiên dữ liệu OBD còn mới, chuyển sang GPS khi OBD không còn dữ liệu mới; nếu cả hai đều không có thì báo "không có".
- Cấp số suy từ vòng tua luôn được đánh dấu "Ước tính".
- Dữ liệu OBD không còn cập nhật được đánh dấu "Quá hạn" thay vì hiển thị như dữ liệu trực tiếp: quá 3 giây với tốc độ, vòng tua, cấp số; quá 15 giây với nhiệt độ nước, điện áp, tăng áp (các thông số này được hỏi thưa hơn).

#### 2. Trung tâm cảnh báo (Alert Center)
- Cảnh báo: nhiệt độ nước (2 mức), điện áp thấp, vòng tua quá cao (mặc định theo vạch đỏ của hồ sơ xe), tăng áp, áp suất lốp thấp/cao, mất cảm biến TPMS, mất OBD, mất GPS, dữ liệu không còn cập nhật.
- Điều kiện phải kéo dài vài giây mới báo; cùng một cảnh báo không lặp lại trong 60 giây.
- Xác nhận bằng cách chạm vào thanh cảnh báo; có lịch sử cảnh báo.
- Ngưỡng chỉnh được và bật/tắt theo nhóm (Cài đặt > Cảnh báo và chế độ lái).
- Chỉ dữ liệu đo trực tiếp (Trực tiếp / GPS) mới tạo cảnh báo kỹ thuật; dữ liệu ước tính, quá hạn hoặc lỗi không gây cảnh báo.

#### 3. Chế độ lái / đỗ (Drive Mode)
- Tự chuyển giữa Lái và Đỗ theo tốc độ OBD hoặc GPS, có hai ngưỡng (hysteresis) và thời gian giữ để không nhảy liên tục; chọn thủ công thắng chế độ tự động.
- Tốc độ cũ, ước tính hoặc không có không làm đổi chế độ.
- **Tính năng tự chuyển mặc định tắt**, bật trong Cài đặt.
- Khi đang lái: ẩn nút AI nổi, thanh cảnh báo gọn (bỏ cảnh báo mức Lưu ý), mở Cài đặt cần xác nhận (tắt được).
- Chế độ ban đêm (19h–6h) mới có phần nhận diện thời gian, **chưa tự đổi giao diện**.

#### 4. Chẩn đoán OBD
- Cài đặt > Xe và OBD > Chẩn đoán OBD: trạng thái kết nối, tên adapter, thời điểm nhận dữ liệu gần nhất, độ trễ trung bình / gần nhất, tần suất hỏi, số phản hồi thành công, số lần hết giờ và lỗi, số lần mất kết nối, số PID xe báo hỗ trợ.

#### 5. Quản lý hồ sơ xe (Vehicle Profiles)
- Cài đặt > Xe và OBD > Quản lý hồ sơ: tìm kiếm, lọc (dựng sẵn / của tôi / xăng / dầu), sắp xếp, xem chi tiết thông số.
- Tạo, chỉnh sửa, nhân bản (kể cả từ hồ sơ dựng sẵn) và xoá hồ sơ người dùng; có kiểm tra giá trị nhập (chu vi lốp, vạch đỏ, tỉ số hộp số giảm dần, PID dạng hex, ngưỡng cảnh báo...).
- Hồ sơ dựng sẵn không sửa hoặc xoá được; hồ sơ đang dùng không xoá được.
- Hồ sơ người dùng điều khiển thực tế: tỉ số hộp số, cầu cuối, chu vi lốp, thang vòng tua, bộ giao diện và 6 ô trang 1, thông số hiển thị ở trang OBD, PID bỏ qua và PID ưu tiên (xoay vòng khi đọc).
- **Giao thức OBD:** tự động hoặc cố định; khi đổi giao thức, app tự khởi tạo lại adapter.
- **Ngưỡng cảnh báo riêng** của hồ sơ xe **ghi đè** ngưỡng trong cài đặt chung (trường để trống thì dùng cài đặt chung).
- Khi đổi xe, cảnh báo đang bật của xe cũ được xoá và điều kiện được tính lại từ đầu.

#### 6. Nhập, xuất và sao lưu hồ sơ
- Nhập / xuất JSON (UTF-8, có schema và metadata) qua bộ chọn tệp, lưu tệp hoặc Chia sẻ.
- Xem trước trước khi nhập; phát hiện trùng mã, trùng tên, hồ sơ không hợp lệ. Mặc định **không ghi đè**; thay thế chỉ khi tự chọn và xác nhận.
- Từ chối tệp quá lớn (>512 KB), sai kiểu dữ liệu hoặc tạo bởi schema mới hơn; tệp không có schema (một mảng hoặc một hồ sơ) vẫn đọc được. Nội dung nhập chỉ là dữ liệu, không được thực thi.
- Sao lưu / khôi phục hồ sơ của bạn và hồ sơ đang chọn (không có khoá API, token hay bộ nhớ đệm). Khôi phục có xem trước, gộp hoặc thay toàn bộ (có xác nhận); nếu ghi lỗi thì dữ liệu cũ được giữ nguyên.
- Sao lưu hiện chỉ gồm hồ sơ xe, chưa gồm toàn bộ cài đặt ứng dụng.

#### 7. Bảo mật cập nhật OTA (từ 0.29)
- Sau khi tải, app kiểm tra file APK trước khi mở trình cài đặt: đúng package `vn.navara.launcher`, versionCode mới hơn bản đang cài, cùng chữ ký với bản đang cài.
- Kiểm tra file tải về đủ dung lượng, không bị cắt giữa chừng. File không hợp lệ bị xoá và báo lý do.

#### 8. Độ tin cậy lưu trữ
- Hồ sơ người dùng lưu trong `vehicle_profiles.json` (schema 1), ghi nguyên tử (ghi tệp tạm rồi đổi tên) và giữ bản dự phòng `.bak`; nếu tệp chính hỏng thì khôi phục từ bản dự phòng.
- Mã hồ sơ dựng sẵn và khoá hồ sơ đang chọn không đổi nên máy cập nhật lên vẫn giữ xe đã chọn. Cấu hình cảnh báo / chế độ lái lưu riêng (`vl23_monitor`), không đụng cấu hình cũ.
- Bản này không có thay đổi nhằm tối ưu hiệu năng.

#### Kiểm thử và xác minh
- **Đã xác minh:**
  - 110 unit test: PASS. Build Debug: PASS.
  - Build Release ký bằng khoá phát hành thật: PASS. Vân tay chứng chỉ SHA-256 `853ecf48…06f4`, trùng với các bản 0.27 đến 0.29.
  - File APK tải từ GitHub khớp từng byte (SHA-256) với file đã build.
  - Emulator: app mở bình thường, màn hình quản lý hồ sơ hiển thị, nhân bản hồ sơ và lưu tệp thành công, khởi động lại không crash.
- **Chưa kiểm chứng:**
  - Cài đè thực tế lên bản đang cài (cập nhật OTA từ 0.28 hoặc 0.29 lên 0.30).
  - OBD adapter và ECU thật; GPS và TPMS thật; màn hình ô tô các độ phân giải.
  - Trên giao diện: chọn, sửa, xoá hồ sơ; nhập, xuất, khôi phục; chia sẻ tệp. (Phần dữ liệu bên dưới đã có unit test.)

**Khuyến nghị:** hãy cập nhật thử trên một thiết bị trước khi triển khai rộng rãi.

#### Chưa có
- Đơn vị đo và chế độ lái theo từng hồ sơ; ảnh xe cho hồ sơ người dùng (đang dùng hình vector).
- Bộ lập lịch PID theo ba mức ưu tiên cao / trung bình / thấp, preset HUD, nhãn nguồn dữ liệu trên các ô HUD, giao diện ban đêm thực sự.

### 0.29
- **Cập nhật OTA an toàn hơn:** sau khi tải, app kiểm tra file .apk (đúng package `vn.navara.launcher`, versionCode mới hơn, cùng chữ ký với bản đang cài, đủ dung lượng) rồi mới mở trình cài đặt; file sai bị xoá và báo lý do.
- Thêm kiểm thử đơn vị cho so sánh phiên bản và đọc mã lỗi DTC.

### 0.28
- **Bỏ giao diện Robot và SciFi.** Còn bốn bộ giao diện: Đồng hồ, HUD SU-35, HUD Orange, HUD Cyber. Máy đang dùng Robot hoặc SciFi tự về Đồng hồ.
- **Trang 1 của ba bộ HUD gọn hơn:** bỏ thanh dock và thanh điều hướng, màn hình HUD toàn màn hình (vuốt ngang để sang trang khác). Tắt bớt được từng ô (giờ và thời tiết, nhạc, tốc độ, tải, nhiệt độ nước, nhiệt độ khí nạp, điện áp, tiêu thụ, áp suất lốp) ở Cài đặt > Hiển thị > HUD; các ô còn lại tự dàn đều và to hơn. Bốn ô áp suất lốp chỉ hiện khi bật TPMS. Ba nút [1] [2] [3] chuyển nhanh bộ HUD giờ là tuỳ chọn (tắt mặc định). Dòng hướng đi với mũi tên chỉ đường nằm dưới cùng cột trái.
- **Trang Cài đặt chia theo chủ đề:** mở vào là danh sách (Giao diện, Hiển thị, Xe và OBD, Bản đồ, Trợ lý AI, Hệ thống, Về ứng dụng); bấm một mục mới hiện phần cài đặt tương ứng, phím Back hoặc nút "‹ Cài đặt" quay về danh sách.
- **Nút AI nổi:** thêm công tắc bật tắt và chọn các trang hiển thị nút (Cài đặt > Trợ lý AI > Nút AI nổi); mặc định chỉ hiện ở trang 1. Trên trang 1 của bộ HUD nút nằm sát đáy vì không có dock.

