# MapLibre Native gọi qua JNI nên phải giữ nguyên tên lớp
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
