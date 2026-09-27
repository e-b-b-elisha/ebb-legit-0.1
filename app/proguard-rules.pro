# --- Anthropic Java SDK / Jackson -------------------------------------------
# The SDK serializes request bodies and deserializes responses reflectively.
-keep class com.anthropic.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepnames class com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.module.kotlin.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**

# --- OkHttp / Okio ----------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin metadata used by Jackson's Kotlin module ------------------------
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**

# --- Desugared / JVM-only APIs the SDK references but Android never loads ---
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
