# ============================================================
# HiRobin — ProGuard / R8 rules
# ============================================================

# ---------- Telecom / ConnectionService ----------
# R8 can strip ConnectionService subclasses because they are
# instantiated by the Telecom framework via reflection, not by
# app code directly.
-keep class com.yourcompany.hirobin.services.** { *; }

# Keep the Connection subclass — Telecom also reflects into it.
-keep class com.yourcompany.hirobin.services.HiRobinConnection { *; }

# ---------- Flutter embedding ----------
# Flutter locates the FlutterActivity subclass by name at runtime.
-keep class com.yourcompany.hirobin.MainActivity { *; }

# ---------- CallEventBus ----------
# Kept as a Kotlin object; its methods are called from the
# ConnectionService and from MethodChannel handlers at runtime.
-keep class com.yourcompany.hirobin.CallEventBus { *; }
-keepclassmembers class com.yourcompany.hirobin.CallEventBus {
    public static ** INSTANCE;
    public *;
}

# ---------- MethodChannel argument types ----------
# Flutter's MethodChannel serialises/deserialises Map<String, Any>
# across the platform boundary. Keep Map implementations that
# R8 might otherwise inline or rename.
-keepclassmembers class * implements java.util.Map {
    public *;
}

# ---------- Kotlin ----------
# Keep Kotlin metadata so reflection-based libraries (e.g. coroutines
# internals, kotlinx.serialization if added later) can inspect types.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }

# Coroutines — keep internal debug infrastructure in debug builds only
# (R8 removes it automatically in release; this silences the warning).
-dontwarn kotlinx.coroutines.debug.**

# ---------- OkHttp / Okio ----------
# WebSocketListener subclasses are referenced by name internally by OkHttp.
-keep class okhttp3.internal.** { *; }
-keep class okhttp3.WebSocketListener { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- AndroidX Lifecycle ----------
# LifecycleService uses @OnLifecycleEvent annotations internally.
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <methods>;
}
