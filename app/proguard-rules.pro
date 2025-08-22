# Keep Hilt/Dagger generated code and annotations
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclassmembers class ** {
    @dagger.hilt.android.internal.lifecycle.HiltViewModel *;
}

# Keep native-loader class names
-keep class com.app.whisper.native.** { *; }

