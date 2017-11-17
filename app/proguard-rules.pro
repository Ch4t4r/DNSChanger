# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\Dev\AndroidSDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
# This dnsjava class uses old Sun API
-dontnote org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontwarn org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontwarn java.awt.*
-dontwarn org.slf4j.*

# See http://stackoverflow.com/questions/5701126, happens in dnsjava
-optimizations !code/allocation/variable
-keep class android.support.v7.widget.SearchView { *; }
-keepattributes SourceFile,LineNumberTable

-keep public class * extends com.frostnerd.utils.database.orm.Serializer
-keep public class * extends com.frostnerd.utils.database.orm.Entity
-keepclassmembers public class * extends com.frostnerd.utils.database.orm.Serializer {
   public <init>(...);
   private <fields>;
}

-keepclassmembers public class * extends com.frostnerd.utils.database.orm.Entity {
   public <init>(...);
   private <fields>;
}