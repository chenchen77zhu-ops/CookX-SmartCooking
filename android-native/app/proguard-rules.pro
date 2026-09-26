# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.smartcooking.app.**$$serializer { *; }
-keepclassmembers class com.smartcooking.app.** { *** Companion; }
-keepclasseswithmembers class com.smartcooking.app.** { kotlinx.serialization.KSerializer serializer(...); }
# ONNX Runtime uses JNI reflection.
-keep class ai.onnxruntime.** { *; }
-dontwarn org.slf4j.**
