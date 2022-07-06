-keepdirectories
# -dontpreverify
-dontoptimize
-dontshrink
-dontwarn

#kotlin
-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclasseswithmembers @kotlin.Metadata class * { *; }
-keepclassmembers class **.WhenMappings {
    <fields>;
}

-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }
-dontwarn kotlinx.**
-dontnote kotlinx.serialization.SerializationKt
# kotlin end

#Java 9+
#-libraryjars /Users/allan/Documents/jdk-16.jdk/Contents/Home/jmods/java.base.jmod(!.jar;!module-info.class)
#-libraryjars thirdLibs/*.jar

# Save meta-data for stack traces
-printmapping out.map
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Rename FXML files together with related views
#-adaptresourcefilenames **.fxml,**.png,**.css,**.properties
#-adaptresourcefilecontents **.fxml
#-adaptclassstrings

# Keep all annotations and meta-data
-keepattributes *Annotation*,Signature,EnclosingMethod

# 指定一个文本文件用来生成混淆后的名字。默认情况下，混淆后的名字一般为 a、b、c 这种。
# 通过使用配置的字典文件，可以使用一些非英文字符做为类名。成员变量名、方法名。字典文件中的空格，标点符号，重复的词，还有以'#'开头的行都会被忽略。
# 需要注意的是添加了字典并不会显著提高混淆的效果，只不过是更不利与人类的阅读。正常的编译器会自动处理他们，并且输出出来的jar包也可以轻易的换个字典再重新混淆一次。
# 最有用的做法一般是选择已经在类文件中存在的字符串做字典，这样可以稍微压缩包的体积。
# 查找了字典文件的格式：一行一个单词，空行忽略，重复忽略
#-obfuscationdictionary proguard_dict.pro
# 指定一个混淆类名的字典，字典格式与 -obfuscationdictionary 相同
#-classobfuscationdictionary proguard_dict.pro
# 指定一个混淆包名的字典，字典格式与 -obfuscationdictionary 相同
#-packageobfuscationdictionary proguard_dict.pro

# Keep entry-point class
#-keep class org.openfjx.MainApp {
#  public static void main(java.lang.String[]);
#}

# Keep names of fields marked with @FXML, @Inject and @PostConstruct attributes
-keepclassmembers class * {
  @javafx.fxml.FXML *;
  @javax.inject.Inject *;
  @javax.annotation.PostConstruct *;
}

-keepclassmembers class * {
    private static synthetic java.lang.Object $deserializeLambda$(java.lang.invoke.SerializedLambda);
}

-keepclassmembernames class * {
    private static synthetic *** lambda$*(...);
}

# mine app
-dontwarn java.awt.datatransfer.**

# 关键的。java9以上保留module-info
-keep class module-info
######
########## 关键的。javafx研究了很久基本上要求package目录不被混淆改变保留目录结构
######
-keeppackagenames com.allan.atools.**
#-keeppackagenames com.allan.atools.bean.**
#-keeppackagenames com.allan.atools.controller.**
#-keeppackagenames com.allan.atools.customui.**
#-keeppackagenames com.allan.atools.entro.**
#-keeppackagenames com.allan.atools.exception.**
#-keeppackagenames com.allan.atools.toolsstartup.**
#-keeppackagenames com.allan.atools.richtext**
#-keeppackagenames com.allan.atools.richtext.codearea**

-keepattributes StartupEntro

# 保留bean
-keep class com.allan.atools.ui.controls.** { *; }
-dontnote com.allan.atools.ui.controls.**

-keep class com.allan.atools.bean.** { *; }
-dontnote com.allan.atools.bean.**

#注解保留
-keep class com.allan.atools.toolsstartup.StartupEntro {*;}
#主入口
-keep public class com.allan.atools.toolsstartup.Startup {
    public static void main(java.lang.String[]);
}

-keep class com.allan.atools.controller.** {
    public protected *;
}
-dontnote com.allan.atools.controller.**

#-keep class com.allan.atools.entro.** {
#    public protected *;
#}

#-keep class com.allan.module.** { *; }
#-dontnote com.allan.module.**
#-keep class com.allan.richtext.** { *; }
#-dontnote com.allan.richtext.**

#-keep class com.allan.richtext.codearea.** { *; }
#-dontnote com.allan.richtext.codearea.**



