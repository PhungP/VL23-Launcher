### 0.30
- **Phân loại độ tin cậy dữ liệu** (`DataQuality.kt`): mỗi thông số có trạng thái Trực tiếp / GPS / Ước tính / Tính toán / Quá hạn / Không có / Lỗi. Tốc độ lấy từ OBD khi còn mới, không thì GPS, không thì "không có"; cấp số suy từ vòng tua luôn là "Ước tính"; dữ liệu OBD quá 3 giây không phản hồi chuyển sang "Quá hạn".
- **Trung tâm cảnh báo** (`AlertEngine.kt`, `AlertUi.kt`): nhiệt độ nước (cảnh báo và nghiêm trọng), điện áp thấp, vòng tua quá cao (mặc định theo vạch đỏ của hồ sơ xe), tăng áp, áp suất lốp thấp/cao/mất cảm biến, mất OBD, mất GPS, dữ liệu cũ. Có debounce, cooldown 60 giây, xác nhận bằng cách chạm vào thanh cảnh báo, lịch sử, ngưỡng chỉnh được và bật/tắt theo nhóm. Chỉ dữ liệu đo trực tiếp mới tạo cảnh báo. Cài đặt > Cảnh báo và chế độ lái.
- **Chế độ lái / đỗ** (`DriveMode.kt`): tự chuyển theo tốc độ OBD hoặc GPS với hai ngưỡng (hysteresis) và thời gian giữ, chọn thủ công thắng tự động, dữ liệu tốc độ cũ/ước tính không bao giờ làm đổi chế độ. **Mặc định tắt** (không đổi hành vi hiện có). Khi lái: ẩn nút AI nổi, thanh cảnh báo gọn và bỏ cảnh báo Lưu ý, mở Cài đặt cần xác nhận (tắt được). Có tùy chọn tự ban đêm 19h–6h (hiện chỉ tính trạng thái, chưa đổi giao diện).
- **Chẩn đoán OBD** (Cài đặt > Xe và OBD > Chẩn đoán OBD, `ObdStats.kt`): trạng thái, tên adapter, thời điểm nhận dữ liệu cuối, độ trễ trung bình/gần nhất, tần suất hỏi, số phản hồi thành công/hết giờ/lỗi, số lần mất kết nối, số PID hỗ trợ.
- **Quản lý hồ sơ xe** (Cài đặt > Xe và OBD > Quản lý hồ sơ; `ProfileData.kt`, `ProfileRepository.kt`, `ProfileBridge.kt`, `ProfileUi.kt`):
  - Danh sách có tìm kiếm, lọc (dựng sẵn / của tôi / xăng / dầu), sắp xếp, đánh dấu hồ sơ đang dùng; chi tiết đầy đủ thông số.
  - Tạo, sửa, xoá, nhân bản hồ sơ (cả từ hồ sơ dựng sẵn). Form có kiểm tra giá trị (chu vi lốp, vạch đỏ, tỉ số hộp số giảm dần, PID hex, ngưỡng cảnh báo...). Hồ sơ dựng sẵn không sửa/xoá được; hồ sơ đang dùng không xoá được.
  - Hồ sơ người dùng điều khiển thật: bảng tỉ số / chu vi lốp, thang vòng tua, bộ giao diện, 6 ô trang 1, thông số OBD, PID bỏ qua và PID ưu tiên (xoay vòng khi đọc), giao thức OBD (ATSP0 tự động hoặc cố định, đổi giao thức thì tự khởi tạo lại adapter), ngưỡng cảnh báo riêng (đè lên cài đặt chung). Đổi xe thì cảnh báo đang bật của xe cũ bị xoá.
  - Nhập / xuất JSON (UTF-8, có schema, metadata) qua bộ chọn tệp, lưu tệp hoặc Chia sẻ; xem trước trước khi nhập, phát hiện trùng mã / trùng tên / không hợp lệ, mặc định không ghi đè, thay thế chỉ khi tự chọn và xác nhận. Tệp quá lớn (>512 KB), sai kiểu, schema mới hơn đều bị từ chối. Tệp không có schema (mảng hoặc một hồ sơ) vẫn đọc được.
  - Sao lưu / khôi phục hồ sơ người dùng và hồ sơ đang chọn (không có khoá API, token, cache); khôi phục có xem trước, gộp hoặc thay toàn bộ (có xác nhận), ghi lỗi thì giữ nguyên dữ liệu cũ.
  - Lưu trữ: tệp `vehicle_profiles.json` (schema 1) trong bộ nhớ riêng của app, ghi nguyên tử và giữ bản `.bak`. Mã hồ sơ dựng sẵn và hồ sơ đang chọn (`UiPrefs.vehicle`) không đổi nên máy cũ cập nhật lên vẫn giữ nguyên xe đã chọn.
- Cấu hình lưu riêng trong `vl23_monitor`, không đụng dữ liệu cấu hình cũ.
- **Kiểm thử:** 110 unit test đều pass. Chưa chạy được trên emulator (hệ thống emulator Android tự sập trước khi cài app), chưa kiểm thử trên màn hình/xe thật.
- **Hồ sơ xe, chưa có / chỉ có một phần:** đơn vị đo và chế độ lái theo từng hồ sơ (hiện là cài đặt chung); biểu tượng ảnh xe cho hồ sơ người dùng (dùng hình vector); xuất nhiều hồ sơ chọn bằng hộp tick (đã có) nhưng chưa thử chia sẻ qua ứng dụng khác trên máy thật; sao lưu chỉ gồm hồ sơ, chưa gồm toàn bộ cài đặt ứng dụng.
- **Chưa làm:** bộ lập lịch PID theo ưu tiên, preset HUD, hiển thị nhãn nguồn dữ liệu trên các ô HUD, giao diện ban đêm thực sự.

### 0.29
- **Cập nhật OTA an toàn hơn:** sau khi tải, app kiểm tra file .apk (đúng package `vn.navara.launcher`, versionCode mới hơn, cùng chữ ký với bản đang cài, đủ dung lượng) rồi mới mở trình cài đặt; file sai bị xoá và báo lý do.
- Thêm kiểm thử đơn vị cho so sánh phiên bản và đọc mã lỗi DTC.

### 0.28
- **Bỏ giao diện Robot và SciFi.** Còn bốn bộ giao diện: Đồng hồ, HUD SU-35, HUD Orange, HUD Cyber. Máy đang dùng Robot hoặc SciFi tự về Đồng hồ.
- **Trang 1 của ba bộ HUD gọn hơn:** bỏ thanh dock và thanh điều hướng, màn hình HUD toàn màn hình (vuốt ngang để sang trang khác). Tắt bớt được từng ô (giờ và thời tiết, nhạc, tốc độ, tải, nhiệt độ nước, nhiệt độ khí nạp, điện áp, tiêu thụ, áp suất lốp) ở Cài đặt > Hiển thị > HUD; các ô còn lại tự dàn đều và to hơn. Bốn ô áp suất lốp chỉ hiện khi bật TPMS. Ba nút [1] [2] [3] chuyển nhanh bộ HUD giờ là tuỳ chọn (tắt mặc định). Dòng hướng đi với mũi tên chỉ đường nằm dưới cùng cột trái.
- **Trang Cài đặt chia theo chủ đề:** mở vào là danh sách (Giao diện, Hiển thị, Xe và OBD, Bản đồ, Trợ lý AI, Hệ thống, Về ứng dụng); bấm một mục mới hiện phần cài đặt tương ứng, phím Back hoặc nút "‹ Cài đặt" quay về danh sách.
- **Nút AI nổi:** thêm công tắc bật tắt và chọn các trang hiển thị nút (Cài đặt > Trợ lý AI > Nút AI nổi); mặc định chỉ hiện ở trang 1. Trên trang 1 của bộ HUD nút nằm sát đáy vì không có dock.

