# Keep Room entities and DAOs (reflection / codegen).
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# Kotlin metadata / coroutines
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# DataStore / protobuf
-dontwarn com.google.protobuf.**

# Listener transcription: Vosk talks to libvosk through JNA (reflection + native callbacks).
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
