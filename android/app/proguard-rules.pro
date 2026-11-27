# Keep Gson-serialized DTOs (field names are part of the server contract).
-keep class app.santals.speaks.data.remote.dto.** { <fields>; }
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit / OkHttp recommended rules.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Exceptions

# Media3 ships its own consumer rules.
