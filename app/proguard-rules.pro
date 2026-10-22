# Strip all logging from release builds so nothing (tokens included) can reach logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Shrink and optimise, but keep readable class names: the code is open source, and
# scrambled names are one of the things antivirus heuristics flag in sideloaded apps.
-dontobfuscate
