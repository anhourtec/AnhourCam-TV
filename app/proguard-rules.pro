# kotlinx.serialization: keep generated serializers of the client's @Serializable classes.
-keepclassmembers class com.anhourtec.camgrid.client.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.anhourtec.camgrid.client.**$$serializer { *; }
