# Keep ThemeMode enum constant names intact for SharedPreferences persistence
-keep enum com.pinned.app.ThemeMode {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.pinned.app.ThemeMode {
    *;
}
