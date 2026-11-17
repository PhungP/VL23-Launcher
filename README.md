# VL23 Launcher

Launcher Android dành cho màn hình đầu máy trên ô tô (thiết kế cho xe bán tải Nissan Navara NP300, màn hình ngang 800x480). Giao diện kiểu đồng hồ xe, hiển thị thông số OBD, bản đồ dẫn đường, nhạc đang phát và thời tiết ngay trên màn hình chính.

- **Nhà phát triển:** Phan Kim Phụng
- **Mã nguồn và bản phát hành:** https://github.com/PhungP/VL23-Launcher

## Tính năng

- **Hồ sơ xe:** chọn đúng dòng xe (hãng, model, năm) để tự đổi giao diện, ảnh xe, thông số OBD và PID, bảng tỉ số hộp số. Hiện có Nissan Navara 2023 VL, Honda Accord 2015 Thái Lan và hai hồ sơ chung.
- **Trang 1 (trang chủ):** hình xe ở giữa, 6 ô thông số OBD, thanh trên cùng (giờ, thời tiết, hướng đi, nhạc đang phát) và cấp số hiện hành.
- **Tùy chọn trang 1:**
  - Hình xe: ảnh Navara, ảnh riêng của bạn, khung dây hoặc bóng xe (vẽ vector).
  - 6 ô thông số: mỗi ô chọn trong 19 thông số (vòng tua, tải động cơ, nhiệt độ nước, boost, khí nạp, điện áp, chân ga, mô-men, công suất, EGT...), có biểu tượng riêng.
  - Cấp số: **Số thể thao** (một chữ số to, nghiêng) hoặc **Ô cấp số**.
  - Màu chữ, màu viền hộp và màu số cấp chọn tự do trong 8 màu.
  - Kéo từng ô tới vị trí mong muốn (Cài đặt > Hình nền và khung > Di chuyển các ô ở trang 1).
- **Giao diện:** ba bộ **Đồng hồ**, **Robot**, **SciFi**; chỉnh cỡ chữ, kích thước biểu tượng, thứ tự các trang.
- **Hình nền và khung:** hình nền đen, địa hình, chuyển màu hoặc ảnh riêng (không áp dụng cho trang 1); độ trong suốt khung 5 mức; hình khung vát góc, bo tròn, lục giác hoặc vuông.
- **Biểu tượng ứng dụng:** bộ biểu tượng Navara đồng nhất, biểu tượng gốc một màu trắng, hoặc xanh kết hợp trắng theo tông đồng hồ xe.
- **Trang thông số OBD:** lưới mọi thông số xe báo hỗ trợ, giá trị đỉnh, thanh báo sang số.
- **Bản đồ:** chọn MapLibre + MapTiler (cảnh báo giới hạn tốc độ và camera), OSM đơn giản, **Google Maps** (khoá API do người dùng nhập, có lớp giao thông) hoặc **MapLibre + Goong** (người dùng nhập khoá Goong); xoay theo hướng xe, nghiêng 3D; thanh dock trong suốt.
- **Dẫn đường:** tìm địa điểm, vẽ tuyến, chỉ dẫn rẽ bằng tiếng Việt, đọc bằng giọng nói, tự tính lại đường khi đi lệch.
- **Nhạc:** tên bài, ảnh bìa, thông số âm thanh, hiệu ứng phổ và điều khiển trình phát đang chạy.
- **Cập nhật trực tuyến:** kiểm tra và cài bản mới từ GitHub Releases ngay trong Cài đặt.

## Yêu cầu

- Android 9 trở lên (minSdk 28), thiết bị arm64-v8a.
- Kết nối OBD: adapter ELM327 Bluetooth, hoặc dùng Torque Pro làm nguồn dữ liệu.
- Cần mạng cho bản đồ, thời tiết, dẫn đường và cập nhật.

## Cài đặt

1. Tải file `.apk` mới nhất ở mục [Releases](https://github.com/PhungP/VL23-Launcher/releases).
2. Cài vào thiết bị (cho phép cài ứng dụng không rõ nguồn nếu được hỏi).
3. Mở Cài đặt của ứng dụng, đặt VL23 Launcher làm màn hình chính mặc định và cấp các quyền được liệt kê (vị trí, hiện trên ứng dụng khác, đọc nhạc đang phát).

## Lịch sử thay đổi

### 0.9
- Đổi tên ứng dụng thành VL23 Launcher.
- Cập nhật ứng dụng trực tuyến từ GitHub Releases.
- Thêm mục Giới thiệu: nhà phát triển Phan Kim Phụng và link GitHub.
- Dẫn đường trên bản đồ MapLibre: tìm địa điểm, vẽ tuyến, chỉ dẫn tiếng Việt, đọc bằng giọng nói, tự tính lại đường.

### 0.10
- Thêm hai bộ giao diện Robot và SciFi.
- Chọn hình xe ở trang 1: ảnh Navara, ảnh riêng, khung dây, bóng xe.
- Chọn 6 thông số OBD hiển thị ở trang 1 trong 19 thông số.
- Chọn cách hiển thị cấp số hiện hành ở trang 1.

### 0.11
- Cấp số trang 1 hiện một chữ số to, nghiêng kiểu thể thao; bỏ thanh hiển thị số đi.
- Nút đóng ở trang Cài đặt luôn nổi.
- Thêm biểu tượng cho từng ô thông số OBD ở trang 1.

### 0.12
- Biểu tượng vẽ riêng cho vòng tua, tải động cơ, turbo, nhiệt độ khí nạp và điện áp ắc quy.
- Biểu tượng nằm góc trái ô, số nằm giữa; ô rộng hơn, cỡ số lớn hơn.
- Bỏ vạch ngăn cấp số ở chân trang 1; ảnh xe xuống sát mép dưới màn hình.
- Biểu tượng ứng dụng có thêm kiểu xanh kết hợp trắng theo tông đồng hồ xe.

### 0.13
- Khung và ô trong suốt ở trang 1, Ứng dụng, thông số OBD, nhạc; chỉnh được độ trong suốt (5 mức).
- Thêm lựa chọn hình nền (đen trơn, địa hình, chuyển màu, ảnh riêng) và độ tối ảnh nền.
- Chọn hình khung thông số: vát góc, bo tròn, lục giác, vuông.
- Kéo di chuyển các ô ở trang 1 (6 ô thông số, thanh trên cùng, cấp số), có nút Đặt lại.

### 0.14
- Bỏ hai bộ giao diện Số đen và Số trắng; còn Đồng hồ, Robot, SciFi.
- Cấp số trang 1 chỉ còn hai kiểu: Số thể thao và Ô cấp số.
- Thanh dock ở trang Bản đồ trong suốt, nhìn thấy bản đồ phía sau.
- Hình nền tuỳ chọn không áp dụng cho trang 1 (trang 1 có nền và ảnh xe riêng).
- Viền menu trong Cài đặt mảnh hơn, đúng 1 pixel, sắc nét.

### 0.15
- Thêm **Hồ sơ xe** (Cài đặt > Hồ sơ xe): mỗi dòng xe có giao diện, ảnh xe, thông số OBD, danh sách PID và bảng tỉ số hộp số riêng.
- Hồ sơ có sẵn: Nissan Navara 2023 VL (diesel 2.3L tăng áp kép, 7AT, giao diện Đồng hồ cam và xanh), Honda Accord 2015 Thái Lan (xăng 2.4L hút khí tự nhiên, 5AT, giao diện SciFi), cùng hai hồ sơ chung cho xe xăng và xe dầu.
- Trang 1 và trang OBD chỉ hiện thông số phù hợp với xe: xe hút khí tự nhiên không có boost và EGT; xe xăng có thêm hiệu chỉnh nhiên liệu, nhiệt độ xúc tác, lambda, đánh lửa sớm.
- Thang đồng hồ vòng tua và dải đèn vòng tua đổi theo xe (Navara vùng đỏ từ 4500, Accord từ 6700).
- Cấp số được ước lượng theo bảng tỉ số của từng hồ sơ; lưu ý bảng của Accord là ước tính.
- Thêm ảnh xe Honda Accord cho trang 1.

### 0.16
- Thêm công cụ bản đồ **Google Maps** (Cài đặt > Bản đồ): người dùng nhập khoá API của mình, có lớp giao thông, xoay theo hướng xe, nghiêng 3D và dẫn đường vẽ lên bản đồ Google. Chạy trong WebView nên không cần Google Play Services.
- Hồ sơ xe có nhiều ảnh để chọn ở trang 1: Honda Accord (Đêm khói, Sa mạc, Núi bùn, Studio) và Nissan Navara (Mưa đêm, Sa mạc, Studio).
- Ảnh phong cảnh phủ kín nền trang 1, có lớp tối nhẹ để chữ và ô thông số dễ đọc.

### 0.17
- Trang 1: chọn màu chữ, màu viền các hộp và màu số cấp trong 8 màu (hoặc theo giao diện). Áp dụng cho thanh trên cùng, 6 ô thông số và cấp số.

### 0.18
- Google Maps: hiện thông báo "Đang tải" và nêu rõ lỗi do Google báo (ví dụ khoá sai loại, chưa bật Maps JavaScript API) thay vì để màn hình đen.

### 0.19
- Thêm công cụ bản đồ **MapLibre + Goong** (Cài đặt > Bản đồ > Công cụ bản đồ): vẽ bản đồ bằng style của Goong, tìm địa điểm bằng Goong Place và chỉ đường bằng Goong Direction, chỉ dẫn tiếng Việt của Goong. Người dùng tự nhập khoá Maptiles và khoá API của Goong.
- Ba kiểu bản đồ Goong: Đường phố, Sáng, Dẫn đường (ngày/đêm). Chỉ nhập khoá Maptiles thì dẫn đường tạm dùng Nominatim + OSRM.
- Thông báo rõ khi Goong từ chối khoá hoặc hết hạn mức.

## Cập nhật

Vào **Cài đặt > Cập nhật ứng dụng**. Ứng dụng so sánh phiên bản đang cài với bản mới nhất trên GitHub Releases, tải file `.apk` đính kèm và gọi trình cài đặt của Android.

Quy tắc phát hành:
- Tạo Release có tag lớn hơn bản đang cài (ví dụ `v0.11`) và đính kèm một file `.apk`.
- Mọi bản phải ký **cùng một khóa**, nếu không Android sẽ không cho cài đè.

## Google Maps

Chọn **Cài đặt > Bản đồ > Công cụ bản đồ > Google Maps** rồi dán khoá API vào ô nhập. Khoá chỉ lưu trên máy, không đưa vào mã nguồn.

Tạo khoá tại console.cloud.google.com:
1. Bật **Maps JavaScript API** (không phải Maps SDK for Android, hai API này tách biệt) và bật thanh toán cho dự án (Google tính phí theo lượt tải bản đồ).
2. Đặt giới hạn ứng dụng cho khoá là **Websites** với `https://localhost/*`, hoặc không giới hạn. Khoá giới hạn "Android apps" không dùng được vì bản đồ chạy trong WebView.
3. Giới hạn API của khoá chỉ gồm Maps JavaScript API.

Dữ liệu tìm địa điểm và tuyến đường vẫn lấy từ Nominatim và OSRM, được vẽ đè lên bản đồ Google.

## Dẫn đường và bản đồ

- Bản đồ vector dùng MapLibre với tile của MapTiler Cloud (dữ liệu OpenStreetMap). Nhập khóa API MapTiler miễn phí trong **Cài đặt > Bản đồ**, hoặc khai báo `MAPTILER_KEY` trong `gradle.properties` khi build. Không có khóa thì app dùng bản đồ OSM đơn giản và không có dẫn đường.
- Dẫn đường dùng Nominatim (tìm địa điểm) và máy chủ OSRM demo công cộng (tính đường). Cả hai chỉ phù hợp dùng cá nhân với lưu lượng nhỏ.
- Giọng nói dùng bộ đọc tiếng Việt có sẵn của máy; cần cài dữ liệu giọng tiếng Việt cho Text-to-speech.

## Goong

Chọn **Cài đặt > Bản đồ > Công cụ bản đồ > MapLibre + Goong**. Tạo khoá tại account.goong.io/keys; Goong có hai loại khoá riêng:
- **Maptiles Key:** dán vào ô "Khoá Maptiles Goong", dùng để vẽ bản đồ.
- **API Key:** dán vào ô "Khoá API Goong", dùng để tìm địa điểm (Place AutoComplete và Place Detail) và tính đường (Direction).

Khoá chỉ lưu trên máy, không đưa vào mã nguồn. Không nhập khoá API thì bản đồ vẫn hiện và dẫn đường dùng Nominatim + OSRM như cũ. Hạn mức gọi API tuỳ gói của tài khoản Goong; app tính lại đường tối đa mỗi 10 giây khi đi lệch.

## Thêm hồ sơ xe mới

Mở `app/src/main/java/vn/navara/launcher/Vehicles.kt`, thêm một `VehicleProfile` (hãng, model, năm, động cơ, hộp số, tỉ số truyền, cầu cuối, chu vi lốp, bộ giao diện, 6 thông số trang 1, thông số ẩn hoặc thêm ở trang OBD, PID bỏ qua, thang vòng tua) rồi đưa vào danh sách `all`. Ảnh xe đặt trong `res/drawable-nodpi/`.

## Phát triển

Mở bằng Android Studio và chạy cấu hình `app`. Không cần khóa MapTiler để build.

Để phát hành: tăng `versionCode` và `versionName` trong `app/build.gradle.kts`, build APK đã ký, rồi tạo Release trên GitHub với tag tương ứng.

## Ghi nguồn

- Dữ liệu bản đồ: © OpenStreetMap contributors; tile © MapTiler.
- Bản đồ vector: MapLibre Native.
- Thời tiết, tìm địa điểm và định tuyến dùng các dịch vụ công cộng; xem điều khoản của từng dịch vụ.

## Tác giả

Phan Kim Phụng
