-keepdirectories
# -dontpreverify
-dontoptimize
-dontshrink
-dontwarn

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
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

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

# Keep names of fields marked with @FXML attributes
-keepclassmembers class * {
  @javafx.fxml.FXML *;
}

# mine app
-dontwarn java.awt.datatransfer.**

# 关键的。java9以上保留module-info
-keep class module-info
######
########## JPMS 模块化下，只有 module-info.java 中被 opens/exports 的包，其包名必须保持
##########（FXMLLoader 反射 setAccessible、Gson 反射都要按 opens 条目的包名命中，包名一变则 InaccessibleObjectException）。
########## 注意按字面包名保留（不带 .**），使各包的子包名也能参与混淆。
######
-keeppackagenames com.allan.atools.controller
-keeppackagenames com.allan.atools.tools
-keeppackagenames com.allan.atools.toolsstartup
-keeppackagenames com.allan.atools.richtext
-keeppackagenames com.allan.atools.ui.controls
-keeppackagenames com.allan.atools.bean

-keepattributes StartupEntro

# 保留bean
-keep class com.allan.atools.ui.controls.** { *; }
-dontnote com.allan.atools.ui.controls.**

-keep class com.allan.atools.bean.** { *; }
-dontnote com.allan.atools.bean.**

#注解保留
-keep class com.allan.atools.toolsstartup.StartupEntro {*;}
#主入口
-keep public class com.allan.atools.toolsstartup.ATools {
    public static void main(java.lang.String[]);
}

# Controller 类名已通过 setController() 方式摆脱 fx:controller 字符串依赖，类名可混淆；
# 但 fx:id 按字段名注入（字段是裸 public、无 @FXML），成员名须由下方 AbstractController 规则 keep。
# tools 包同理，无 FXML 字符串类名引用。
-dontnote com.allan.atools.controller.**
-dontnote com.allan.atools.tools.**

# fx:id 按字段名注入，public/protected 成员名必须 keep，否则注入静默失败导致运行期 NPE；
# initialize(URL,ResourceBundle) / initialize() 按方法名反射调用；无参构造保证 newInstance() 不炸。
# 类名本身不 keep，可正常混淆。
-keepclassmembers class * extends com.allan.atools.bases.AbstractController {
    public protected *;
    <init>();
    void initialize(java.net.URL, java.util.ResourceBundle);
    void initialize();
}

#-keep class com.allan.atools.entro.** {
#    public protected *;
#}

#-keep class com.allan.module.** { *; }
#-dontnote com.allan.module.**
#-keep class com.allan.richtext.** { *; }
#-dontnote com.allan.richtext.**

#-keep class com.allan.richtext.codearea.** { *; }
#-dontnote com.allan.richtext.codearea.**

######
########## 子模块（BaseParty/BaseUiLibs）同为 JPMS 命名模块。
########## 其 exports 的包名必须保留（module-info exports 按字面包名，app 按这些包名调用）；
########## FXML 按全限定类名引用的子模块控件必须 keep 类名。
######
-keeppackagenames com.allan.baseparty
-keeppackagenames com.allan.baseparty.utils
-keeppackagenames com.allan.baseparty.content
-keeppackagenames com.allan.baseparty.handler
-keeppackagenames com.allan.baseparty.memory
-keeppackagenames com.allan.baseparty.exception
-keeppackagenames com.allan.baseparty.collections
-keeppackagenames com.allan.uilibs
-keeppackagenames com.allan.uilibs.controls
-keeppackagenames com.allan.uilibs.richtexts
-keeppackagenames com.allan.uilibs.jfoenix
-dontnote com.allan.baseparty.**
-dontnote com.allan.uilibs.**

# FXML（picture_show.fxml）按全限定类名引用的子模块控件，类名必须 keep
-keep class com.allan.uilibs.controls.Drag2ScrollPane { *; }
-keep class com.allan.uilibs.controls.RotatablePaneLayouter { *; }
