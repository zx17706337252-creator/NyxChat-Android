# ─── Kotlin ───────────────────────────────────────────────────────────────────
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── Gson (data classes serialized to/from JSON) ──────────────────────────────
-keepattributes Signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep all NyxChat data classes (SharedPreferences JSON)
-keep class com.nyxchat.data.NyxCharacter    { *; }
-keep class com.nyxchat.data.NyxMessage      { *; }
-keep class com.nyxchat.data.NyxMemory       { *; }
-keep class com.nyxchat.data.WorldBookEntry  { *; }
-keep class com.nyxchat.data.KnowledgeEntry  { *; }
-keep class com.nyxchat.data.ApiConfig       { *; }
-keep class com.nyxchat.data.TtsConfig       { *; }
-keep class com.nyxchat.data.Relationship    { *; }
-keep class com.nyxchat.data.ChatSession     { *; }
-keep class com.nyxchat.data.ProactiveConfig { *; }
-keep enum  com.nyxchat.data.MatchMode       { *; }

# ─── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *             { *; }
-keep @androidx.room.Dao    interface *         { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# ─── OkHttp + Okio ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ─── WorkManager ──────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── Coroutines ───────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ─── Compose ──────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ─── NyxChat notification workers ────────────────────────────────────────────
-keep class com.nyxchat.notification.** { *; }
-keep class com.nyxchat.pipeline.**    { *; }
-keep class com.nyxchat.services.**    { *; }
