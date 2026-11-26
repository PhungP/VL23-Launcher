### 0.29
- **Cập nhật OTA an toàn hơn:** sau khi tải, app kiểm tra file .apk (đúng package `vn.navara.launcher`, versionCode mới hơn, cùng chữ ký với bản đang cài, đủ dung lượng) rồi mới mở trình cài đặt; file sai bị xoá và báo lý do.
- Thêm kiểm thử đơn vị cho so sánh phiên bản và đọc mã lỗi DTC.

### 0.28
- **Bỏ giao diện Robot và SciFi.** Còn bốn bộ giao diện: Đồng hồ, HUD SU-35, HUD Orange, HUD Cyber. Máy đang dùng Robot hoặc SciFi tự về Đồng hồ.
- **Trang 1 của ba bộ HUD gọn hơn:** bỏ thanh dock và thanh điều hướng, màn hình HUD toàn màn hình (vuốt ngang để sang trang khác). Tắt bớt được từng ô (giờ và thời tiết, nhạc, tốc độ, tải, nhiệt độ nước, nhiệt độ khí nạp, điện áp, tiêu thụ, áp suất lốp) ở Cài đặt > Hiển thị > HUD; các ô còn lại tự dàn đều và to hơn. Bốn ô áp suất lốp chỉ hiện khi bật TPMS. Ba nút [1] [2] [3] chuyển nhanh bộ HUD giờ là tuỳ chọn (tắt mặc định). Dòng hướng đi với mũi tên chỉ đường nằm dưới cùng cột trái.
- **Trang Cài đặt chia theo chủ đề:** mở vào là danh sách (Giao diện, Hiển thị, Xe và OBD, Bản đồ, Trợ lý AI, Hệ thống, Về ứng dụng); bấm một mục mới hiện phần cài đặt tương ứng, phím Back hoặc nút "‹ Cài đặt" quay về danh sách.
- **Nút AI nổi:** thêm công tắc bật tắt và chọn các trang hiển thị nút (Cài đặt > Trợ lý AI > Nút AI nổi); mặc định chỉ hiện ở trang 1. Trên trang 1 của bộ HUD nút nằm sát đáy vì không có dock.

