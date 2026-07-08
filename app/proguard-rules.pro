# NeoLibEditor ProGuard Rules - Optimized for size

# Aggressive optimization
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 8
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames

# Remove logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Repackage and flatten
-repackageclasses Lk
-keepdirectories
-flattenpackagehierarchy Lk
-keeppackagenames

# Keep JNI bridge
-keep class com.neomods.libeditor.service.JniBridge { *; }
-keepclassmembers class com.neomods.libeditor.service.JniBridge {
    private external *;
}

# Keep serialization models
-keep class com.neomods.libeditor.model.** { *; }
-keepclassmembers class com.neomods.libeditor.model.** {
    *** Companion;
}
-keepclassmembers class com.neomods.libeditor.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep crash reporter (OkHttp + JSON)
-keep class com.neomods.libeditor.crash.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep annotations and inner classes
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep R$* fields
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep source file and line numbers for crash reports
-keepattributes Signature,Exceptions,Annotation,EnclosingMethod,InnerClasses,LineNumberTable,SourceFile

# Remove debug info in release (saves size)
-renamesourcefileattribute SourceFile

# Optimize Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Remove unused annotations
-dontwarn org.xmlpull.v1.**
-dontnote
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
