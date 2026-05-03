# ============================================
# PicFlick ProGuard/R8 Rules - Production
# ============================================

# Keep line numbers for debugging stack traces in Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# Kotlin & Coroutines
# ============================================
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================
# Jetpack Compose
# ============================================
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
-dontwarn androidx.compose.**

# ============================================
# Firebase - Auth
# ============================================
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase - Firestore
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
}
-keepattributes *Annotation*
-keepattributes Signature
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep Firestore data classes used with toObject()/fromObject()
-keep class com.picflick.app.data.** { *; }
-keepclassmembers class com.picflick.app.data.** { <init>(...); }

# ============================================
# Firebase - Storage
# ============================================
-keep class com.google.firebase.storage.** { *; }

# ============================================
# Firebase - Messaging
# ============================================
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-dontwarn com.google.firebase.iid.**

# ============================================
# Firebase - Functions
# ============================================
-keep class com.google.firebase.functions.** { *; }

# ============================================
# Firebase - Crashlytics
# ============================================
-keep class com.google.firebase.crashlytics.** { *; }
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ============================================
# Firebase - Analytics
# ============================================
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }

# ============================================
# Google Play Services / Auth
# ============================================
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.api.client.** { *; }

# ============================================
# Credential Manager / Google Sign In
# ============================================
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ============================================
# Coil Image Loading
# ============================================
-keep class coil3.** { *; }
-keep class coil3.compose.** { *; }
-keep class coil3.network.** { *; }
-dontwarn coil3.**

# ============================================
# Zoomable / ZoomImage
# ============================================
-keep class net.engawapg.lib.zoomable.** { *; }
-keep class me.saket.telephoto.** { *; }
-dontwarn net.engawapg.lib.zoomable.**
-dontwarn me.saket.telephoto.**

# ============================================
# Navigation Compose
# ============================================
-keep class androidx.navigation.** { *; }
-keep class * implements androidx.navigation.Navigator { *; }
-keepclassmembers class * {
    @androidx.navigation.ComposeNavigator.Destination <methods>;
}

# ============================================
# Google Play Billing
# ============================================
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# ============================================
# Play In-App Review
# ============================================
-keep class com.google.android.play.core.** { *; }
-keep class com.google.android.play.core.review.** { *; }
-dontwarn com.google.android.play.core.**

# ============================================
# Android-Image-Cropper (CanHub)
# ============================================
-keep class com.canhub.cropper.** { *; }
-dontwarn com.canhub.cropper.**

# ============================================
# AndroidX / Lifecycle / ViewModel
# ============================================
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }

# ============================================
# ExifInterface
# ============================================
-keep class androidx.exifinterface.** { *; }

# ============================================
# SplashScreen
# ============================================
-keep class androidx.core.splashscreen.** { *; }

# ============================================
# Serializable / Parcelable (if used)
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
# OkHttp (used by Coil network)
# ============================================
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ============================================
# Generic: Keep enum values for Firestore deserialization
# ============================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# Generic: Keep constructors used by Firestore/Reflection
# ============================================
-keepclassmembers class * {
    public <init>(...);
}
