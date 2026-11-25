-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keep class com.google.android.gms.maps.MapView { *; }
-keep class com.google.android.gms.maps.GoogleMap { *; }
-keep class com.google.android.gms.maps.MapFragment { *; }
-keep class com.google.android.gms.maps.SupportMapFragment { *; }
-dontwarn com.google.android.gms.**
