package com.allan.atools.richtext.codearea.keywordhelper

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.regex.Pattern

/** 编辑器和 HTML 输出共用代码词法着色；字符串、注释及嵌入语言保持各自边界。 */
object MarkdownCodeLanguages {
    data class Token(val start: Int, val end: Int, val style: String)

    private val patterns = ConcurrentHashMap<String, Pattern>()
    private const val IDENTIFIER = "[\\p{L}_$][\\p{L}\\p{N}_$]*"
    private const val DOUBLE = "\"(?:\\\\[\\s\\S]|[^\"\\\\\\r\\n])*+(?:\"|(?=\\r?\\n)|\\z)"
    private const val SINGLE = "'(?:\\\\[\\s\\S]|[^'\\\\\\r\\n])*+(?:'|(?=\\r?\\n)|\\z)"
    private const val TRIPLE = "\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"
    private const val C_COMMENT = "//[^\\r\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)"
    private const val NUMBER = "(?<![\\p{L}\\p{N}_$])(?:0[xX][\\da-fA-F][\\da-fA-F_']*(?:\\.[\\da-fA-F_']*)?(?:[pP][+-]?\\d[\\d_]*)?|0[bB][01][01_']*|(?:\\d[\\d_']*(?:\\.\\d[\\d_']*)?|\\.\\d[\\d_']*)(?:[eE][+-]?\\d[\\d_]*)?)[uUlLfFdDnN]*(?![\\p{L}\\p{N}_$])"
    private const val DISABLED = "(?!)"
    private const val GRADLE_CALLS = "\\b(?:plugins|repositories|dependencies|buildscript|allprojects|subprojects|tasks|android|java|kotlin|sourceSets|publishing|configurations|maven|mavenCentral|google|implementation|api|compileOnly|runtimeOnly|testImplementation|id|classpath)\\b"
    private val markup = Pattern.compile(
        "(?<COMMENT><!--[\\s\\S]*?(?:-->|\\z))|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?(?:]]>|\\z))" +
            "|(?<PROCESSING><\\?[\\s\\S]*?(?:\\?>|\\z))|(?<DOCTYPE><!DOCTYPE(?:\"[^\"]*\"|'[^']*'|\\[[\\s\\S]*?]|[^>])*>)" +
            "|(?<ELEMENT>(?<OPEN></?\\s*)(?<TAG>[\\p{L}_][\\p{L}\\p{N}_.:-]*)(?<ATTRS>(?:\"[^\"]*\"|'[^']*'|[^\"'<>])*?)(?<CLOSE>/?>))" +
            "|(?<ENTITY>&(?:#[0-9]+|#x[0-9a-f]+|[a-z][a-z0-9]+);)", Pattern.CASE_INSENSITIVE)
    private val attributes = Pattern.compile(
        "(?<NAME>[^\\s=\"'<>/]+)(?:\\s*(?<EQUAL>=)\\s*(?<VALUE>\"[^\"]*\"|'[^']*'|[^\\s>]+))?")

    @JvmStatic
    fun supportedLanguages(): List<String> = listOf("java", "kotlin", "groovy", "swift", "objective-c",
        "c", "cpp", "go", "csharp", "xml", "html", "css", "javascript", "typescript", "python", "json",
        "shell", "sql", "yaml", "toml", "ini", "dockerfile", "powershell", "rust", "dart", "ruby", "php", "lua", "protobuf", "diff")

    @JvmStatic
    fun normalize(info: String?): String? {
        val name = info?.trim()?.takeWhile { !it.isWhitespace() }?.lowercase(Locale.ROOT)
        return when (name) {
            "java" -> "java"
            "kotlin", "kt", "kts" -> "kotlin"
            "gradle.kts", "build.gradle.kts", "kotlin-gradle", "gradle-kotlin" -> "kotlin"
            "groovy", "goovy", "gradle", "build.gradle", "gvy" -> "groovy"
            "swift" -> "swift"
            "objective-c", "object-c", "objc", "objectivec", "obj-c", "objective-c++", "objcpp", "m", "mm" -> "objective-c"
            "c", "h" -> "c"
            "cpp", "c++", "cc", "cxx", "hpp", "hxx" -> "cpp"
            "go", "golang" -> "go"
            "csharp", "cs", "c#" -> "csharp"
            "xml", "svg" -> "xml"
            "html", "htm", "xhtml" -> "html"
            "css", "scss", "less" -> "css"
            "javascript", "js", "jsx" -> "javascript"
            "typescript", "ts", "tsx" -> "typescript"
            "python", "py" -> "python"
            "json", "jsonc" -> "json"
            "shell", "sh", "bash", "zsh" -> "shell"
            "sql" -> "sql"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "ini", "cfg", "properties", "dotenv", "env" -> "ini"
            "dockerfile", "docker" -> "dockerfile"
            "powershell", "ps1", "pwsh", "psm1" -> "powershell"
            "rust", "rs" -> "rust"
            "dart" -> "dart"
            "ruby", "rb" -> "ruby"
            "php" -> "php"
            "lua" -> "lua"
            "protobuf", "proto", "proto3" -> "protobuf"
            "diff", "patch" -> "diff"
            else -> null
        }
    }

    @JvmStatic
    fun pattern(language: String): Pattern? {
        val normal = normalize(language) ?: return null
        if (normal == "xml" || normal == "html") return markup
        return patterns.computeIfAbsent(normal, ::createPattern)
    }

    @JvmStatic
    fun forEachToken(source: String, info: String, canContinue: BooleanSupplier, consumer: Consumer<Token>) {
        val language = normalize(info) ?: return
        if (language == "xml" || language == "html") scanMarkup(source, language, canContinue, consumer)
        else scanCode(source, language, canContinue, consumer)
    }

    private fun scanCode(source: String, language: String, canContinue: BooleanSupplier, consumer: Consumer<Token>) {
        val matcher = requireNotNull(pattern(language)).matcher(source)
        var cursor = 0
        var count = 0
        while (matcher.find(cursor)) {
            if ((count++ and 255) == 0 && !canContinue.asBoolean) return
            val style = when {
                matcher.group("ATTRIBUTE") != null -> "attribute"
                matcher.group("STRING") != null -> "string"
                matcher.group("COMMENT") != null -> "comment"
                matcher.group("KEYWORD") != null -> "keyword"
                matcher.group("VALUE") != null || matcher.group("NUMBER") != null -> "attribute-value"
                matcher.group("FUNCTION") != null -> "attribute"
                matcher.group("TYPE") != null -> "tag"
                else -> "punct"
            }
            var end = matcher.end()
            if (style == "comment" && (language == "kotlin" || language == "swift" || language == "rust") && source.startsWith("/*", matcher.start())) {
                // 支持嵌套块注释的语言不能在第一个 */ 后把注释正文当成代码。
                var depth = 1
                end = matcher.start() + 2
                while (end < source.length && depth > 0) {
                    if ((end and 1023) == 0 && !canContinue.asBoolean) return
                    when {
                        source.startsWith("/*", end) -> { depth++; end += 2 }
                        source.startsWith("*/", end) -> { depth--; end += 2 }
                        else -> end++
                    }
                }
            }
            if (language == "yaml" && matcher.group("YAMLBLOCK") != null) {
                end = yamlBlockEnd(source, matcher.start(), end, matcher.group("YAMLBLOCK"), canContinue)
                if (!canContinue.asBoolean) return
            }
            consumer.accept(Token(matcher.start(), end, style))
            cursor = end
            if (cursor >= source.length) break
        }
    }

    private fun yamlBlockEnd(source: String, start: Int, headerEnd: Int, header: String, canContinue: BooleanSupplier): Int {
        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        var parentIndent = 0
        while (source.getOrNull(lineStart + parentIndent) == ' ') parentIndent++
        if (source.substring(lineStart, start).trimEnd().endsWith(':')) {
            // 序列项中的映射键还包含 “- ” 的缩进，显式缩进指示符须从映射键的位置计算。
            while (source.getOrNull(lineStart + parentIndent) == '-' && source.getOrNull(lineStart + parentIndent + 1) == ' ') {
                parentIndent++
                while (source.getOrNull(lineStart + parentIndent) == ' ') parentIndent++
            }
        }
        val indicator = header.takeWhile { !it.isWhitespace() }.firstOrNull { it in '1'..'9' }
        var contentIndent = indicator?.let { parentIndent + it.digitToInt() }
        var end = headerEnd
        // 块标量内的 #、冒号、布尔值都是正文，按缩进结束整段字符串。
        var cursor = source.indexOf('\n', headerEnd).let { if (it < 0) source.length else it + 1 }
        while (cursor < source.length && canContinue.asBoolean) {
            val next = source.indexOf('\n', cursor).let { if (it < 0) source.length else it }
            val line = source.substring(cursor, next).trimEnd('\r')
            val indent = line.takeWhile { it == ' ' }.length
            if (line.isNotBlank()) {
                if (indent <= parentIndent || contentIndent != null && indent < contentIndent) break
                if (contentIndent == null) contentIndent = indent
            }
            end = next
            cursor = next + 1
        }
        return end
    }

    private fun scanMarkup(source: String, language: String, canContinue: BooleanSupplier, consumer: Consumer<Token>) {
        val matcher = markup.matcher(source)
        var cursor = 0
        while (matcher.find(cursor)) {
            if (!canContinue.asBoolean) return
            when {
                matcher.group("COMMENT") != null -> consumer.accept(Token(matcher.start(), matcher.end(), "comment"))
                matcher.group("CDATA") != null -> consumer.accept(Token(matcher.start(), matcher.end(), "string"))
                matcher.group("PROCESSING") != null || matcher.group("DOCTYPE") != null -> consumer.accept(Token(matcher.start(), matcher.end(), "tagmark"))
                matcher.group("ENTITY") != null -> consumer.accept(Token(matcher.start(), matcher.end(), "attribute-value"))
                else -> {
                    consumer.accept(Token(matcher.start("OPEN"), matcher.end("OPEN"), "tagmark"))
                    consumer.accept(Token(matcher.start("TAG"), matcher.end("TAG"), "tag"))
                    val offset = matcher.start("ATTRS")
                    val values = attributes.matcher(matcher.group("ATTRS"))
                    while (values.find()) {
                        if (!canContinue.asBoolean) return
                        consumer.accept(Token(offset + values.start("NAME"), offset + values.end("NAME"), "attribute"))
                        if (values.group("EQUAL") != null) consumer.accept(Token(offset + values.start("EQUAL"), offset + values.end("EQUAL"), "tagmark"))
                        if (values.group("VALUE") != null) consumer.accept(Token(offset + values.start("VALUE"), offset + values.end("VALUE"), "attribute-value"))
                    }
                    consumer.accept(Token(matcher.start("CLOSE"), matcher.end("CLOSE"), "tagmark"))
                }
            }
            cursor = matcher.end()
            val tag = matcher.group("TAG")?.lowercase(Locale.ROOT)
            if (language == "html" && (tag == "script" || tag == "style") && matcher.group("OPEN") == "<" && matcher.group("CLOSE") != "/>") {
                val close = Pattern.compile("</\\s*$tag\\s*>", Pattern.CASE_INSENSITIVE).matcher(source)
                val end = if (close.find(cursor)) close.start() else source.length
                val embedded = if (tag == "style") "css" else if (matcher.group("ATTRS").contains("application/json", true)) "json" else "javascript"
                val offset = cursor
                scanCode(source.substring(cursor, end), embedded, canContinue) { token ->
                    consumer.accept(Token(offset + token.start, offset + token.end, token.style))
                }
                cursor = end
            }
            if (cursor >= source.length) break
        }
    }

    private fun createPattern(language: String): Pattern {
        var strings = "$DOUBLE|$SINGLE"
        var comments = C_COMMENT
        var attribute = DISABLED
        var value = DISABLED
        var types = "(?<![\\p{L}\\p{N}_$])(?:[A-Z][\\p{L}\\p{N}_]*|bool|string|int|int8|int16|int32|int64|uint|uint8|uint16|uint32|uint64|float32|float64|byte|rune|error)\\b"
        var functions = "$IDENTIFIER(?=\\s*\\()"
        var numbers = NUMBER
        var extraKeywords = DISABLED
        val words: String
        when (language) {
            "java" -> {
                words = "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while record var yield sealed permits non-sealed true false null _"
                strings = "$TRIPLE|$strings"
                extraKeywords = "@$IDENTIFIER"
            }
            "kotlin" -> {
                words = "as? as !in !is break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set setparam value where abstract actual annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg it"
                strings = "$TRIPLE|$strings"
                extraKeywords = "@$IDENTIFIER"
                functions += "|$GRADLE_CALLS"
            }
            "groovy" -> {
                words = "abstract as assert boolean break byte case catch char class const continue def default do double else enum extends false final finally float for goto if implements import in instanceof int interface long native new null package private protected public return short static strictfp super switch synchronized this throw throws trait transient true try var void volatile while"
                strings = "\\$/[\\s\\S]*?(?:/\\$|\\z)|$TRIPLE|'''[\\s\\S]*?(?:'''|\\z)|$strings"
                extraKeywords = "@$IDENTIFIER"
                // Gradle 的 Groovy DSL 常用闭包形式调用，补上没有括号的入口。
                functions += "|$GRADLE_CALLS"
            }
            "rust" -> {
                words = "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type union unsafe use where while abstract become box do final macro override priv typeof unsized virtual yield try"
                strings = "b?r(?<RUSTHASH>#{0,16})\"[\\s\\S]*?\"\\k<RUSTHASH>|b?$DOUBLE|b?'(?:\\\\[\\s\\S]|[^'\\r\\n])'"
                value = "'[a-zA-Z_][\\w]*"
                numbers = "(?<![\\p{L}\\p{N}_])(?:0x[\\da-fA-F_]+|0o[0-7_]+|0b[01_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d[\\d_]*)?)(?:(?:u|i)(?:8|16|32|64|128|size)|f(?:32|64))?(?![\\p{L}\\p{N}_])"
                extraKeywords = "#!?\\[[^\\]\\r\\n]*\\]|$IDENTIFIER!"
            }
            "dart" -> {
                words = "abstract as assert async await base break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory false final finally for Function get hide if implements import in interface is late library mixin new null of on operator part required rethrow return sealed set show static super switch sync this throw true try typedef var void when while with yield"
                strings = "r?(?:$TRIPLE|'''[\\s\\S]*?(?:'''|\\z)|$strings)"
                extraKeywords = "@$IDENTIFIER(?:\\.$IDENTIFIER)*"
            }
            "ruby" -> {
                words = "BEGIN END alias and begin break case class def defined? do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield"
                strings = "%[qQwWiIxr]?\\{[^}]*}|%[qQwWiIxr]?\\([^)]*\\)|$strings"
                comments = "^=begin\\b[\\s\\S]*?(?:^=end\\b|\\z)|#[^\\r\\n]*"
                value = "(?<!:):$IDENTIFIER|[@$]{1,2}$IDENTIFIER"
            }
            "php" -> {
                words = "abstract and array as break callable case catch class clone const continue declare default die do echo else elseif empty enddeclare endfor endforeach endif endswitch endwhile enum eval exit extends false final finally fn for foreach function global goto if implements include include_once instanceof insteadof interface isset list match namespace new null or print private protected public readonly require require_once return static switch throw trait true try unset use var while xor yield self parent"
                comments = "$C_COMMENT|#[^\\r\\n]*"
                strings = "<<<\\h*['\"]?(?<PHPHEREDOC>[a-zA-Z_]\\w*)['\"]?\\h*\\R[\\s\\S]*?^\\h*\\k<PHPHEREDOC>\\b|$strings"
                value = "\\$$IDENTIFIER"
                extraKeywords = "<\\?(?:php|=)|\\?>"
            }
            "lua" -> {
                words = "and break do else elseif end false for function goto if in local nil not or repeat return then true until while"
                comments = "--\\[(?<LUACOMMENT>=*)\\[[\\s\\S]*?(?:]\\k<LUACOMMENT>]|\\z)|--[^\\r\\n]*"
                strings = "\\[(?<LUASTRING>=*)\\[[\\s\\S]*?(?:]\\k<LUASTRING>]|\\z)|$strings"
                types = DISABLED
            }
            "c", "cpp", "objective-c" -> {
                words = "alignas alignof asm auto bool break case catch char char8_t char16_t char32_t class compl concept const consteval constexpr constinit const_cast continue co_await co_return co_yield decltype default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new noexcept not not_eq nullptr operator or or_eq private protected public register reinterpret_cast requires restrict return short signed sizeof static static_assert static_cast struct switch template this thread_local throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while xor xor_eq and and_eq bitand bitor typeof typeof_unqual _Alignas _Alignof _Atomic _Bool _Complex _Generic _Imaginary _Noreturn _Static_assert _Thread_local" +
                    if (language == "objective-c") " YES NO nil Nil self super id instancetype nonatomic atomic strong weak copy assign retain readonly readwrite nullable nonnull" else ""
                extraKeywords = "#\\h*(?:include|import|define|undef|if|ifdef|ifndef|elif|else|endif|pragma|error|warning|line)\\b"
                if (language == "cpp" || language == "objective-c") strings = "(?:u8|u|U|L)?R\"(?<RAWDELIMITER>[^()\\s\\\\]{0,16})\\([\\s\\S]*?\\)\\k<RAWDELIMITER>\"|$strings"
                if (language == "objective-c") {
                    extraKeywords += "|@(?:interface|implementation|end|protocol|property|synthesize|dynamic|class|selector|encode|autoreleasepool|try|catch|finally|throw|synchronized|optional|required|public|private|protected|package|import|available)\\b"
                    strings = "@$DOUBLE|$strings"
                    functions = "$IDENTIFIER(?=\\s*[(:])"
                }
            }
            "csharp" -> {
                words = "abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while async await var dynamic record init required with global file yield partial get set value"
                strings = "@\"(?:\"\"|[^\"])*\"|$TRIPLE|$strings"
            }
            "swift" -> {
                words = "actor associatedtype async await borrowing break case catch class consuming continue convenience default defer deinit didSet distributed do dynamic else enum extension fallthrough false fileprivate final for func get guard if import in indirect infix init inout internal is isolated lazy let macro mutating nil nonisolated nonmutating open operator optional override package postfix precedencegroup prefix private protocol public repeat required rethrows return self Self set some static struct subscript super switch throws throw true try typealias unowned var weak where while willSet any as"
                strings = "(?<SWIFTHASH>#{1,16})(?:\"\"\"[\\s\\S]*?\"\"\"|\"[\\s\\S]*?\")\\k<SWIFTHASH>|$TRIPLE|$DOUBLE"
                extraKeywords = "@$IDENTIFIER|#(?:available|unavailable|selector|keyPath|if|elseif|else|endif|warning|error|file|line|function)\\b"
            }
            "go" -> {
                words = "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var true false nil iota"
                strings = "\\x60[^\\x60]*(?:\\x60|\\z)|$strings"
            }
            "javascript", "typescript", "json" -> {
                words = if (language == "json") "true false null" else "async await break case catch class const continue debugger default delete do else export extends false finally for from function if import in instanceof interface let new null of return static super switch this throw true try typeof undefined var void while with yield type implements private public protected readonly enum as abstract declare namespace keyof never unknown any number string boolean satisfies infer assert asserts module require"
                strings = "\\x60(?:\\\\[\\s\\S]|[^\\x60\\\\])*(?:\\x60|\\z)|$strings"
                if (language == "json") {
                    attribute = "$DOUBLE(?=\\h*:)"
                    types = DISABLED
                    functions = DISABLED
                }
            }
            "python" -> {
                words = "and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield match case"
                comments = "#[^\\r\\n]*"
                strings = "(?i:[rubf]{0,2})(?:$TRIPLE|'''[\\s\\S]*?(?:'''|\\z)|$strings)"
                extraKeywords = "@$IDENTIFIER(?:\\.$IDENTIFIER)*"
            }
            "shell" -> {
                words = "if then else elif fi for while do done case esac in function select until return export local readonly declare typeset unset echo printf read cd pwd source eval exec exit shift trap test true false break continue set"
                comments = "(?<!\\S)#[^\\r\\n]*"
                strings = "<<-?\\h*['\"]?(?<HEREDOCNAME>[A-Za-z_]\\w*)['\"]?[^\\r\\n]*\\R[\\s\\S]*?^\\t*\\k<HEREDOCNAME>\\h*$|\"(?:\\\\[\\s\\S]|[^\"\\\\])*(?:\"|\\z)|'[^']*(?:'|\\z)"
                value = "\\$(?:\\{[^}]*}|\\([\\s\\S]*?\\)|$IDENTIFIER|[0-9@*#?$!-])"
                types = DISABLED
            }
            "sql" -> {
                words = "select from where insert into update delete create alter drop table index view join inner left right outer cross on as and or not null is in exists values set group by order having limit offset distinct union all case when then else end with primary key foreign references begin commit rollback asc desc count sum avg min max like between cascade constraint default unique check database truncate replace returning over partition"
                comments = "--[^\\r\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)"
                strings = "'(?:''|[^'])*(?:'|\\z)|\"(?:\"\"|[^\"])*(?:\"|\\z)"
                types = DISABLED
            }
            "yaml" -> {
                words = "true false null yes no on off"
                comments = "(?<!\\S)#[^\\r\\n]*"
                strings = "(?<YAMLBLOCK>[|>][1-9+-]{0,3}\\h*(?:#[^\\r\\n]*)?)(?=\\r?$)|$DOUBLE|'(?:''|[^'])*(?:'|\\z)"
                attribute = "(?:$DOUBLE|'(?:''|[^'])*'|[\\p{L}_][\\p{L}\\p{N}_. -]*)(?=\\h*:(?:\\h|$|[\\[{]))"
                value = "[&*][\\w.-]+|!!?[\\w:.-]+|!<[^>]+>"
                types = DISABLED
                functions = DISABLED
            }
            "toml" -> {
                words = "true false inf nan"
                strings = "$TRIPLE|'''[\\s\\S]*?(?:'''|\\z)|$strings"
                comments = "(?<!\\S)#[^\\r\\n]*"
                val key = "(?:[\\w-]+|$DOUBLE|'[^']*')"
                attribute = "$key(?:\\h*\\.\\h*$key)*(?=\\h*=)"
                value = "\\b\\d{4}-\\d{2}-\\d{2}(?:[Tt ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[Zz]|[+-]\\d{2}:\\d{2})?)?\\b"
                types = "^\\h*\\[{1,2}[^\\]\\r\\n]+\\]{1,2}"
                functions = DISABLED
            }
            "ini" -> {
                words = "true false null yes no on off"
                comments = "^\\h*[#;!][^\\r\\n]*"
                attribute = "^\\h*[\\p{L}\\p{N}_.-][^=:\\r\\n]*?(?=\\h*[=:])"
                value = "\\$\\{[^}]+}"
                types = "^\\h*\\[[^\\]\\r\\n]+\\]"
                functions = DISABLED
            }
            "dockerfile" -> {
                words = "FROM RUN CMD LABEL MAINTAINER EXPOSE ENV ADD COPY ENTRYPOINT VOLUME USER WORKDIR ARG ONBUILD STOPSIGNAL HEALTHCHECK SHELL AS"
                comments = "^\\h*#[^\\r\\n]*"
                value = "\\$(?:\\{[^}]+}|$IDENTIFIER)"
                types = DISABLED
                functions = DISABLED
            }
            "powershell" -> {
                words = "begin break catch class clean continue data define do dynamicparam else elseif end enum exit filter finally for foreach from function hidden if in param process return static switch throw trap try until using var while workflow"
                comments = "<#[\\s\\S]*?(?:#>|\\z)|#[^\\r\\n]*"
                strings = "@\"\\r?\\n[\\s\\S]*?^\"@|@'\\r?\\n[\\s\\S]*?^'@|\"(?:\\x60[\\s\\S]|[^\"\\x60])*+(?:\"|\\z)|'(?:''|[^'])*(?:'|\\z)"
                value = "\\$(?:\\{[^}]+}|[\\w:]+|[?^_$])"
                types = "\\[[\\w.]+]"
                functions = "[a-zA-Z]+-[a-zA-Z][\\w-]*|$functions"
                extraKeywords = "-[a-zA-Z][\\w-]*"
            }
            "protobuf" -> {
                words = "syntax import public weak package option message enum service rpc returns stream repeated optional required oneof map reserved extensions extend group to max true false double float int32 int64 uint32 uint64 sint32 sint64 fixed32 fixed64 sfixed32 sfixed64 bool string bytes"
                attribute = "$IDENTIFIER(?=\\h*=)"
            }
            "diff" -> {
                words = "diff index"
                strings = "^\\+[^\\r\\n]*"
                comments = DISABLED
                extraKeywords = "^-[^\\r\\n]*"
                types = "^@@[^\\r\\n]*|^\\*\\*\\*[^\\r\\n]*"
                numbers = DISABLED
                functions = DISABLED
            }
            "css" -> {
                words = "important inherit initial unset revert none auto transparent currentColor from to"
                comments = "/\\*[\\s\\S]*?(?:\\*/|\\z)"
                extraKeywords = "@[\\w-]+"
                attribute = "(?:--)?[a-zA-Z_][\\w-]*(?=\\s*:)"
                value = "#[0-9a-fA-F]{3,8}\\b|[+-]?(?:\\d*\\.)?\\d+(?:[eE][+-]?\\d+)?(?:%|[a-zA-Z]+)?"
                types = "[.#][a-zA-Z_][\\w-]*|:{1,2}[a-zA-Z_][\\w-]*"
                functions = "[a-zA-Z_][\\w-]*(?=\\s*\\()"
                numbers = DISABLED
            }
            else -> error("Unsupported code language: $language")
        }
        val keywords = words.split(' ').sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) }
        val boundedKeywords = "(?<![\\p{L}\\p{N}_$])(?:$keywords)(?![\\p{L}\\p{N}_$])"
        return Pattern.compile("(?<ATTRIBUTE>$attribute)|(?<STRING>$strings)|(?<COMMENT>$comments)" +
            "|(?<KEYWORD>$extraKeywords|$boundedKeywords)|(?<VALUE>$value)|(?<NUMBER>$numbers)" +
            "|(?<FUNCTION>$functions)|(?<TYPE>$types)|(?<PUNCT>[(){}\\[\\];:,.=+*/%<>!&|^~?-])",
            Pattern.MULTILINE or if (language in setOf("sql", "yaml", "ini", "dockerfile", "powershell")) Pattern.CASE_INSENSITIVE else 0)
    }
}
