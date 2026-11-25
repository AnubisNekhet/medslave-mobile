# zstd-jni loads its classes from native code
-keep class com.github.luben.zstd.** { *; }
# the card WebView calls these by name
-keepclassmembers class pizza.kulhad.medslave.CardBridge { @android.webkit.JavascriptInterface <methods>; }
