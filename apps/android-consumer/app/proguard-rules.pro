# kotlinx.serialization (SPEC §10.1)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# ML Kit barcode (bundled model)
-keep class com.google.mlkit.** { *; }
