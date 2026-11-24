# JNA finds its native callbacks and the bindings' structures by reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
# The UniFFI bindings: JNA reads their structure fields and callback methods
# by name.
-keep class uniffi.spectra_core.** { *; }
