# SortMate release rules.
# Firebase, Room, CameraX and ML Kit publish their own consumer ProGuard rules.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep enum values used by Firestore/Room serialization.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
