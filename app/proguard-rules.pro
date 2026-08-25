# Keep kotlinx.serialization models used for Retrofit/JSON parsing.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.firmlyplanted.app.**$$serializer { *; }
-keepclassmembers class com.firmlyplanted.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.firmlyplanted.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
