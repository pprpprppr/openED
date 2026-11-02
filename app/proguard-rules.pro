# Proguard / R8 rules for openED — Global Personal AI University

# 1. Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.migration.Migration
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomOpenHelper { *; }
-dontwarn androidx.room.paging.**

# 2. Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

# 3. Google API Client, OAuth, and Google Drive
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn com.google.common.**

# 4. OkHttp & SSE
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 5. Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 6. Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-keep class androidx.compose.material3.** { *; }

# 7. AndroidX Keystore Security
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
