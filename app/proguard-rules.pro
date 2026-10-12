# Xray-core / AndroidLibXrayLite JNI köprüsü isim değişikliğinden etkilenmesin
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Room entity/dao
-keep class com.ihvpn.app.data.db.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.ihvpn.app.**$$serializer { *; }
-keepclassmembers class com.ihvpn.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ihvpn.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
