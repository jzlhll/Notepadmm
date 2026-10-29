package com.allan.atools

import com.allan.atools.utils.Locales
import com.allan.atools.utils.ResLocation
import javafx.css.CssParser
import javafx.css.Selector
import javafx.scene.paint.Color
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.roundToInt

enum class AppTheme(val id: String, val dark: Boolean, val cssFile: String, private val nameKey: String) {
    ISLANDS_LIGHT("islands-light", false, "colors.css", "setting.themeIslandsLight"),
    ISLANDS_DARK("islands-dark", true, "colors_dark.css", "setting.themeIslandsDark"),
    ISLANDS_DRACULA("islands-dracula-colorful", true, "colors_dracula.css", "setting.themeDracula"),
    EYE_CARE("eye-care", false, "colors_eye_care.css", "setting.themeEyeCare"),
    ALUCARD("alucard", false, "colors_alucard.css", "setting.themeAlucard");

    // 程序绘制与 HTML 预览共用 CSS 色板，避免主题存在多套颜色来源。
    private val palettes by lazy {
        val path = Path.of(ResLocation.getRealPath("css", cssFile))
        val stylesheet = CssParser().parse(path.toUri().toString(), Files.readString(path))
        listOf(".root", ".markdown-editor").associateWith { selectorText ->
            val selector = Selector.createSelector(selectorText)
            stylesheet.rules.filter { selector in it.selectors }.flatMap { it.declarations }
                .filter { it.property.startsWith("-au-") }
                .associate { declaration ->
                    val color = declaration.parsedValue.convert(null) as Color
                    declaration.property to String.format(Locale.ROOT, "#%02x%02x%02x%02x",
                        (color.red * 255).roundToInt(), (color.green * 255).roundToInt(),
                        (color.blue * 255).roundToInt(), (color.opacity * 255).roundToInt())
                }
        }
    }

    private val markdownPalette: Map<String, String> get() = palettes.getValue(".markdown-editor")

    fun color(key: String): Color = Color.valueOf(palettes.getValue(".root").getValue(key))

    override fun toString(): String = Locales.str(nameKey)

    companion object {
        @JvmStatic
        fun current(): AppTheme = entries.firstOrNull {
            it.id == SettingPreferences.getStr(SettingPreferences.appThemeKey)
        } ?: ISLANDS_LIGHT

        /** HTML 预览沿用应用主题；打印与 PDF 可指定使用浅色纸面。 */
        @JvmStatic fun previewCss(dark: Boolean): String {
            val current = current()
            val theme = if (current.dark == dark) current else if (dark) ISLANDS_DARK else ISLANDS_LIGHT
            val palette = theme.markdownPalette
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
}
