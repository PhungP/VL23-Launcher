# VL23 Launcher

Launcher Android dành cho màn hình đầu máy trên ô tô (thiết kế cho xe bán tải Nissan Navara NP300, màn hình ngang 800x480). Giao diện kiểu đồng hồ xe, hiển thị thông số OBD, bản đồ dẫn đường, nhạc đang phát và thời tiết ngay trên màn hình chính.

- **Nhà phát triển:** Phan Kim Phụng
- **Mã nguồn và bản phát hành:** https://github.com/PhungP/VL23-Launcher

## Tính năng

- **Hồ sơ xe:** chọn đúng dòng xe (hãng, model, năm) để tự đổi giao diện, ảnh xe, thông số OBD và PID, bảng tỉ số hộp số. Hiện có Nissan Navara 2023 VL, Honda Accord 2015 Thái Lan và hai hồ sơ chung.
- **Trang 1 (trang chủ):** hình xe ở giữa, 2 đến 10 ô thông số OBD (mặc định 6), thanh trên cùng (giờ, thời tiết, hướng đi, nhạc đang phát) và cấp số hiện hành.
- **Tùy chọn trang 1:**
  - Hình xe: ảnh Navara, ảnh riêng của bạn, khung dây hoặc bóng xe (vẽ vector).
  - Ô thông số: số ô từ 2 đến 10, mỗi ô chọn trong hơn 40 thông số (vòng tua, tải, nhiệt độ nước, boost, khí nạp, điện áp, chân ga, mô-men, công suất, EGT, lambda, áp suất rail, tốc độ và độ cao GPS, áp suất và nhiệt độ lốp TPMS...), có biểu tượng riêng.
  - Độ dày viền ô thông số (không viền, mảnh, vừa, dày, rất dày) và chiều cao ô (rất thấp đến đầy hàng).
  - Kiểu hiển thị kiểu Torque Pro: số, đồng hồ kim, đồng hồ cung, thanh, số điện tử 7 đoạn, đồ thị; chọn mặc định hoặc riêng từng ô.
  - Cấp số: **Số thể thao** (một chữ số to, nghiêng) hoặc **Ô cấp số**.
  - Màu chữ, màu viền hộp và màu số cấp chọn tự do trong 8 màu.
  - Sắp xếp kiểu Torque Pro (Cài đặt > Hình nền và khung > Sắp xếp các ô ở trang 1): kéo ô để đổi chỗ, kéo ô vuông ở góc để đổi cỡ, chạm ô để chọn thông số, kiểu hiển thị và cỡ riêng.
- **Giao diện:** ba bộ **Đồng hồ**, **Robot**, **SciFi**; chỉnh cỡ chữ, tỉ lệ giao diện, kích thước biểu tượng, thứ tự các trang. Tự co giãn theo màn hình 800×480, 1280×720, 1920×1080...
- **TPMS:** đọc cảm biến áp suất lốp mua ngoài qua Bluetooth (họ cảm biến nắp van TPMS1_...TPMS4_...), hiện áp suất và nhiệt độ từng lốp ở trang 1, cảnh báo khi ngoài ngưỡng.
- **Hình nền và khung:** hình nền đen, địa hình, chuyển màu hoặc ảnh riêng (không áp dụng cho trang 1); độ trong suốt khung 5 mức; hình khung vát góc, bo tròn, lục giác hoặc vuông.
- **Biểu tượng ứng dụng:** bộ biểu tượng Navara đồng nhất, biểu tượng gốc một màu trắng, hoặc xanh kết hợp trắng theo tông đồng hồ xe.
- **Trang thông số OBD:** lưới mọi thông số xe báo hỗ trợ, giá trị đỉnh, thanh báo sang số; chọn kiểu hiển thị (thẻ, số, kim, cung, thanh, số điện tử, đồ thị) và số cột 3 đến 6.
- **Bản đồ:** chọn MapLibre + MapTiler (cảnh báo giới hạn tốc độ và camera), OSM đơn giản hoặc **MapLibre + Goong** (người dùng nhập khoá Goong); xoay theo hướng xe, nghiêng 3D; nút khoá / mở khoá cảm ứng để kéo và zoom bản đồ bằng tay; thanh dock trong suốt.
- **Dẫn đường:** tìm địa điểm (có nút Dán địa chỉ hoặc liên kết từ Google Maps và nút sao chép địa chỉ), vẽ tuyến, chỉ dẫn rẽ bằng tiếng Việt, đọc bằng giọng nói, tự tính lại đường khi đi lệch.
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

### 0.21
- **Trợ lý Gemini** (người dùng tự nhập khoá API trong Cài đặt > Gemini AI): nút Gemini nổi trên mọi trang, kéo được và nhớ vị trí.
  - **Tìm đường bằng lời nói hoặc gõ chữ:** Gemini hiểu câu tự nhiên (ví dụ "cho tôi đường tới sân bay Cát Bi"), đổi thành chuỗi tìm địa chỉ, chạy qua dịch vụ tìm địa chỉ đang dùng (Goong hoặc Nominatim) rồi bạn chọn kết quả để dẫn đường.
  - **Hỏi về xe:** trả lời ngắn bằng tiếng Việt dựa trên số liệu OBD, TPMS và hồ sơ xe hiện tại.
  - **Mã lỗi:** đọc mã lỗi đang lưu trong xe (dịch vụ 03, không xoá mã, chỉ khi nối trực tiếp adapter) rồi nhờ Gemini giải thích ý nghĩa, nguyên nhân, mức độ; cũng gõ tay mã lỗi được.
  - **Chuyến đi:** ghi quãng đường, tốc độ, nhiên liệu ước tính, tăng tốc và phanh gắt, thời gian đứng yên, vòng tua cao; Gemini nhận xét và gợi ý cải thiện. Lưu lại sau khi tắt máy, tự sang chuyến mới nếu OBD im quá 20 phút.
  - Nói bằng micro của đầu xe (ghi âm tối đa 12 giây, gửi cho Gemini nghe); đọc to câu trả lời bằng giọng đọc của máy (tắt được).

### 0.20
- **Bỏ bản đồ Google Maps** (WebView). Cài đặt đã chọn Google Maps tự chuyển về MapLibre + MapTiler.
- **Trang 1:** chọn số ô (2 đến 10), độ dày viền ô, chiều cao ô, 6 kiểu hiển thị (số, kim, cung, thanh, số điện tử, đồ thị), đổi cỡ từng ô và chọn thông số, kiểu riêng cho từng ô bằng cách chạm ô khi sắp xếp. Thêm hơn 20 thông số OBD, tốc độ và độ cao GPS.
- **Trang OBD:** chọn kiểu hiển thị thông số (thẻ, số, kim, cung, thanh, số điện tử, đồ thị) và số cột; độ dày viền ô dùng chung với trang 1.
- **TPMS:** đọc cảm biến áp suất lốp Bluetooth mua ngoài; gán cảm biến cho bốn bánh, chọn đơn vị bar, psi hoặc kPa, ngưỡng cảnh báo, hiệu chỉnh áp suất; ô lốp trên trang 1 đổi màu khi ngoài ngưỡng.
- **Bản đồ:** nút khoá / mở khoá cảm ứng ngay trên bản đồ (mở khoá thì kéo, zoom hai ngón và dùng nút + / − để zoom, tắt vuốt đổi trang; nút định vị để về xe).
- **Tìm kiếm:** nút Dán nhận địa chỉ, toạ độ hoặc liên kết Google Maps (kể cả liên kết rút gọn maps.app.goo.gl) và dẫn thẳng tới đó khi đã có toạ độ; thêm nút sao chép địa chỉ ở mỗi kết quả.
- **Độ phân giải:** tỉ lệ giao diện chỉnh được (80% đến 140%), cột thông số chia theo bề rộng màn hình, bản đồ MapLibre dùng đúng mật độ điểm ảnh nên chữ trên bản đồ cùng cỡ ở 800×480, 1280×720 và 1920×1080.

### 0.19
- Thêm công cụ bản đồ **MapLibre + Goong** (Cài đặt > Bản đồ > Công cụ bản đồ): vẽ bản đồ bằng style Goong với ba kiểu Đường phố, Sáng, Dẫn đường (ngày/đêm).
- **Tìm địa chỉ bằng Goong Geocoding** với mọi công cụ bản đồ (kể cả MapLibre + MapTiler): nhập "API Key" của Goong, mỗi lần bấm Tìm tốn một lượt gọi, câu đã tìm được nhớ lại (30 câu gần nhất).
- Tuỳ chọn **Chỉ đường bằng Goong** (Goong Direction, chỉ dẫn tiếng Việt của Goong); mặc định tắt, vẫn dùng OSRM để không tốn hạn mức.
- Thông báo rõ khi Goong từ chối khoá hoặc hết hạn mức.

## Cập nhật

Vào **Cài đặt > Cập nhật ứng dụng**. Ứng dụng so sánh phiên bản đang cài với bản mới nhất trên GitHub Releases, tải file `.apk` đính kèm và gọi trình cài đặt của Android.

Quy tắc phát hành:
- Tạo Release có tag lớn hơn bản đang cài (ví dụ `v0.11`) và đính kèm một file `.apk`.
- Mọi bản phải ký **cùng một khóa**, nếu không Android sẽ không cho cài đè.

## Dẫn đường và bản đồ

- Bản đồ vector dùng MapLibre với tile của MapTiler Cloud (dữ liệu OpenStreetMap). Nhập khóa API MapTiler miễn phí trong **Cài đặt > Bản đồ**, hoặc khai báo `MAPTILER_KEY` trong `gradle.properties` khi build. Không có khóa thì app dùng bản đồ OSM đơn giản và không có dẫn đường.
- Dẫn đường dùng Nominatim (tìm địa điểm) và máy chủ OSRM demo công cộng (tính đường). Cả hai chỉ phù hợp dùng cá nhân với lưu lượng nhỏ.
- Giọng nói dùng bộ đọc tiếng Việt có sẵn của máy; cần cài dữ liệu giọng tiếng Việt cho Text-to-speech.

## Goong

Tạo khoá tại account.goong.io/keys; Goong có hai loại khoá riêng:
- **Maptiles Key:** chỉ dùng khi chọn công cụ bản đồ **MapLibre + Goong** (ô "Khoá Maptiles Goong"), để vẽ bản đồ.
- **API Key:** ô "Khoá API Goong" trong Cài đặt > Bản đồ, dùng được với mọi công cụ bản đồ. Có khoá này thì tìm địa chỉ bằng Goong Geocoding. Bật thêm "Chỉ đường bằng Goong" nếu muốn tính đường bằng Goong Direction; tắt thì tính đường bằng OSRM như cũ.

Khoá chỉ lưu trên máy, không đưa vào mã nguồn. Để đỡ tốn hạn mức: chỉ tìm khi bấm nút Tìm, câu đã tìm được nhớ lại, và chỉ đường mặc định không dùng Goong. Nếu bật chỉ đường bằng Goong, app tính lại đường tối đa mỗi 10 giây khi đi lệch, mỗi lần là một lượt gọi.

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
