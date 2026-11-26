### Chưa phát hành (đang phát triển sau 0.29)
- **Phân loại độ tin cậy dữ liệu** (`DataQuality.kt`): mỗi thông số có trạng thái Trực tiếp / GPS / Ước tính / Tính toán / Quá hạn / Không có / Lỗi. Tốc độ lấy từ OBD khi còn mới, không thì GPS, không thì "không có"; cấp số suy từ vòng tua luôn là "Ước tính"; dữ liệu OBD quá 3 giây không phản hồi chuyển sang "Quá hạn".
- **Trung tâm cảnh báo** (`AlertEngine.kt`, `AlertUi.kt`): nhiệt độ nước (cảnh báo và nghiêm trọng), điện áp thấp, vòng tua quá cao (mặc định theo vạch đỏ của hồ sơ xe), tăng áp, áp suất lốp thấp/cao/mất cảm biến, mất OBD, mất GPS, dữ liệu cũ. Có debounce, cooldown 60 giây, xác nhận bằng cách chạm vào thanh cảnh báo, lịch sử, ngưỡng chỉnh được và bật/tắt theo nhóm. Chỉ dữ liệu đo trực tiếp mới tạo cảnh báo. Cài đặt > Cảnh báo và chế độ lái.
- **Chế độ lái / đỗ** (`DriveMode.kt`): tự chuyển theo tốc độ OBD hoặc GPS với hai ngưỡng (hysteresis) và thời gian giữ, chọn thủ công thắng tự động, dữ liệu tốc độ cũ/ước tính không bao giờ làm đổi chế độ. **Mặc định tắt** (không đổi hành vi hiện có). Khi lái: ẩn nút AI nổi, thanh cảnh báo gọn và bỏ cảnh báo Lưu ý, mở Cài đặt cần xác nhận (tắt được). Có tùy chọn tự ban đêm 19h–6h (hiện chỉ tính trạng thái, chưa đổi giao diện).
- **Chẩn đoán OBD** (Cài đặt > Xe và OBD > Chẩn đoán OBD, `ObdStats.kt`): trạng thái, tên adapter, thời điểm nhận dữ liệu cuối, độ trễ trung bình/gần nhất, tần suất hỏi, số phản hồi thành công/hết giờ/lỗi, số lần mất kết nối, số PID hỗ trợ.
- Cấu hình lưu riêng trong `vl23_monitor`, không đụng dữ liệu cấu hình cũ.
- **Kiểm thử:** 55 unit test đều pass. Chưa chạy được trên emulator (hệ thống emulator Android tự sập trước khi cài app), chưa kiểm thử trên màn hình/xe thật.
- **Chưa làm:** hồ sơ xe tạo/sửa/xuất/nhập, bộ lập lịch PID theo ưu tiên, preset HUD, hiển thị nhãn nguồn dữ liệu trên các ô HUD, giao diện ban đêm thực sự.

### 0.29
- **Cập nhật OTA an toàn hơn:** sau khi tải, app kiểm tra file .apk (đúng package `vn.navara.launcher`, versionCode mới hơn, cùng chữ ký với bản đang cài, đủ dung lượng) rồi mới mở trình cài đặt; file sai bị xoá và báo lý do.
- Thêm kiểm thử đơn vị cho so sánh phiên bản và đọc mã lỗi DTC.

### 0.28
- **Bỏ giao diện Robot và SciFi.** Còn bốn bộ giao diện: Đồng hồ, HUD SU-35, HUD Orange, HUD Cyber. Máy đang dùng Robot hoặc SciFi tự về Đồng hồ.
- **Trang 1 của ba bộ HUD gọn hơn:** bỏ thanh dock và thanh điều hướng, màn hình HUD toàn màn hình (vuốt ngang để sang trang khác). Tắt bớt được từng ô (giờ và thời tiết, nhạc, tốc độ, tải, nhiệt độ nước, nhiệt độ khí nạp, điện áp, tiêu thụ, áp suất lốp) ở Cài đặt > Hiển thị > HUD; các ô còn lại tự dàn đều và to hơn. Bốn ô áp suất lốp chỉ hiện khi bật TPMS. Ba nút [1] [2] [3] chuyển nhanh bộ HUD giờ là tuỳ chọn (tắt mặc định). Dòng hướng đi với mũi tên chỉ đường nằm dưới cùng cột trái.
- **Trang Cài đặt chia theo chủ đề:** mở vào là danh sách (Giao diện, Hiển thị, Xe và OBD, Bản đồ, Trợ lý AI, Hệ thống, Về ứng dụng); bấm một mục mới hiện phần cài đặt tương ứng, phím Back hoặc nút "‹ Cài đặt" quay về danh sách.
- **Nút AI nổi:** thêm công tắc bật tắt và chọn các trang hiển thị nút (Cài đặt > Trợ lý AI > Nút AI nổi); mặc định chỉ hiện ở trang 1. Trên trang 1 của bộ HUD nút nằm sát đáy vì không có dock.

