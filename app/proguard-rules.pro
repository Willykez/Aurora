# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.auroraai.chat.**$$serializer { *; }
-keepclassmembers class com.auroraai.chat.** { *** Companion; }
-keepclasseswithmembers class com.auroraai.chat.** { kotlinx.serialization.KSerializer serializer(...); }
