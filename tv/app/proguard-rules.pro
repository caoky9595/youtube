# kotlinx.serialization: giu serializer sinh tu @Serializable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.youtube.tv.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.youtube.tv.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# android-youtube-player chay JS trong WebView qua @JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
