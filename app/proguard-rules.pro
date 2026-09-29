# Keep NanoHTTPD and all project classes
-keep class fi.iki.elonen.** { *; }
-keep class com.hadii.tvcasing.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.jetbrains.kotlinx.coroutines.** { *; }

# Kotlin
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-keepclassmembers class kotlin.Metadata { * }

# Coroutines — keep continuation and state machine classes
-keepclassmembers class * {
    @org.jetbrains.kotlinx.coroutines.internal.MainDispatcherFactory *;
}
-dontwarn org.jetbrains.kotlinx.coroutines.**

# BouncyCastle ASN.1 / cert builders referenced reflectively
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-dontwarn org.bouncycastle.**

# Compose — keep runtime + compiler-generated classes
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# JSON used by the WebSocket command channel
-keep class org.json.** { *; }
-dontwarn org.json.**

# Parcelable / Serializable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
