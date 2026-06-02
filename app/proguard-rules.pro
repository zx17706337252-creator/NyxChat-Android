# ─── Kotlin ───────────────────────────────────────────────────────────────────
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── Gson (data classes serialized to/from JSON) ──────────────────────────────
-keepattributes Signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep Gson TypeToken subclasses (anonymous object : TypeToken<T>() {} in StorageManager)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
# Keep all NyxChat data classes (SharedPreferences JSON via Gson)
# WARNING: every class stored with storage.save() / storage.load() MUST be listed here.
# R8 obfuscates field names → Gson can no longer match JSON keys → silent data loss on reinstall.
-keep class com.nyxchat.data.NyxCharacter         { *; }
-keep class com.nyxchat.data.NyxMessage           { *; }
-keep class com.nyxchat.data.NyxMemory            { *; }
-keep class com.nyxchat.data.WorldBookEntry       { *; }
-keep class com.nyxchat.data.ApiConfig            { *; }
-keep class com.nyxchat.data.TtsConfig            { *; }
-keep class com.nyxchat.data.Relationship         { *; }
-keep class com.nyxchat.data.ChatSession          { *; }
-keep class com.nyxchat.data.ProactiveConfig      { *; }
# UserPersona / SceneState / RelationshipLogEntry — also stored via Gson but were missing.
# Without these rules R8 renames their fields; JSON deserialization silently returns defaults.
-keep class com.nyxchat.data.UserPersona          { *; }
-keep class com.nyxchat.data.SceneState           { *; }
-keep class com.nyxchat.data.RelationshipLogEntry { *; }
# Enum rules: Gson serialises enum constants by name(); R8 renames them without -keep.
# MatchMode is stored inside WorldBookEntry; ReplyLength inside NyxCharacter.
# TtsProvider is stored inside TtsConfig via storage.save("tts_config_meta"); without this
# rule R8 renames e.g. "Azure" → "a" in release builds, causing Gson to return null on
# deserialization → NPE in when(config.provider) — same class of bug as the replyLength fix.
-keep enum  com.nyxchat.data.MatchMode            { *; }
-keep enum  com.nyxchat.data.ReplyLength          { *; }
-keep enum  com.nyxchat.data.TtsProvider          { *; }

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
