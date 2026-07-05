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

# Ktor (uzaktan kumanda WS sunucusu — RC) + SLF4J (Ktor logger'ı; sağlayıcı yok, NOP)
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
