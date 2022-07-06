## 注解详解
注解，英文，Annotation，也称为：元数据。

java, javafx, android都内置了很多注解。

稍微来看几个：

```java
@Documented
@Retention(CLASS)
@Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE})
public @interface NonNull {  //package android.support.annotation; android支持包中的
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface FXML {
}

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Override {
}
```

比如最常见的接口、类继承的时候，Override的方法会提示报错，这是因为编译器发出了错误警告。而IDE发扬光大在显示的时候直接红色。

#### 使用注解

这个就不多做解释了。后面介绍target就会有讲。并且我们也经常看到各种，Override，Deprecated，相信都有接触。 

#### 定义注解

如前面的例子，定义一个注解，第一步，跟定义接口是一样的，只是多了一个@。

```java
public @interface StartupEntro {
}
```

第二步，需要描述2个必须的“头”。在头上添加2个meta annotation（**元注解**），

* @Target来指明注解用在哪里，可以逗号隔开多个，如前面NonNull；
* @Retention指明保留在什么级别，对应着不同的工作模式。

> 其实有4个元注解可以放在一个注解的头部，分别是
>
> @Target 表示该注解用于什么地方，可能的值在枚举类 ElemenetType 中，包括：
>
>      ElemenetType.CONSTRUCTOR-----------------------------构造器声明 
>      ElemenetType.FIELD ----------------------------------域声明（包括 enum 实例） 
>      ElemenetType.LOCAL_VARIABLE------------------------- 局部变量声明 
>      ElemenetType.METHOD ---------------------------------方法声明 
>      ElemenetType.PACKAGE --------------------------------包声明 
>      ElemenetType.PARAMETER ------------------------------参数声明 
>      ElemenetType.TYPE----------------------------------- 类，接口（包括注解类型）或enum声明 
>
>
> @Retention 表示在什么级别保存该注解信息。可选的参数值在枚举类型 RetentionPolicy 中，包括：
>
>      RetentionPolicy.SOURCE-------------注解将被编译器丢弃 
>      RetentionPolicy.CLASS -------------注解在class文件中可用，但会被VM丢弃 
>      RetentionPolicy.RUNTIME ---------VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息。
>
>
> @Documented 将此注解包含在 javadoc 中 ，它代表着此注解会被javadoc工具提取成文档。在doc文档中的内容会因为此注解的信息内容不同而不同。相当与@see,@param 等。
>
> @Inherited 允许子类继承父类中的注解。
>
> 后2个这边不做介绍，忽略。也非必须。



因此，你定义的这个注解想要用在什么地方倒是好处理，比如我要定义参数的就描述`@Target({PARAMETER})`;又比如，我要标记某个模块的类，想被其他模块调用，就用`@Target({TYPE})`。

```java
@Target({ElementType.TYPE})
public @interface StartupEntro {
}
```

还得使用 `@Retention` 指明保留在什么级别，对应着不同的工作模式。

我们还得一一来研究着3种RetentionPolicy。

* Retention - RetentionPolicy.SOURCE

```java
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface StartupEntro {
}
```

<img src="/Users/allan/Library/Application Support/typora-user-images/image-20210520144216985.png" alt="image-20210520144216985" style="zoom:50%;" />

你看，当我们指明了Target只有TYPE，表示只标注类，放在方法上面就会IDE提示报错。（不用担心搞错啦。大胆尝试呗！）只保留类的描述。

源码注解(RetentionPolicy.SOURCE)的生命周期只存在Java源文件这一阶段，是3种生命周期中最短的注解。当在Java源程序上加了一个注解，这个Java源程序要由javac去编译，javac把java源文件编译成.class文件，在编译成class时会把Java源程序上的源码注解给去掉。

你看android support包中有一个`IntDef`:

```java
@Retention(SOURCE)
@Target({ANNOTATION_TYPE})
public @interface IntDef {
    /** Defines the allowed constants for this element */
    int[] value() default {};

    /** Defines whether the constants can be used as a flag, or just as an enum (the default) */
    boolean flag() default false;
}
```

每一个枚举值都是一个对象，在使用它时会增加额外的内存消耗，所以枚举相比与 Integer 和 String 会占用更多的内存。 较多的使用 Enum 会增加 DEX 文件的大小，会造成运行时更多的开销。单个枚举会使应用的 classes.dex 文件增加大约 1.0 到 1.4 KB 的大小。`@IntDef`，它的目的是为了限定某些类似Status状态机的int值取值的范围，因为如果直接写比如：

```
public static class BTStatus {
    public static final int STATUS_ADLE = 1;
    public static final int STATUS_OPENING = 2;
    public static final int STATUS_WORKED = 3;
    public static final int STATUS_CLOSING = 4;
}
```

那么，我们用的时候`setStatus(int st)`，容易给出5，6，-1，-100去都是可以的。

所以我们结合IntDef，再定义出一个注解为：

```java
@Retention(RetentionPolicy.SOURCE) //源码级别，class文件是没有的。
@Target(ElementType.PARAMETER) //参数上去使用它
@IntDef({BTStatus.STATUS_ADLE, BTStatus.STATUS_OPENING, BTStatus.STATUS_CLOSING, BTStatus.STATUS_WORKED}) //范围指定了
public @interface MyStatus {
    int STATUS_ADLE = 1;
    int STATUS_OPENING = 2;
    int STATUS_WORKED = 3;
    int STATUS_CLOSING = 4;
}
```

![image-20210520150841609](/Users/allan/Library/Application Support/typora-user-images/image-20210520150841609.png)

你看，这就提示错误了。

同样的，我将IntDef拷贝到IDEA的java工程中就不会提示出错了。而且kotlin的老版本似乎也不支持（最新有待调研）。推测是android studio做了兼容support包的逻辑。可见他的SOURCE级别的含义就在此处，编译后就丢弃掉了，用做范围取值合适不过。同理还有`StringDef`。



* Retention - RetentionPolicy.CLASS

保留在字节码阶段，VM阶段就没有了。即编译阶段有用。这就牵涉到了**APT，注解处理器 **。

编译时注解的核心就是实现**AbstractProcessor的process()**方法，一般来说主要有以下两个步骤
1。搜集信息，包括被注解的类的类信息，方法，字段等信息，还有注解的值；
2。生成对应的java源代码，主要根据上一步的信息，生成响应的代码。

>  下图表明RetentionPolicy.CLASS的会被保留在class反编译的文件中：而SOURCE则不会。

<img src="/Users/allan/Library/Application Support/typora-user-images/image-20210520144553471.png" alt="image-20210520144553471" style="zoom:30%;" />

这种方法，在android各大框架中广泛使用。用来**节省同样可以实现类似功能的方式“反射”的开销**。



现在有个逻辑，是这样，我这个StartupApplication是一个子模块（比如做成一个开源的库）的代码，我要求传入主模块的StartupInitImp实现类。如果不然，可能需要引用者自己调用我的StartupApplication来传递进去。

但是可能我不想暴露它，或者，这个类不适合new出来等等，或者我就是只想让主模块标识注解你的InitImp类出来我框架就自行知道了。仔细想想，模块只是我们人为的分割，对于字节码而言，我们只需要组装一下，他们之前就可以直接引用了对不对。（当然反射也可以实现，后面章节我们再来详解。）

<img src="/Users/allan/Library/Application Support/typora-user-images/image-20210520154132769.png" alt="image-20210520154132769" style="zoom:30%;" />

所以，对于“框架”而言，圈出来的部分要删除。

即整个mInit需要我们通过注解，在编译阶段自动生成。

所以这里的代码模式得改一下：

```java
private final IStartupInit mInit = null;
```

然后标志我们的IStartInit的子类，主模块中的代码：

```java
@StartupEntro
public final class StartupInitImp implements IStartupInit {
```

接下来就是编写注解器代码来实现自动生成java代码`StartupAppHelper`。

有兴趣的就多查阅一些，AbstractProcessor相关的资料。这里就简略的描述一下。想要学好这个APT的使用确实还挺麻烦的。android中还有auto-service生成辅助信息，javapoet来配合生成java文件。具体自行学习了。

最终我们在build下得到了：

```
```

[java注解之编译时注解RetentionPolicy.CLASS 基本用法_带你装逼带你飞的专栏-CSDN博客](https://blog.csdn.net/qiyei2009/article/details/79947352)

[java 秒懂 注解 （Annotation）你可以这样学 运行时注解和编译注解_深南大盗的博客-CSDN博客_编译时注解 运行时注解](https://blog.csdn.net/WHB20081815/article/details/89287253?utm_medium=distribute.pc_relevant.none-task-blog-2~default~BlogCommendFromMachineLearnPai2~default-2.control&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2~default~BlogCommendFromMachineLearnPai2~default-2.control)



* Retention - RetentionPolicy.RUNTIME

最后一种生命周期最长。VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息。

这种方式相对CLASS需要编写APT而言简单一些。不过在android中反射会比较耗时，尤其在Application init阶段使用的话，往往是各大公司首先会去优化的点。会将运行时改成编译时的CLASS去解决。不过会复杂一些。在java后台项目(比如Spring大量的反射)，pc项目或者android上一些后期的动作，偶尔使用一下动态运行的代码，反射使用，比较合适。当然啦，普通项目中，不在乎不扣这一点性能的也可以使用。慢慢优化即可。

```java
    private IStartupInit create() {
        var list = ClassUtil.getAllClassByInterface(IStartupInit.class, "com.tools");
        System.out.println("list" + list.size());
        //自行保证list
        try {
            var clazz = list.get(0);
            Object obj = clazz.getConstructor().newInstance();
            return (IStartupInit) obj;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
```

上述代码是根据反射查找某个接口的子类。如果不是interface的子类，有RUNTIME的注解也可以获取。

```java
var list = ClassUtil.getClasses("com.tools");
//自行判断list
for (Class<?> cls : list) {
if (cls.getAnnotation(StartupEntro.class) != null) {
 }
}
```





