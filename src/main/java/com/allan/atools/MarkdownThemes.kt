package com.allan.atools

import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import javafx.beans.property.ReadOnlyLongProperty
import javafx.beans.property.ReadOnlyLongWrapper
import javafx.css.CssParser
import javafx.css.CompoundSelector
import javafx.css.Selector
import javafx.css.SimpleSelector
import javafx.scene.Parent
import javafx.scene.paint.Color
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Markdown 主题目录、明暗偏好和编辑器局部样式统一管理；配色快照供弹窗和 HTML 预览共用。 */
object MarkdownThemes {
    data class Theme(
        val id: String,
        val name: String,
        val dark: Boolean,
        val path: Path,
        val builtIn: Boolean,
        val palette: Map<String, String>
    )

    private const val MAX_CSS_BYTES = 1024 * 1024L
    private val revision = ReadOnlyLongWrapper(0)
    private val editors = WeakHashMap<Parent, List<String>>()
    private var initialized = false
    @Volatile private var themes: List<Theme> = emptyList()
    @Volatile private var selectedLight = "github"
    @Volatile private var selectedDark = "github-dark"
    private var loadProblems: List<String> = emptyList()
    private val paletteSelector = Selector.createSelector(".markdown-editor")

    @JvmStatic fun revisionProperty(): ReadOnlyLongProperty = revision.readOnlyProperty
    @JvmStatic fun directory(): Path = Path.of(CacheLocation.get("markdown-themes"))
    @JvmStatic fun available(): List<Theme> = themes
    @JvmStatic fun problems(): List<String> = loadProblems
    @JvmStatic fun current(): Theme = resolve(Colors.isDark())

    @JvmStatic fun initialize() {
        if (initialized) return
        selectedLight = SettingPreferences.getStr(SettingPreferences.markdownLightThemeKey)
        selectedDark = SettingPreferences.getStr(SettingPreferences.markdownDarkThemeKey)
        reload()
        initialized = true
        SettingPreferences.getBoolProp(SettingPreferences.appVisionKey).addListener { _, _, _ -> apply() }
        for (key in listOf(SettingPreferences.markdownLightThemeKey, SettingPreferences.markdownDarkThemeKey)) {
            SettingPreferences.getStringProp(key).addListener { _, _, _ ->
                selectedLight = SettingPreferences.getStr(SettingPreferences.markdownLightThemeKey)
                selectedDark = SettingPreferences.getStr(SettingPreferences.markdownDarkThemeKey)
                apply()
            }
        }
    }

    @JvmStatic fun attachEditor(editor: Parent) {
        initialize()
        editors.putIfAbsent(editor, emptyList())
        val theme = current()
        val base = themes.first { it.builtIn && it.dark == theme.dark }
        val urls = listOf(base, theme).distinctBy { it.id }
            .map { "${it.path.toUri()}?atools-theme=${revision.get()}" }
        editor.stylesheets.removeAll(editors[editor].orEmpty().toSet())
        editor.stylesheets.addAll(urls)
        editors[editor] = urls
        editor.applyCss()
        editor.requestLayout()
    }

    @JvmStatic fun select(theme: Theme) {
        if (theme.dark != Colors.isDark() || themes.none { it.id == theme.id && it.dark == theme.dark }) return
        val key = if (theme.dark) SettingPreferences.markdownDarkThemeKey else SettingPreferences.markdownLightThemeKey
        SettingPreferences.updateStr(key, theme.id)
    }

    @JvmStatic fun reload() {
        val loaded = arrayListOf(
            readTheme(Path.of(ResLocation.getRealPath("css", "markdown-themes", "github.css")), true),
            readTheme(Path.of(ResLocation.getRealPath("css", "markdown-themes", "github-dark.css")), true)
        )
        val problems = ArrayList<String>()
        val folder = directory()
        if (Files.isDirectory(folder)) {
            try {
                Files.list(folder).use { paths ->
                    paths.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase(Locale.ROOT).endsWith(".css") }
                        .sorted().forEach { path ->
                            try {
                                val theme = readTheme(path, false)
                                val base = loaded.first { it.builtIn && it.dark == theme.dark }
                                loaded.add(theme.copy(palette = base.palette + theme.palette))
                            }
                            catch (error: Exception) {
                                problems.add("${path.fileName}: ${error.message}")
                                Log.e("Load markdown theme failed: $path", error)
                            }
                        }
                }
            } catch (error: Exception) {
                problems.add("$folder: ${error.message}")
                Log.e("Read markdown theme directory failed", error)
            }
        }
        themes = loaded.toList()
        loadProblems = problems.toList()
        apply()
    }

    @JvmStatic fun importCss(source: Path): Theme {
        readTheme(source, false)
        val folder = directory()
        Files.createDirectories(folder)
        val name = source.fileName.toString()
        var destination = folder.resolve(name)
        // 同名导入保留已有文件；编辑主题目录中的原文件后重新加载即可替换。
        if (source.toAbsolutePath().normalize() != destination.toAbsolutePath().normalize()) {
            var suffix = 2
            while (Files.exists(destination)) {
                destination = folder.resolve("${name.substringBeforeLast('.')}-${suffix++}.css")
            }
            Files.copy(source, destination)
        }
        reload()
        return themes.first { !it.builtIn && it.path == destination }
    }

    @JvmStatic fun exportTemplate(destination: Path) {
        val template = themes.first { it.builtIn && it.dark == Colors.isDark() }
        val text = Files.readString(template.path)
            .replace("ATools-Theme-Name: ${template.name}", "ATools-Theme-Name: My Markdown Theme")
        Files.writeString(destination, text)
    }

    private fun readTheme(path: Path, builtIn: Boolean): Theme {
        require(Files.size(path) <= MAX_CSS_BYTES) { Locales.str("markdown.themeTooLarge") }
        val text = Files.readString(path).removePrefix("\uFEFF")
        val header = Regex("^\\s*/\\*(.*?)\\*/", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            ?: throw IllegalArgumentException(Locales.str("markdown.themeMetadataRequired"))
        val metadata = header.lineSequence().map { it.trim().removePrefix("*").trim() }
            .filter { ':' in it }.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        fun metadata(key: String): String? = metadata.entries.firstOrNull { it.key.equals(key, true) }?.value
        val mode = metadata("ATools-Theme-Mode")?.lowercase(Locale.ROOT)
        require(mode == "light" || mode == "dark") { Locales.str("markdown.themeMetadataRequired") }
        val name = metadata("ATools-Theme-Name")?.takeIf { it.isNotBlank() } ?: path.fileName.toString().substringBeforeLast('.')
        val errors = CssParser.errorsProperty()
        val before = errors.size
        val stylesheet = CssParser().parse(path.toUri().toString(), text)
        require(errors.size == before && stylesheet.rules.isNotEmpty()) { Locales.str("markdown.themeInvalidCss") }
        // 字体注册不受节点范围限制，主题使用应用现有字体或已安装的系统字体。
        require(stylesheet.fontFaces.isEmpty()) { Locales.str("markdown.themeScopeRequired") }
        val palette = LinkedHashMap<String, String>()
        val importantColors = HashSet<String>()
        for (rule in stylesheet.rules) {
            for (selector in rule.selectors) {
                val first = when (selector) {
                    is SimpleSelector -> selector
                    is CompoundSelector -> selector.selectors.firstOrNull()
                    else -> null
                }
                require(first != null && "markdown-editor" in first.styleClasses) {
                    Locales.str("markdown.themeScopeRequired")
                }
            }
            for (declaration in rule.declarations) {
                val key = declaration.property
                if (!key.startsWith("-au-")) continue
                // 配色必须来自无条件的独立选择器，避免预览采用编辑器未匹配的条件规则。
                require(rule.selectors.size == 1 && rule.selectors[0] == paletteSelector) {
                    Locales.str("markdown.themePaletteRequired")
                }
                val value = declaration.parsedValue
                val color = if (value.isContainsLookups) null else value.convert(null) as? Color
                require(color != null) { "${Locales.str("markdown.themeInvalidColor")} $key" }
                if (key in importantColors && !declaration.isImportant) continue
                if (declaration.isImportant) importantColors.add(key)
                palette[key] = String.format(Locale.ROOT, "#%02x%02x%02x%02x",
                    (color.red * 255).roundToInt(), (color.green * 255).roundToInt(),
                    (color.blue * 255).roundToInt(), (color.opacity * 255).roundToInt())
            }
        }
        require(palette.isNotEmpty()) { Locales.str("markdown.themePaletteRequired") }
        val id = if (builtIn) path.fileName.toString().substringBeforeLast('.') else "user:${path.fileName}"
        return Theme(id, name, mode == "dark", path, builtIn, palette.toMap())
    }

    private fun resolve(dark: Boolean): Theme {
        val saved = if (dark) selectedDark else selectedLight
        return themes.firstOrNull { it.id == saved && it.dark == dark }
            ?: themes.first { it.builtIn && it.dark == dark }
    }

    private fun apply() {
        revision.set(revision.get() + 1)
        editors.keys.toList().forEach(::attachEditor)
    }

    /** HTML 仅同步主题配色；JavaFX 专用的控件选择器仍交给编辑器应用。 */
    @JvmStatic fun previewCss(dark: Boolean): String {
        val palette = resolve(dark).palette
        fun color(key: String) = palette.getValue(key)
        val result = StringBuilder("body.${if (dark) "dark" else "light"}{color-scheme:${if (dark) "dark" else "light"};")
        val variables = mapOf(
            "background" to "-au-editor-bg-color", "foreground" to "-au-editor-text-color",
            "muted" to "-au-md-quote", "border" to "-au-md-table-mark", "code" to "-au-md-code-bg",
            "link" to "-au-md-link", "heading-border" to "-au-md-heading-border", "inline-code" to "-au-md-inline-code-bg",
            "inline-fill" to "-au-md-inline-code-fill", "quote-bg" to "-au-md-quote-bg",
            "quote-border" to "-au-md-quote-border", "table-alternate" to "-au-md-table-alternate",
            "highlight" to "-au-md-highlight"
        )
        variables.forEach { (name, key) -> result.append("--$name:${color(key)};") }
        result.append("}")
        for (level in 1..6) result.append("h$level{color:${color("-au-md-title-$level")};}")
        for (token in listOf("keyword", "string", "comment", "punct", "tag", "tagmark", "attribute", "attribute-value")) {
            result.append("body .token-$token{color:${color("-au-md-code-$token")};}")
        }
        result.append("pre,pre code{color:${color("-au-md-code-fill")};}")
        result.append("body ::selection{background:${color("-au-editor-selection-words-color")};}")
        return result.toString()
    }
}
