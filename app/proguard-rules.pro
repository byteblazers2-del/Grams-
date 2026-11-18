# Strip all debug/info/verbose/warn/error Android logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Mask source file names while preserving stack line numbers
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-renamesourcefileattribute SourceFile

# Room persistence & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.core.data.*_Impl { *; }
-keepclassmembers class com.example.core.data.* {
    <fields>;
    <init>(...);
}

# org.ton.kotlin TL-B / cryptography
-keep class org.ton.** { *; }
-dontwarn org.ton.**

# OkHttp / Okio / kotlinx-coroutines optional runtime references
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
