# VL23 Launcher

Launcher Android dành cho màn hình đầu máy trên ô tô (thiết kế cho xe bán tải Nissan Navara NP300, màn hình ngang 800x480). Giao diện kiểu đồng hồ xe, hiển thị thông số OBD, bản đồ dẫn đường, nhạc đang phát và thời tiết ngay trên màn hình chính.

**Nhà phát triển:** Phan Kim Phụng
**Mã nguồn và bản phát hành:** https://github.com/PhungP/VL23-Launcher

## Tính năng

- **Trang chủ OBD:** vòng tua, tải động cơ, nhiệt độ nước, boost, nhiệt độ khí nạp, điện áp ắc quy; cấp số đang cài; la bàn, giờ, thời tiết, bài hát đang phát.
- **Tùy chọn trang 1:** chọn hình xe (ảnh Navara, ảnh riêng, khung dây, bóng xe), chọn 6 thông số OBD hiển thị, chọn kiểu hiển thị cấp số (thanh 7 ô, dải số, LED, số lớn, ẩn).
- **Giao diện:** Đồng hồ xe, Số đen, Số trắng, **Robot**, **SciFi**; chỉnh cỡ chữ, kích thước icon, thứ tự các trang.
- **Bản đồ MapLibre:** bản đồ vector, xoay theo hướng xe, nghiêng 3D, cảnh báo giới hạn tốc độ và camera.
- **Dẫn đường:** tìm địa điểm, vẽ tuyến, chỉ dẫn rẽ bằng tiếng Việt, đọc bằng giọng nói, tự tính lại đường khi đi lệch.
- **Theo dõi OBD kiểu xe đua:** lưới mọi thông số xe báo hỗ trợ, giá trị đỉnh.
- **Nhạc:** hiện tên bài, ảnh bìa, điều khiển trình phát đang chạy.
- **Cập nhật trực tuyến:** kiểm tra và cài bản mới từ GitHub Releases ngay trong Cài đặt.

## Yêu cầu

- Android 9 trở lên (minSdk 28), thiết bị arm64-v8a.
- Kết nối OBD: adapter ELM327 Bluetooth, hoặc dùng Torque Pro làm nguồn dữ liệu.
- Cần mạng cho bản đồ, thời tiết, dẫn đường và cập nhật.

## Cài đặt

1. Tải file `.apk` mới nhất ở mục [Releases](https://github.com/PhungP/VL23-Launcher/releases).
2. Cài vào thiết bị (cho phép cài ứng dụng không rõ nguồn nếu được hỏi).
3. Mở Cài đặt của ứng dụng, đặt VL23 Launcher làm màn hình chính mặc định và cấp các quyền được liệt kê (vị trí, hiện trên ứng dụng khác, đọc nhạc đang phát).

## Cập nhật

Vào **Cài đặt > Cập nhật ứng dụng**. Ứng dụng so sánh phiên bản đang cài với bản mới nhất trên GitHub Releases, tải file `.apk` đính kèm và gọi trình cài đặt của Android.

Quy tắc phát hành:
- Tạo Release có tag lớn hơn bản đang cài (ví dụ `v0.11`) và đính kèm một file `.apk`.
- Mọi bản phải ký **cùng một khóa**, nếu không Android sẽ không cho cài đè.

## Dẫn đường và bản đồ

- Bản đồ vector dùng MapLibre với tile của MapTiler Cloud (dữ liệu OpenStreetMap). Nhập khóa API MapTiler miễn phí trong **Cài đặt > Bản đồ**, hoặc khai báo `MAPTILER_KEY` trong `gradle.properties` khi build. Không có khóa thì app dùng bản đồ OSM đơn giản và không có dẫn đường.
- Dẫn đường dùng Nominatim (tìm địa điểm) và máy chủ OSRM demo công cộng (tính đường). Cả hai chỉ phù hợp dùng cá nhân với lưu lượng nhỏ.
- Giọng nói dùng bộ đọc tiếng Việt có sẵn của máy; cần cài dữ liệu giọng tiếng Việt cho Text-to-speech.

## Phát triển

Mở bằng Android Studio và chạy cấu hình `app`. Không cần khóa MapTiler để build.

Để phát hành: tăng `versionCode` và `versionName` trong `app/build.gradle.kts`, build APK đã ký, rồi tạo Release trên GitHub với tag tương ứng.

## Ghi nguồn

- Dữ liệu bản đồ: © OpenStreetMap contributors; tile © MapTiler.
- Bản đồ vector: MapLibre Native.
- Thời tiết, tìm địa điểm và định tuyến dùng các dịch vụ công cộng; xem điều khoản của từng dịch vụ.

## Tác giả

Phan Kim Phụng
