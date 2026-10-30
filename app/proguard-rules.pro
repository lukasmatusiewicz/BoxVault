# BoxVault ProGuard & R8 Rules

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Entities, DAOs, and Query POJOs
-keep class com.boxvault.app.data.local.entity.** { *; }
-keep class com.boxvault.app.data.local.model.** { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase { *; }

# Bundled ML Kit Barcode Scanning & JNI Barhopper models
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep interface com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.photos.vision.barhopper.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# ZXing Core
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
