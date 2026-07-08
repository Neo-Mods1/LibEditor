# NeoLibEditor ProGuard Rules - Maximum size reduction

# Aggressive optimization
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 8
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames

# Remove all logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Repackage everything
-repackageclasses ''
-keepdirectories
-flattenpackagehierarchy ''
-keeppackagenames

# Keep JNI bridge only
-keep class com.neomods.libeditor.service.JniBridge { *; }
-keepclassmembers class com.neomods.libeditor.service.JniBridge {
    private external *;
}

# Keep serialization models (minimal)
-keep class com.neomods.libeditor.model.** { *; }

# Keep crash reporter (minimal)
-keep class com.neomods.libeditor.crash.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Compose essentials only
-keep class androidx.compose.runtime.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Remove debug info
-renamesourcefileattribute SourceFile
-keepattributes Signature,Annotation,EnclosingMethod,InnerClasses

# Remove unused
-dontwarn org.xmlpull.v1.**
-dontnote
-dontwarn javax.annotation.**
