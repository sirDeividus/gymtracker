# Room y kotlinx.serialization incluyen sus propias reglas de consumidor.
# El módulo de dominio usa @Serializable; conservamos los serializadores generados.
-keepclassmembers class com.gymtrack.domain.** { *** Companion; }
-keepclasseswithmembers class com.gymtrack.domain.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.gymtrack.domain.**$$serializer { *; }
