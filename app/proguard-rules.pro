# ProGuard / R8 rules for Tuesday.sh (sh.tuesday.app)

# Keep source file names and line numbers for readable stack traces in Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep kotlinx.serialization @Serializable classes and generated $serializer companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class sh.tuesday.app.**$$serializer { *; }
-keepclassmembers class sh.tuesday.app.** {
    *** Companion;
}
-keepclasseswithmembers class sh.tuesday.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all data / model / API DTO classes intact
-keep class sh.tuesday.app.data.** { *; }
-keep class sh.tuesday.app.model.** { *; }
-keep class sh.tuesday.app.services.** { *; }

# OkHttp3 & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
