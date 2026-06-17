-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 8
-allowaccessmodification
-mergeinterfacesaggressively
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-repackageclasses Lk
-keepdirectories
-overloadaggressively
-useuniqueclassmembernames
-flattenpackagehierarchy Lk
-keeppackagenames
-keep class com.neomods.libeditor.service.JniBridge { *; }
-keepclassmembers class com.neomods.libeditor.service.JniBridge {
    private external *;
}
-keep class com.neomods.libeditor.model.** { *; }
-keepclassmembers class com.neomods.libeditor.model.** {
    *** Companion;
}
-keepclassmembers class com.neomods.libeditor.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn okio.**
-keep class okio.** { *; }
-keep class androidx.datastore.** { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}
-keepattributes Signature,Exceptions,Annotation,EnclosingMethod,InnerClasses,LineNumberTable,SourceFile
-dontwarn org.xmlpull.v1.**
-dontnote