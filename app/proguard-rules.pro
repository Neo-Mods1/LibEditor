# NeoLibEditor ProGuard Rules

# Aggressive optimization
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 8
-allowaccessmodification
-mergeinterfacesaggressively
-allowaccessmodification

# Remove logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Obfuscate
-repackageclasses com.neomods.libeditor.obfuscated
-allowaccessmodification

# Keep JNI bridge (native method names must match)
-keep class com.neomods.libeditor.service.JniBridge { *; }
-keepclassmembers class com.neomods.libeditor.service.JniBridge {
    private external *;
}

# Keep model classes for serialization (kotlinx.serialization)
-keep class com.neomods.libeditor.model.** { *; }
-keepclassmembers class com.neomods.libeditor.model.** {
    *** Companion;
}
-keepclassmembers class com.neomods.libeditor.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Compose - keep only what's needed
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.node.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Okio (transitive from Lottie)
-dontwarn okio.**
-keep class okio.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Remove unused resources
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Aggressive dead code removal
-repackageclasses
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

# Strip annotations
-keepattributes Signature,Exceptions,Annotation,EnclosingMethod,InnerClasses,LineNumberTable,SourceFile

# File exclusion
-dontwarn org.xmlpull.v1.**
-dontnote
