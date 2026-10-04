---
title: "Vditor 中文综合示例与 ATools 日常自测"
date: '2026-10-04'
tags: [markdown, 自测, 中文]
description: "元数据里的 **粗体**、:smile: 和 https://example.com 都是文本"
...

## 测试阅读说明

前半部分保留 Vditor 中文示例，后半部分增加 ATools 的显示与编辑检查。原示例中的用户提及、通知、上传、多媒体等说明属于上游网站，不表示 ATools 具备这些服务。

新增用例配有本地图片与链接目标文档，连同 assets 文件夹一起移动即可保留相对路径。原示例里的远程链接仍可能需要联网。

先在编辑区检查常用排版和交互，再打开“完整排版预览”检查公式、脚注、目录和 HTML 输出。原示例中的 PlantUML、mindmap、ECharts、ABC、Graphviz、Flowchart 和 WaveDrom 等扩展保留作兼容性观察，不列入当前必须渲染为图形的范围；Mermaid 流程图、时序图与甘特图单独检查。

未闭合语法与另一种 Front Matter 结束写法放在代码块里，按说明复制到新文档检查；这篇文档本身保持完整结构。

## 教程

这是一篇讲解如何正确使用 **Markdown** 的排版示例，学会这个很有必要，能让你的文章有更佳清晰的排版。

> 引用文本：Markdown is a text formatting syntax inspired

## 语法指导

### 普通内容

这段内容展示了在内容里面一些排版格式，比如：

- **加粗** - `**加粗**`
- *倾斜* - `*倾斜*`
- ~~删除线~~ - `~~删除线~~`
- `Code 标记` - `` `Code 标记` ``
- [超级链接](https://ld246.com) - `[超级链接](https://ld246.com)`
- [username@gmail.com](mailto:username@gmail.com) - `[username@gmail.com](mailto:username@gmail.com)`

### 提及用户

@Vanessa 通过 `@User` 可以在内容中提及用户，被提及的用户将会收到系统通知。

> NOTE:
>
> 1. @用户名之后需要有一个空格
> 2. 新手没有艾特的功能权限

### 表情符号 Emoji

支持大部分标准的表情符号，可使用输入法直接输入，也可手动输入字符格式。通过输入 `:` 触发自动完成，可在个人设置中[设置常用表情](https://ld246.com/settings/function)。

#### 一些表情例子

:smile: :laughing: :dizzy_face: :sob: :cold_sweat: :sweat_smile:  :cry: :triumph: :heart_eyes: :relieved:
:+1: :-1: :100: :clap: :bell: :gift: :question: :bomb: :heart: :coffee: :cyclone: :bow: :kiss: :pray: :anger:

### 大标题 - Heading 3

你可以选择使用 H1 至 H6，使用 ##(N) 打头。建议帖子或回帖中的顶级标题使用 Heading 3，不要使用 1 或 2，因为 1 是系统站点级，2 是帖子标题级。

> NOTE: 别忘了 # 后面需要有空格！

#### Heading 4

##### Heading 5

###### Heading 6

### 图片

```
![alt 文本](http://image-path.png)
![alt 文本](http://image-path.png "图片 Title 值")
```

支持复制粘贴直接上传。

### 代码块

#### 普通

```
*emphasize*    **strong**
_emphasize_    __strong__
var a = 1
```

#### 语法高亮支持

如果在 ``` 后面跟随语言名称，可以有语法高亮的效果哦，比如:

##### 演示 Go 代码高亮

```go
package main

import "fmt"

func main() {
	fmt.Println("Hello, 世界")
}
```

##### 演示 Java 高亮

```java
public class HelloWorld {

    public static void main(String[] args) {
        System.out.println("Hello World!");
    }

}
```

> Tip: 语言名称支持下面这些: `ruby`, `python`, `js`, `html`, `erb`, `css`, `coffee`, `bash`, `json`, `yml`, `xml` ...

### 有序、无序、任务列表

#### 无序列表

- Java
  - Spring
    - IoC
    - AOP
- Go
  - gofmt
  - Wide
- Node.js
  - Koa
  - Express

#### 有序列表

1. Node.js
   1. Express
   2. Koa
   3. Sails
2. Go
   1. gofmt
   2. Wide
3. Java
   1. Latke
   2. IDEA

#### 任务列表

- [x] 发布 Sym
- [x] 发布 Solo
- [ ] 预约牙医

### 表格

如果需要展示数据什么的，可以选择使用表格。

| header 1 | header 2 |
| -------- | -------- |
| cell 1   | cell 2   |
| cell 3   | cell 4   |
| cell 5   | cell 6   |

### 隐藏细节

<details>
<summary>这里是摘要部分。</summary>
这里是细节部分。
</details>

### 段落

空行可以将内容进行分段，便于阅读。（这是第一段）

使用空行在 Markdown 排版中相当重要。（这是第二段）

### 链接引用

[链接文本][链接标识]

[链接标识]: https://b3log.org

```
[链接文本][链接标识]

[链接标识]: https://b3log.org
```

### 数学公式

多行公式块：

$$
\frac{1}{
  \Bigl(\sqrt{\phi \sqrt{5}}-\phi\Bigr) e^{
  \frac25 \pi}} = 1+\frac{e^{-2\pi}} {1+\frac{e^{-4\pi}} {
    1+\frac{e^{-6\pi}}
    {1+\frac{e^{-8\pi}}{1+\cdots}}
  }
}
$$

行内公式：

公式 $a^2 + b^2 = \color{red}c^2$ 是行内。

### 脑图

```mindmap
- 教程
- 语法指导
  - 普通内容
  - 提及用户
  - 表情符号 Emoji
    - 一些表情例子
  - 大标题 - Heading 3
    - Heading 4
      - Heading 5
        - Heading 6
  - 图片
  - 代码块
    - 普通
    - 语法高亮支持
      - 演示 Go 代码高亮
      - 演示 Java 高亮
  - 有序、无序、任务列表
    - 无序列表
    - 有序列表
    - 任务列表
  - 表格
  - 隐藏细节
  - 段落
  - 链接引用
  - 数学公式
  - 脑图
  - 流程图
  - 时序图
  - 甘特图
  - 图表
  - 五线谱
  - Graphviz
  - 多媒体
  - 脚注
- 快捷键
```

### plantuml

```plantuml
@startuml component
actor client
node app
database db

db -> app
app -> client
@enduml
```

更多图形参考[https://plantuml.com/zh/](https://plantuml.com/zh/)

### 流程图

```mermaid
graph TB
    c1-->a2
    subgraph one
    a1-->a2
    end
    subgraph two
    b1-->b2
    end
    subgraph three
    c1-->c2
    end
```

### 时序图

```mermaid
sequenceDiagram
    Alice->>John: Hello John, how are you?
    loop Every minute
        John-->>Alice: Great!
    end
```

### 甘特图

```mermaid
gantt
    title A Gantt Diagram
    dateFormat  YYYY-MM-DD
    section Section
    A task           :a1, 2019-01-01, 30d
    Another task     :after a1  , 20d
    section Another
    Task in sec      :2019-01-12  , 12d
    another task      : 24d
```

### 图表

```echarts
{
  "title": { "text": "最近 30 天" },
  "tooltip": { "trigger": "axis", "axisPointer": { "lineStyle": { "width": 0 } } },
  "legend": { "data": ["帖子", "用户", "回帖"] },
  "xAxis": [{
      "type": "category",
      "boundaryGap": false,
      "data": ["2019-05-08","2019-05-09","2019-05-10","2019-05-11","2019-05-12","2019-05-13","2019-05-14","2019-05-15","2019-05-16","2019-05-17","2019-05-18","2019-05-19","2019-05-20","2019-05-21","2019-05-22","2019-05-23","2019-05-24","2019-05-25","2019-05-26","2019-05-27","2019-05-28","2019-05-29","2019-05-30","2019-05-31","2019-06-01","2019-06-02","2019-06-03","2019-06-04","2019-06-05","2019-06-06","2019-06-07"],
      "axisTick": { "show": false },
      "axisLine": { "show": false }
  }],
  "yAxis": [{ "type": "value", "axisTick": { "show": false }, "axisLine": { "show": false }, "splitLine": { "lineStyle": { "color": "rgba(0, 0, 0, .38)", "type": "dashed" } } }],
  "series": [
    {
      "name": "帖子", "type": "line", "smooth": true, "itemStyle": { "color": "#d23f31" }, "areaStyle": { "normal": {} }, "z": 3,
      "data": ["18","14","22","9","7","18","10","12","13","16","6","9","15","15","12","15","8","14","9","10","29","22","14","22","9","10","15","9","9","15","0"]
    },
    {
      "name": "用户", "type": "line", "smooth": true, "itemStyle": { "color": "#f1e05a" }, "areaStyle": { "normal": {} }, "z": 2,
      "data": ["31","33","30","23","16","29","23","37","41","29","16","13","39","23","38","136","89","35","22","50","57","47","36","59","14","23","46","44","51","43","0"]
    },
    {
      "name": "回帖", "type": "line", "smooth": true, "itemStyle": { "color": "#4285f4" }, "areaStyle": { "normal": {} }, "z": 1,
      "data": ["35","42","73","15","43","58","55","35","46","87","36","15","44","76","130","73","50","20","21","54","48","73","60","89","26","27","70","63","55","37","0"]
    }
  ]
}
```

### 五线谱

```abc
X: 24
T: Clouds Thicken
C: Paul Rosen
S: Copyright 2005, Paul Rosen
M: 6/8
L: 1/8
Q: 3/8=116
R: Creepy Jig
K: Em
|:"Em"EEE E2G|"C7"_B2A G2F|"Em"EEE E2G|\
"C7"_B2A "B7"=B3|"Em"EEE E2G|
"C7"_B2A G2F|"Em"GFE "D (Bm7)"F2D|\
1"Em"E3-E3:|2"Em"E3-E2B|:"Em"e2e gfe|
"G"g2ab3|"Em"gfeg2e|"D"fedB2A|"Em"e2e gfe|\
"G"g2ab3|"Em"gfe"D"f2d|"Em"e3-e3:|
```

### Graphviz

```graphviz
digraph finite_state_machine {
    rankdir=LR;
    size="8,5"
    node [shape = doublecircle]; S;
    node [shape = point ]; qi

    node [shape = circle];
    qi -> S;
    S  -> q1 [ label = "a" ];
    S  -> S  [ label = "a" ];
    q1 -> S  [ label = "a" ];
    q1 -> q2 [ label = "ddb" ];
    q2 -> q1 [ label = "b" ];
    q2 -> q2 [ label = "b" ];
}
```

### Flowchart

```flowchart
st=>start: Start
op=>operation: Your Operation
cond=>condition: Yes or No?
e=>end

st->op->cond
cond(yes)->e
cond(no)->op
```

### WaveDrom

```wavedrom
{ signal: [
  { name: "clk", wave: "p......" },
  { name: "bus", wave: "x.34.5x", data: "head body tail" },
  { name: "wire", wave: "0.1..0." }
]}
```

### 多媒体

支持 v.qq.com，youtube.com，youku.com，coub.com，facebook.com/video，dailymotion.com，.mp4，.m4v，.ogg，.ogv，.webm，.mp3，.wav 链接解析

https://v.qq.com/x/cover/zf2z0xpqcculhcz/y0016tj0qvh.html

### 脚注

这里是一个脚注引用[^1]，这里是另一个脚注引用[^bignote]。

[^1]: 第一个脚注定义。
[^bignote]: 脚注定义可使用多段内容。

    缩进对齐的段落包含在这个脚注定义内。
    
    ```text
    可以使用代码块。
    ```
    
    还有其他行级排版语法，比如**加粗**和[链接](https://b3log.org)。

````text
这里是一个脚注引用[^1]，这里是另一个脚注引用[^bignote]。
[^1]: 第一个脚注定义。
[^bignote]: 脚注定义可使用多段内容。

    缩进对齐的段落包含在这个脚注定义内。

    ```text
    可以使用代码块。
    ```

    还有其他行级排版语法，比如**加粗**和[链接](https://b3log.org)。
````

## 快捷键

我们的编辑器支持很多快捷键，具体请参考 [键盘快捷键](https://ld246.com/article/1582778815353)

---

## ATools 补充自测用例

这些用例用于手动检查显示、编辑和保存，结果需要在实际界面中确认。光标进入某些结构后显示源码标记是编辑行为；移开光标后再比较阅读效果。公式、脚注、目录和部分 HTML 扩展请同时检查“完整排版预览”。

### 六级标题与不同标题写法

检查：标题与正文左边缘对齐，六级标题层次清楚；标题加粗可与下面的粗体正文比较。大纲应包含 ATX 和 Setext 标题。

# 自测一级标题

**粗体正文对照：标题不能显得比这段还细。**

## 自测二级标题

二级标题后面的正文。

### 自测三级标题

三级标题后面的正文。

#### 自测四级标题

四级标题后面的正文。

##### 自测五级标题

五级标题后面的正文。

###### 自测六级标题

六级标题后面的正文。

自测 Setext 一级标题
====================

自测 Setext 二级标题
--------------------

### 标题中的 **粗体**、*斜体* 与 `inlineCode` ###

以下两行是普通文本，不应误判成标题：

#缺少空格的文本

####### 超过六个井号的文本

### 行内格式、反引号与转义

检查：星号和下划线写法分别产生斜体或粗体；代码中的标记、反斜杠和下划线应原样显示。保存后源码仍使用原来的标记。

*星号斜体*，_下划线斜体_，**星号粗体**，__下划线粗体__。

***同时加粗和倾斜***，**粗体中的 *斜体***，*斜体中的 **粗体***，~~删除线中的 **粗体**~~。

文件名 foo_bar_baz、变量 user_name、路径 /tmp/my_file_name 不应自动产生斜体。

`*emphasize* **strong** _emphasize_ __strong__`，`a_b`，`C:\Users\name`。

``含有一个 ` 反引号``，`` `两端有反引号` ``，`   `。

\*这不是斜体\*，\_这不是斜体\_，\[这不是链接\](https://example.com)，\# 这不是标题。

实体：&lt;tag&gt; &amp; &quot;引号&quot; &#169; &#x1F680;。

中文标点：**粗体**，*斜体*；`代码`。英文标点：**bold**, *italic*; `code`.

### 软换行、硬换行与分隔线

这一行后面只有普通换行。
下一行仍属于同一个段落，软换行的显示由编辑器配置决定。

这一行末尾有两个空格。  
这一行应是同一段中的硬换行。

这一行末尾使用反斜杠。\
这一行也应是同一段中的硬换行。

三个分隔线写法，下方均应显示分隔线：

---

***

___

### 引用、多段引用与混合内容

检查：引用竖线到正文之间约一个字符的距离，正文稍灰且清楚；嵌套引用、长行折行和内部列表不能额外缩进一大块。

> 一级引用：**粗体**、*斜体*、[链接](https://example.com)、`代码`。
>
> 第二段引用，用来检查背景与竖线的连续性。
>
> > 二级引用。
> >
> > > 三级引用。
>
> 返回一级引用。

> 第一行显式带有引用标记。
这一行省略引用标记，仍是上一段引用的续行。

> 引用里的列表：
>
> - 一级项目
>   - 二级项目
>     - 三级项目
>
> ~~~java
> String message = "引用中的 Hello World";
> // 注释也应在代码块内部
> ~~~
>
> | 引用内表格 | 值 |
> | --- | --- |
> | **格式** | `内容` |

### 五层列表、混合列表与长行

检查：无序列表第一层为实心圆，第二层为空心圆，第三层及以后为实心方块；圆点大小与正文协调。不同源码符号使用相同层级规则。

- 第一层：实心圆
  - 第二层：空心圆
    - 第三层：实心方块
      - 第四层：仍是实心方块
        - 第五层：仍是实心方块
  - 返回第二层
- 返回第一层

* 星号列表
  * 星号的第二层
    * 星号的第三层

+ 加号列表
  + 加号的第二层
    + 加号的第三层

- 无序列表中放入有序列表
  1. 第一步
  2. 第二步
     - 有序列表中的无序项目

3. 有序列表从 3 开始
4. 第二个项目
   1. 子项目
   2. 子项目的下一项

1) 右括号形式的有序列表
2) 右括号形式的第二项

- 松散列表第一项。

  第一项的第二段正文，左边缘应保持与项目文字对齐。

  > 第一项内的引用。

  ~~~kotlin
  val message = "列表里的代码块"
  println(message)
  ~~~

- 松散列表第二项。

- 长行：这是一段用于检查自动折行、项目符号位置与悬挂缩进的中文文本。把窗口缩窄后观察第二行和第三行应如何与正文起始位置对齐，同时检查光标、选区和滚动位置，避免缩进随着行数不断增加。这段文字继续延长，以便在常见窗口宽度下也能出现自动折行。

### 任务列表的点击和撤销

检查：悬停在复选框上显示手形，点击只切换对应任务；已完成任务的源码可用小写或大写 x。切换后撤销、重做、保存重开应一致；只读模式不应改写文件。

- [ ] 未完成任务，包含 **粗体** 和 [链接](https://example.com)
- [x] 已完成任务：小写 x
- [X] 已完成任务：大写 X
- [ ] 父任务
  - [ ] 子任务一
  - [x] 子任务二
- [ ] 这是一个很长的任务项目，用来观察任务内容在窄窗口下自动折行之后，复选框是否仍然对齐到第一行，后续文字是否保持合适的缩进，以及点击复选框后文字和滚动位置是否稳定。

  这是最后一个任务的第二段正文。

1. [ ] 有序任务一
2. [x] 有序任务二

普通正文里的 [ ] 和 [x]、行内代码 `- [ ] 示例` 不应出现可点击复选框。

### 表格的对齐、格式与编辑

检查：左、中、右对齐有区别；格式在单元格中有效，转义的竖线没有拆出新列。编辑时试一下 Tab、Shift+Tab、增删行列、复制 TSV、粘贴多格与撤销。

| 左对齐 | 居中 | 右对齐 |
| :--- | :---: | ---: |
| **粗体** | *斜体* | 123.45 |
| ~~删除线~~ | `a_b` | -98 |
| [链接](https://example.com) | :smile: | 0 |
| 管道 A\|B | `x\|y` | 1000 |
| 空单元格 → | | |
| 很长的中文单元格，用来观察窄窗口中是否可以折行和选择文字，且不会遮住其他单元格 | 普通文字 | 7 |

以下表格的源码省略了外侧竖线：

名称 | 状态
--- | ---
第一项 | 正常
第二项 | 待检查

宽表格，用于检查横向查看与列编辑：

| 项目 | 负责人 | 计划开始 | 计划结束 | 实际开始 | 实际结束 | 状态 | 优先级 | 备注 | 链接 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 中文测试项目 | Allan | 2026-10-04 | 2026-10-10 | 2026-10-04 | | 进行中 | 高 | 宽表格不能吞掉后面的列 | [说明](https://example.com) |

### 链接、引用定义与本地跳转

检查：普通点击可编辑，Ctrl／⌘ 点击按程序约定打开链接。引用定义的标识与地址要能读清；查找引用标识时能定位源码。不存在的引用定义应保留文本。

[带标题的链接](https://example.com "这是链接标题")，<https://example.com>，<reader@example.com>。

自动链接：https://example.com/path?q=markdown 与 www.example.com。

[完整引用][补充链接]，[折叠引用][]，[快捷引用]。

[补充链接]: https://example.com/docs "引用标题"
[折叠引用]: https://example.com/collapsed
[快捷引用]: https://example.com/shortcut

[未定义引用][不存在的引用标识]。

[跳到中文标题](#补充定位中文标题)，[跳到第一个重复标题](#补充重复标题)，[跳到第二个重复标题](#补充重复标题-1)。

[打开本地测试文档](<assets/链接跳转 测试.md>)，[打开本地文档的中文标题](<assets/链接跳转 测试.md#中文跳转目标>)。

[查看本地图片](<assets/自测 图片 (中文).png>)。

### 补充定位中文标题

从上面的中文标题链接跳到这里，再编辑标题并观察大纲更新。

### 补充重复标题

这是重复标题的第一次出现。

### 补充重复标题

这是重复标题的第二次出现。

### 离线图片与失败回退

检查：下列前三张图片来自同一张本地 PNG，无需联网。图片有红、绿、蓝三色块和细网格；路径带中文、空格和括号。尝试设置宽度、切换源码、另存为到其他目录后重开。

![中文 空格 括号路径](<assets/自测 图片 (中文).png> "离线图片标题")

![引用式图片][补充图片]

[补充图片]: <assets/自测 图片 (中文).png> "引用式离线图片"

<img src="assets/自测 图片 (中文).png" alt="设置宽度的图片" width="240">

<img src="assets/自测 图片 (中文).png" alt="Typora 缩放写法" style="zoom:50%">

这是一段文字中的行内图片：![行内图片](<assets/自测 图片 (中文).png>)，图片后面仍有文字。用它检查文字与图片混排的范围。

> 引用中的图片：
>
> ![引用图片](<assets/自测 图片 (中文).png>)

- 列表中的图片：

  ![列表图片](<assets/自测 图片 (中文).png>)

| 表格图片 | 说明 |
| --- | --- |
| ![缩略图](<assets/自测 图片 (中文).png>) | 本地图片缩略图 |

下面是故意不存在的图片，检查错误提示、重试入口与源码可编辑性：

![故意失败的图片](assets/不存在的图片.png)

### 表情清晰度与源码空格

检查：100%、150%、200% 缩放下均应清楚；复制和保存仍保留原始短码或 Unicode。原文中 sweat_smile 与 cry 之间的两个空格保留不变。

:smile: :laughing: :sweat_smile: :cry: :heart: :coffee: :rocket: :white_check_mark:

单空格对照：:sweat_smile: :cry:

双空格对照：:sweat_smile:  :cry:

相邻无空格对照：:sweat_smile::cry:

别名对照：:+1: :thumbsup: :thumbs_up: :-1: :thumbsdown:。

直接 Unicode：😄 😅 😢 ❤️ 👍🏽 👩‍💻 👨‍👩‍👧‍👦 🇨🇳 1️⃣。

混排：中文😄English，**粗体 :smile:**，*斜体 :cry:*。

未知短码 :not_a_real_emoji:、时间 12:30:45 和代码 `:smile:` 应保留原文。

### 代码围栏与字符串保护

检查：代码内的星号、下划线、表情短码和 HTML 标签均按源码显示。滚动、复制、搜索和语言切换后不能错行着色；重点看空行之后的字符串、注释和关键字。

~~~text
*emphasize*    **strong**
_emphasize_    __strong__
var a = 1
<details><summary>代码里的标签</summary></details>
:smile: $a^2$ [链接](https://example.com)
~~~

````markdown
# 这里是代码，不是正文标题

```java
String message = "内层围栏";
```

- [ ] 这里不出现可点击任务框
````

下面是四个空格缩进的代码块：

    _emphasize_    __strong__
    String message = "缩进代码块";

### 常用代码语言与别名

每个代码块都要检查字符串、注释、关键字、数字的颜色。不同语言和主题可以使用不同配色，不要求颜色与 Typora 完全一致。块中含有空行，用于检查后续行的着色位置。

#### Java

~~~java
package demo;

public class HelloWorld {
    // 字符串里的 // 和 /* 不应变成注释
    private static final String MESSAGE = "Hello World! // literal";

    public static void main(String[] args) {
        System.out.println(MESSAGE);
        System.out.println("Hello, 世界");
        int count = 42;
    }
}
~~~

#### Kotlin 与 kt 别名

~~~kotlin
package demo

/* 外层注释 /* 内层注释 */ 仍是注释 */
data class User(val name: String, val enabled: Boolean = true)

fun main() {
    val message = "Hello, 世界"
    val multiline = """
        第一行 // 这是字符串
        第二行 **这也不是粗体**
    """.trimIndent()
    println("$message: ${User("Allan").name}")
}
~~~

~~~kt
val message: String = "kt alias"
println(message)
~~~

#### Swift

~~~swift
import Foundation

/* 外层注释 /* 内层注释 */ 仍是注释 */
struct User {
    let name: String
}

let message = "Hello, 世界"
let multiline = """
第一行
第二行
"""
print("\(message): \(User(name: "Allan").name)")
~~~

#### Objective-C 与 object-c 别名

~~~objective-c
#import <Foundation/Foundation.h>

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        NSString *message = @"Hello, 世界";
        // NSString 字面量与注释要有不同颜色
        NSLog(@"%@, count=%d", message, 42);
    }
    return 0;
}
~~~

~~~object-c
NSString *message = @"object-c alias";
NSLog(@"%@", message);
~~~

#### C

~~~c
#include <stdio.h>

/* C 注释 */
int main(void) {
    const char *message = "Hello World!";
    char newline = '\n';
    printf("%s%c", message, newline);
    return 0;
}
~~~

#### C++ 与 c++ 别名

~~~cpp
#include <iostream>
#include <string>

int main() {
    // 原始字符串里的引号和 // 都是内容
    const std::string message = R"demo(Hello "世界" // literal)demo";
    std::cout << message << std::endl;
    return 0;
}
~~~

~~~c++
std::string message = "c++ alias";
auto count = 42;
~~~

#### Go

~~~go
package main

import "fmt"

func main() {
    // 空行之后的 import、func 和字符串仍应着色
    message := "Hello, 世界"
    raw := `第一行 // 字符串
第二行 /* 仍是字符串 */`
    fmt.Println(message, raw, 42)
}
~~~

#### Groovy、Gradle 与 goovy 别名

~~~groovy
// 单引号、双引号和多行字符串
def name = 'Allan'

def message = "Hello, ${name}"
def multiline = '''第一行
第二行 // 字符串'''
println(message)
~~~

~~~gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'example:test-library:1.0.0'
}
~~~

~~~goovy
def message = "goovy alias"
println(message)
~~~

~~~gradle.kts
plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}
~~~

#### YAML 与 yml 别名

~~~yaml
# YAML 注释
name: "Hello, 世界"
enabled: true
count: 42

defaults: &defaults
  retries: 3
service:
  <<: *defaults
  message: '字符串里的 # 不是注释'
  literal: |-
    第一行 # 仍是字符串
    第二行: true
  folded: >
    第一行
    第二行
items:
  - name: alpha
    value: null
~~~

~~~yml
name: "yml alias"
enabled: false
~~~

#### XML

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- XML 注释 -->
<resources enabled="true">
    <string name="hello">Hello, 世界 &amp; XML</string>
    <content><![CDATA[<b>这是原样文本</b>]]></content>
</resources>
~~~

#### HTML 中嵌入 CSS 与 JavaScript

~~~html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <style>
      .title { color: #336699; font-size: 16px; }
    </style>
  </head>
  <body>
    <!-- 属性值、实体和注释 -->
    <h1 class="title">Hello &amp; 世界</h1>
    <script>
      const message = "Hello World!";
      console.log(message);
    </script>
  </body>
</html>
~~~

#### CSS 与 SCSS

~~~css
/* CSS 注释 */
.card:hover {
    --accent: #336699;
    color: var(--accent);
    padding: 12px 16px;
    font-family: "JetBrains Mono", monospace;
    content: "Hello, 世界";
}
~~~

~~~scss
$accent: #336699;
.card {
    color: $accent;
    &:hover { content: "Hello"; }
}
~~~

#### Bash 与 sh 别名

~~~bash
#!/usr/bin/env bash
# 字符串里的 # 不应当作注释
name="Allan"

if [ -n "$name" ]; then
    printf '%s\n' "Hello, $name # literal"
fi

cat <<'MESSAGE'
Hello, 世界
MESSAGE
~~~

~~~sh
message='sh alias'
printf '%s\n' "$message"
~~~

#### JavaScript 与 TypeScript

~~~js
// 普通字符串与模板字符串
const user = { name: "Allan", enabled: true };

const message = `Hello, ${user.name}
第二行 // 字符串`;
console.log(message, 42);
~~~

~~~typescript
interface User {
    name: string;
    enabled: boolean;
}

const user: User = { name: "Allan", enabled: true };
function greet(value: User): string {
    return `Hello, ${value.name}`;
}
~~~

~~~jsx
// JSX 属性与字符串
const element = <div className="card">Hello, 世界</div>;
~~~

~~~tsx
type Props = { name: string };
function Greeting({ name }: Props) {
    return <div title="Hello World!">Hello, {name}</div>;
}
~~~

#### Python

~~~python
# Python 注释
def greet(name: str) -> str:
    message = f"Hello, {name}"
    multiline = """第一行
第二行 # 仍是字符串"""
    return message + multiline

print(greet("世界"), 42, True, None)
~~~

#### JSON 与 JSONC

~~~json
{
  "message": "Hello, 世界",
  "enabled": true,
  "count": 42,
  "optional": null,
  "escaped": "quote: \"hello\"",
  "items": ["first", "second"]
}
~~~

~~~jsonc
{
  // JSONC 允许注释
  "message": "Hello World!"
}
~~~

#### SQL

~~~sql
-- SQL 注释
SELECT id, name, 'Hello, 世界' AS message
FROM users
WHERE enabled = TRUE AND id >= 42
ORDER BY id DESC;
~~~

#### C#

~~~csharp
using System;

class HelloWorld {
    static void Main() {
        string message = "Hello World!";
        string path = @"C:\Users\Allan\notes";
        Console.WriteLine($"{message}: {path}");
    }
}
~~~

#### Rust

~~~rust
/* 外层注释 /* 内层注释 */ 仍是注释 */
fn main() {
    let message = "Hello, 世界";
    let raw = r#"Hello "world" // literal"#;
    println!("{} {}", message, raw);
}
~~~

#### Dart

~~~dart
void main() {
  final message = 'Hello, 世界';
  final multiline = '''第一行
第二行''';
  print('$message: $multiline');
}
~~~

#### Ruby

~~~ruby
# Ruby 注释
name = "世界"

def greet(name)
  "Hello, #{name}"
end

puts greet(name)
~~~

#### PHP

~~~php
<?php
// PHP 注释
$message = "Hello, 世界";

function greet($name) {
    return 'Hello, ' . $name;
}
echo greet($message);
?>
~~~

#### Lua

~~~lua
-- Lua 注释
local message = "Hello, 世界"

local multiline = [[第一行
第二行 -- 仍是字符串]]
print(message, multiline, 42)
~~~

#### TOML、INI 与 Properties

~~~toml
# TOML 注释
title = "Hello, 世界"

[server]
port = 8080
enabled = true
hosts = ["localhost", "example.com"]
~~~

~~~ini
; INI 注释
[server]
message = "Hello World!"
port = 8080
~~~

~~~properties
# Properties 注释
app.name=ATools
message=Hello World!
server.port=8080
~~~

~~~dotenv
# 环境变量文件
APP_NAME="Hello, 世界"
APP_PORT=8080
APP_ENABLED=true
~~~

#### Dockerfile

~~~dockerfile
FROM example/base:1.0

# 这里只检查颜色，不要求执行
ENV MESSAGE="Hello World!"
WORKDIR /app
COPY . /app
RUN echo "$MESSAGE"
CMD ["echo", "Hello, 世界"]
~~~

#### PowerShell

~~~powershell
# PowerShell 注释
$message = "Hello, 世界"

function Get-Greeting {
    param([string]$Name)
    return "Hello, $Name"
}
Write-Host (Get-Greeting -Name $message)
~~~

#### Protobuf 与 Diff

~~~protobuf
syntax = "proto3";
package demo;

// Protobuf 注释
message Greeting {
    string message = 1;
    bool enabled = 2;
}
~~~

~~~diff
diff --git a/hello.txt b/hello.txt
--- a/hello.txt
+++ b/hello.txt
@@ -1 +1 @@
-Hello World!
+Hello, 世界
~~~

#### 大写标签、未知语言与长代码行

~~~JAVA
public class AliasCheck {
    String message = "Uppercase JAVA";
}
~~~

~~~unknown-language
未知语言保留原文，不应吞字符或影响下一个代码块。
"Hello World!" _emphasize_ **strong** :smile:
~~~

~~~text
LONG_LINE_BEGIN 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz LONG_LINE_END
~~~

### HTML 折叠、嵌套与注释

检查：默认关闭的详情可以展开，带 open 的详情初始展开；嵌套详情各自切换。折叠与展开不应改写源码，进入源码后应能修改 summary 和正文。

<details>
<summary>默认关闭：点击展开这里</summary>

这里是详情正文，含 **粗体**、*斜体*、`代码` 和 :smile:。

- 详情里的列表一
- 详情里的列表二

<details>
<summary>嵌套详情：独立展开</summary>

这里是嵌套正文。

</details>

</details>

<details open>
<summary>默认展开：点击可以折叠</summary>

这里是默认展开的详情正文。

~~~java
String message = "详情里的代码块";
~~~

</details>

注释前的正文。

<!-- 这是 HTML 注释，其中的 **粗体**、:smile:、<details> 都不应作为正文渲染。 -->

注释后的正文。

有限 HTML 行内样式：<u>下划线</u>、<mark>高亮</mark>、H<sub>2</sub>O、x<sup>2</sup>、<del>删除线</del>。

### 扩展格式、公式、脚注与目录

检查：完整排版预览中的高亮、上下标、公式、脚注编号和目录；编辑区中的源码应可以正常修改和复制。

==高亮文本==，H~2~O，x^2^。

行内公式：$a^2+b^2=c^2$。相邻文字：中文$E=mc^2$中文。

金额对照：$5、$10、USD 20 不应吞掉后面的正文。

$$
\begin{aligned}
f(x) &= x^2 + 2x + 1 \\
     &= (x+1)^2
\end{aligned}
$$

这里首次引用补充脚注[^补充说明]，这里再次引用同一个脚注[^补充说明]。这是另一个脚注[^补充多段]。

[^补充说明]: 这是脚注内容，包含 **粗体** 和 [链接](https://example.com)。

[^补充多段]: 这是第一段。

    这是第二段，包含 `inlineCode`。

    - 脚注中的列表一
    - 脚注中的列表二

独立一行的目录标记，检查完整预览中的标题导航：

[TOC]

### Front Matter 的开头位置

本文件开头的元数据使用 `---` 开始、`...` 结束，正文应从“测试阅读说明”开始。检查元数据背景、灰色文字、展开源码、查找字段和保存重开；里面的短码、Markdown 格式和链接应作为元数据文本保留。

另一种使用 `---` 结束的写法，复制到一个新文档的最开头检查；这里放在代码块中，仅用于展示输入：

~~~~markdown
---
title: "中文元数据示例"
tags:
  - markdown
  - 自测
description: "**不是粗体** :smile:"
---

# 元数据之后的正文
~~~~

### Mermaid 混排与错误回退

检查：流程图、时序图、甘特图能离线渲染，操作栏可切换图形和源码；相邻正文不被遮挡。试一下缩窄窗口、缩放、编辑节点名称和撤销。

~~~mermaid
%% 声明前的注释
flowchart LR
    A[开始] --> B{检查条件}
    B -->|是| C[保存]
    B -->|否| D[修改]
    D --> B
~~~

两张图之间的正文，不应被图表覆盖。

~~~MERMAID
sequenceDiagram
    participant U as 用户
    participant E as 编辑器
    U->>E: 打开文档
    E-->>U: 显示内容
    alt 内容已修改
        U->>E: 保存
    else 内容未修改
        E-->>U: 保持状态
    end
~~~

~~~mermaid
gantt
    title 中文项目计划
    dateFormat YYYY-MM-DD
    axisFormat %m-%d
    section 准备
    用例整理 :done, prep, 2026-10-04, 2d
    section 验证
    界面检查 :active, check, after prep, 3d
    问题修正 :fix, after check, 2d
    完成验收 :milestone, after fix, 0d
~~~

下面故意写错 Mermaid，用于检查提示和源码回退，错误不能影响下一段正文：

~~~mermaid
flowchart LR
    A[未闭合的节点 --> B
~~~

错误图表后的正文仍应可见、可编辑。

### 编辑、搜索、保存与滚动检查

以下是手动操作清单，不代表这些场景已经通过验收。执行前可以复制本文件，避免改变之后的基准对照。

- [ ] 在标题、粗体、下划线、引用和代码围栏中移动光标，检查标记展开与收起，不出现额外空白或跳行。
- [ ] 在列表和任务列表末尾按 Enter 续写，在空项目上再按 Enter，检查退层与退出；每一步试一下撤销和重做。
- [ ] 在不同代码块之前增删空行，查找 Hello World、下划线和注释，检查高亮不会错到上一行或下一行。
- [ ] 搜索源码中的 **、__、:smile:、补充链接和 title，检查隐藏标记可定位，复制出来仍是原始源码。
- [ ] 拖选跨过标题、正文、图片、表格、详情和图表，检查选区与复制内容一致。
- [ ] 在表格中输入中文，修改单元格格式，粘贴两行三列 TSV，再撤销；输入法候选词不应被刷新打断。
- [ ] 点击任务框并撤销重做；折叠详情并切换标签；检查状态、文本与滚动位置。
- [ ] 切换浅色和深色主题，缩放到 100%、150%、200%，检查引用、链接标识、表情、列表圆点和代码颜色。
- [ ] 缩窄窗口，检查长段落、长代码行、宽表格和 Mermaid 操作栏；纵向滑块应保持可见，横向滚动应能到达行尾。
- [ ] 另存为到其他目录后重开，检查本地图片和文档链接的处理；保存文件中不能混入显示层零宽占位字符。
- [ ] 切换只读模式，再尝试勾选任务与编辑；文档内容不应被修改。
- [ ] 在完整排版预览中检查公式、脚注和目录，导出 HTML 后比较内容与本地附件路径。
- [ ] 打开 02-GFM官方规范与用例.md 与 03-CommonMark官方规范与用例.md，检查万行文档的滚动条、查找和标签切换；本文件不替代大文档验收。

### 未完成语法的独立检查输入

下面的内容放在外层代码块中，避免未闭合结构吞掉本文件后面的用例。每种输入分别复制到一个新文档，检查未完成内容保持可编辑，补全后恢复正常显示。

未闭合粗体：

~~~~text
**等待继续输入
~~~~

未闭合链接：

~~~~text
[链接文字](
~~~~

未闭合 HTML 详情：

~~~~text
<details>
<summary>等待闭合</summary>
正文
~~~~

未闭合 Front Matter（必须放在新文档开头）：

~~~~text
---
title: "等待结束标记"
~~~~

未闭合公式：

~~~~text
$$
x^2+y^2
~~~~

未闭合代码围栏：

~~~~text
```java
String message = "等待闭合围栏";
~~~~
