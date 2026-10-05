# Room, Hilt, Compose and WorkManager ship their own consumer rules.
# Keep stack-trace line numbers readable for crash reports; strip the file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep the generated serializers of the backup format (read by reflection-free code, but R8 must not strip
# the Companion.serializer() entry points).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.yasin.vcardly.domain.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.yasin.vcardly.domain.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
