# Kotlinx-Serialization: generierte Serializer behalten.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ch.schreibwerkstatt.mobile.data.net.dto.** {
    *** Companion;
}
-keepclassmembers class ch.schreibwerkstatt.mobile.data.net.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# GitHub-Release-DTOs der Update-Prüfung (UpdateChecker).
-keepclassmembers class ch.schreibwerkstatt.mobile.update.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# @JavascriptInterface-Methoden der Editor-Bridge dürfen nicht entfernt/umbenannt werden.
-keepclassmembers class ch.schreibwerkstatt.mobile.editor.EditorBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Tink (via androidx.security.crypto → TokenStore/EncryptedSharedPreferences) referenziert
# errorprone-Compile-Annotationen, die zur Laufzeit fehlen. Nur Warnungen unterdrücken.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
