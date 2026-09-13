# Left — Smart Expense Tracker ProGuard rules.
# Minification is currently disabled for release builds (see app/build.gradle.kts).
# These rules exist so enabling R8 later is a configuration change, not a new task.

# Room: keep entity/DAO metadata discoverable via reflection where Room requires it.
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Domain models are constructed reflectively by Room only at the entity layer;
# domain-layer classes are referenced directly and need no keep rules.

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
