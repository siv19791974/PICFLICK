# ============================================
# PicFlick ProGuard/R8 Rules - Production
# ============================================

# Keep line numbers for debugging stack traces in Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================
# Jetpack Compose (preview/debug only)
# ============================================
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}

# ============================================
# Firebase - Firestore Data Classes
# ============================================
# Keep app data classes used with Firestore toObject()/fromObject()
-keep class com.picflick.app.data.** { *; }
-keepclassmembers class com.picflick.app.data.** { <init>(...); }

# Keep Firestore annotations on fields
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
}

# ============================================
# Firebase - Crashlytics
# ============================================
-keep public class * extends java.lang.Exception

# ============================================
# Credential Manager / Google Sign In
# ============================================
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ============================================
# Zoomable / ZoomImage
# ============================================
-keep class net.engawapg.lib.zoomable.** { *; }
-keep class me.saket.telephoto.** { *; }
-dontwarn net.engawapg.lib.zoomable.**
-dontwarn me.saket.telephoto.**

# ============================================
# Android-Image-Cropper (CanHub)
# ============================================
-keep class com.canhub.cropper.** { *; }
-dontwarn com.canhub.cropper.**

# ============================================
# Serializable / Parcelable
# ============================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================
# Generic: Keep enum values for Firestore deserialization
# ============================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# Generic: Keep no-arg constructors used by Firestore/Reflection
# ============================================
-keepclassmembers class * {
    public <init>(...);
}
