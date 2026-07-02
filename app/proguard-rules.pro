# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep,includedescriptorclasses class com.ozgen.navicloud.**$$serializer { *; }
-keepclassmembers class com.ozgen.navicloud.** {
    *** Companion;
}
-keepclasseswithmembers class com.ozgen.navicloud.** {
    kotlinx.serialization.KSerializer serializer(...);
}
