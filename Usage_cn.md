## 7步实现java9+
7步实现java9+，java14+模块化打包javafx程序最精简方案含代码

由于java9以上，java和javafx式微，界面程序一直低迷，这里自己花了比较多时间总结打包。

目前总结了mac、win上，java9+以上的打包过程生成img过程。java14又多了jpackage工具。

首先模块化，在java9上就是为了精简大小的。这方面资料很少，经过多方研究成型一套代码模板，看官集成到代码中即可轻松调试和编译打包，一体化，支持到了最新的2021.11年，java17。

### 准备工作

* windows请注意将jdk的目录全部去掉空格，特殊符号。比如放在`D:\profiles\jdk17.0.2`

#### 1. 项目构建

idea，新建maven项目，选择SDK，目前支持到了jdk17（新项目建议14+有牛逼的JVM GC效率高）。默认不勾选Create from archetype。输入名字，自行修改groupid等不赘述。

#### 2. 代码结构

> java9的模块化是什么？
>
> src->main->java->下面新建一个module-info.java
>
> 2.无名模块(Unnamed Module)
> 无名模块指的就是不包含 module-info.java 的 jar 包，通常这些 jar 包都是 Java 9 之前构建的。无名模块可以读取到其他所有的模块，并且也会将自己包下的所有类都暴露给外界。需要注意的是无名模块导出了所有的包，但并不意味着任何具名的模块可以读取无名模块，因为具名模块在 module-info.java 中无法声明对无名模块的依赖，无名模块导出所有包的目的在于让其他无名模块可以加载这些类。但是无名模块存在一个问题，假如我们需要依赖某个第三方的构件，但这个依赖还没有迁移到 Java 9 模块化，那么我们就无法引用其中的类，我们就无法编写应用了，难道我们要一直等到依赖迁移完成才能使用吗？请看下面的自动模块。
>
> 3.自动模块(Automatic Module)
> 任何无名模块(没有 module-info.java 的模块)放到模块路径(module path)上会自动变为自动模块，允许 Java 9 模块能引用到这些模块中的类。自动模块对外暴露所有的包并且能引用到其他所有模块的类，其他模块也能引用到自动模块的类。由于自动模块并不能声明模块名，那么 JDK 会根据 jar 包名来自动生成一个模块名以允许其他模块来引用。生成的模块名按以下规则生成：首先会移除文件扩展名以及版本号，然后使用".“替换所有非字母字符。例如 spring-core-4.3.12.jar 生成的模块名为 spring.core，那么其他模块就可以通过 requires spring.core 来引用其中的类。
> 但是这也并不意味着，自动模块可以读到具名模块所有的包和类。只有具名模块中使用exports导出的包，才能在自动模块中使用。如果没有导出，或者是指定导出，一样不可用。比如jdk9开源了Unsafe类的源码，也添加了一个新的类jdk.internal.misc.Unsafe，但是这个新类就无法被使用，因为没有被导出。原来的sun.misc.Unsafe依然可以用反射获取到，因为它是opens的。
>
>
> 有module-info.java的模块，是java9正规的module
> 没有module-info.java的jar包，如果放在module-path下，java9模块系统自动将其变成automatic module(其中如果jar包中MANIFEST.MF文件有Automatic-Module-Name属性，取其值作为模块名，没有的话，将jar包文件名根据一定规则提取模块名，如果提取不成功则无法转变为automatic module)
>
> 资料是在太少，目前只能分一个模块出去。而且他是独立的不引用。



### 混淆

proguard，2021.11月，需要proguard7.2.0-beta以上支持到17.

目前尝试下来，如果proguard版本低，将如下出现的source\target全部降低。 有时间可以继续尝试，只需要编译出来的target/classes/下面的任意class点击查看，bytecode version低于proguard要求的版本号即可。

/Users/allan/Documents/Tools/proguard7_2beta/bin/proguard.sh @../proguard_use.pro -injars myLibs/atools.jar -outjars myLibs/atools_cvt.jar
```
<properties>
    <maven.compiler.source>14</maven.compiler.source>
    <maven.compiler.target>14</maven.compiler.target>
</properties>

<plugin>
<groupId>org.apache.maven.plugins</groupId>
<artifactId>maven-compiler-plugin</artifactId>
<version>3.8.1</version>
<configuration>
<source>14</source>
<target>14</target>
</configuration>
</plugin>
```

修改maven的source支持和compiler版本。

还可以修改IDEA->Settings->Java compiler的target版本。

混淆规则：
如下基础的：

```properties
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
-keepattributes *Annotation*,Signature,EnclosingMethod

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

-keep class module-info


# mine app start
# suggest for we have to keep almost every package name for refelect call
-keeppackagenames com.allan.**
# keep these bean
-keep class com.allan.bean.** { *; }
-dontnote com.allan.bean.**

-keep class com.allan.controller.** { *; }
-dontnote com.allan.controller.**
```

只注意，基本上因为在工程使用的时候，基本上都是被系统反射调用很多类；我们就基本上全部keeppackagename，然后防止混淆bean，和防止混淆controller。

--add-exports javafx.web/com.sun.webkit.dom=atools

### 杂项

IDEA运行：，注意添加：VMOptions：
-DA_MEM_WATCHER=none -DA_DEBUG=true -DA_WARN=true -Dfile.encoding=UTF-8 --add-exports java.base/java.lang.reflect=com.jfoenix --add-opens java.base/java.lang.reflect=com.jfoenix --add-exports javafx.base/com.sun.javafx.event=com.jfoenix --add-exports javafx.graphics/com.sun.javafx.stage=com.jfoenix --add-exports javafx.controls/com.sun.javafx.scene.control.behavior=com.jfoenix --add-exports javafx.controls/com.sun.javafx.scene.control=com.jfoenix --add-exports javafx.base/com.sun.javafx.binding=com.jfoenix --add-exports javafx.graphics/com.sun.javafx.scene=com.jfoenix --add-opens com.jfoenix/com.jfoenix.skins=atools --add-opens org.fxmisc.richtext/org.fxmisc.richtext=atools --add-exports javafx.graphics/com.sun.javafx.stage=atools --add-opens com.jfoenix/com.jfoenix.controls=atools --add-opens com.jfoenix/com.jfoenix.skins=atools.baseparty --add-opens org.fxmisc.richtext/org.fxmisc.richtext=atools.baseparty --add-exports javafx.graphics/com.sun.javafx.stage=atools.baseparty --add-opens com.jfoenix/com.jfoenix.controls=atools.baseparty
通过命令行运行：
直接参考编译的时候，直接运行的时候IDEA缩起来的命令。
/Users/allan/Documents/codes/MyCodes/notepadmm/buildRoot/miniJre/bin/java -Dfile.encoding=UTF-8 --add-exports java.base/java.lang.reflect=com.jfoenix --add-opens java.base/java.lang.reflect=com.jfoenix --add-exports javafx.base/com.sun.javafx.event=com.jfoenix --add-exports javafx.graphics/com.sun.javafx.stage=com.jfoenix --add-exports javafx.controls/com.sun.javafx.scene.control.behavior=com.jfoenix --add-exports javafx.controls/com.sun.javafx.scene.control=com.jfoenix --add-exports javafx.base/com.sun.javafx.binding=com.jfoenix --add-exports javafx.graphics/com.sun.javafx.scene=com.jfoenix --add-opens com.jfoenix/com.jfoenix.skins=atools --add-opens org.fxmisc.richtext/org.fxmisc.richtext=atools --add-exports javafx.graphics/com.sun.javafx.stage=atools --add-opens com.jfoenix/com.jfoenix.controls=atools -classpath /Users/allan/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.6.20/kotlin-stdlib-jdk8-1.6.20.jar:/Users/allan/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-common/1.6.20/kotlin-stdlib-common-1.6.20.jar:/Users/allan/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.6.20/kotlin-stdlib-jdk7-1.6.20.jar:/Users/allan/.m2/repository/org/openjfx/javafx-controls/18.0.1/javafx-controls-18.0.1.jar:/Users/allan/.m2/repository/org/openjfx/javafx-fxml/18.0.1/javafx-fxml-18.0.1.jar:/Users/allan/.m2/repository/org/openjfx/javafx-graphics/18.0.1/javafx-graphics-18.0.1.jar:/Users/allan/.m2/repository/org/openjfx/javafx-base/18.0.1/javafx-base-18.0.1.jar -p /Users/allan/.m2/repository/org/openjfx/javafx-controls/18.0.1/javafx-controls-18.0.1-mac-aarch64.jar:/Users/allan/.m2/repository/org/fxmisc/richtext/richtextfx/0.10.9/richtextfx-0.10.9.jar:/Users/allan/.m2/repository/org/fxmisc/wellbehaved/wellbehavedfx/0.3.3/wellbehavedfx-0.3.3.jar:/Users/allan/.m2/repository/org/fxmisc/undo/undofx/2.1.1/undofx-2.1.1.jar:/Users/allan/.m2/repository/org/openjfx/javafx-fxml/18.0.1/javafx-fxml-18.0.1-mac-aarch64.jar:/Users/allan/Documents/codes/MyCodes/notepadmm/BaseParty/target/classes:/Users/allan/.m2/repository/org/openjfx/javafx-graphics/18.0.1/javafx-graphics-18.0.1-mac-aarch64.jar:/Users/allan/.m2/repository/com/jfoenix/jfoenix/9.0.10/jfoenix-9.0.10.jar:/Users/allan/.m2/repository/org/reactfx/reactfx/2.0-M5/reactfx-2.0-M5.jar:/Users/allan/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.6.20/kotlin-stdlib-1.6.20.jar:/Users/allan/.m2/repository/org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar:/Users/allan/Documents/codes/MyCodes/notepadmm/BaseUiLibs/target/classes:/Users/allan/.m2/repository/org/openjfx/javafx-base/18.0.1/javafx-base-18.0.1-mac-aarch64.jar:/Users/allan/.m2/repository/com/google/code/gson/gson/2.9.0/gson-2.9.0.jar:/Users/allan/.m2/repository/org/fxmisc/flowless/flowless/0.6.9/flowless-0.6.9.jar:/Users/allan/Documents/codes/MyCodes/notepadmm/target/classes -m atools/com.allan.atools.toolsstartup.Startup

--vendor --win-dir-chooser --win-shortcut
window打包