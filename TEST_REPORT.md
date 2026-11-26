# Báo cáo kiểm thử (chưa phát hành, sau 0.29)

Cập nhật lần cuối: sau thêm quản lý hồ sơ xe.

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| Build Debug | PASS | |
| Build Release | xem RELEASE_NOTES / lần chạy cuối | Gradle không còn ký bản release; dùng `./release.sh` hoặc `keystore.properties` |
| Unit tests (JVM) | PASS 110/110 | DataQuality, AlertEngine, DriveMode, ObdStats, Updater, hồ sơ xe (validation, repository, nhập/xuất/sao lưu, tích hợp) |
| Emulator (API 37, màn 2400x1080) | PASS một phần | App mở không crash; mở Cài đặt > Quản lý hồ sơ; nhân bản hồ sơ Honda; tệp `vehicle_profiles.json` được ghi đúng; khởi động lại app không lỗi |
| Chọn hồ sơ người dùng, sửa, xoá, nhập/xuất/khôi phục trên giao diện | NOT TESTED | Chỉ được kiểm thử bằng unit test ở tầng dữ liệu |
| Chia sẻ / lưu tệp bằng SAF trên thiết bị | NOT TESTED | |
| OBD thật (giao thức cố định, PID ưu tiên), GPS, TPMS, màn hình xe | NOT TESTED | Không có phần cứng |
| Nâng cấp từ 0.28 giữ nguyên cấu hình | NOT TESTED | Chỉ suy ra từ thiết kế (mã hồ sơ dựng sẵn và khoá `vehicle` không đổi) |

## Giới hạn đã biết
- Cảnh báo đang bật được giữ khi dữ liệu mất tin cậy cho tới khi điều kiện hết hoặc đổi xe.
- Giao diện mới chưa được duyệt trên màn hình ô tô thật (kích thước chữ, vùng chạm).
