# NeoLibEditor ProGuard Rules

# Keep JNI bridge
-keep class com.neomods.libeditor.service.JniBridge { *; }
-keepclassmembers class com.neomods.libeditor.service.JniBridge { *; }

# Keep model classes for serialization
-keep class com.neomods.libeditor.model.** { *; }
-keepclassmembers class com.neomods.libeditor.model.** { *; }

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Compose
-keep class androidx.compose.** { *; }

# Optimization
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Obfuscate strings
-repackageclasses com.neomods.libeditor.obfuscated
-allowaccessmodification
