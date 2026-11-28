package com.allan.atools.richtext.codearea

import com.allan.atools.GlobalCfgStores
import com.allan.atools.pop.AppContextMenu
import com.allan.atools.utils.Locales
import javafx.geometry.Side
import javafx.scene.control.Button
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Menu
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.Tooltip
import javafx.scene.layout.Region

/** 在窄侧栏内共用一个设置入口，并按模型能力生成本次请求的配置。 */
class CodexOptionsButton : Button(Locales.str("editor.codex.options")) {
    private val store = GlobalCfgStores.user()
    private val levels = listOf("medium", "high", "xhigh")
    private var assistant = SelectionAssistant.CODEX
    private val modelKey get() = if (assistant == SelectionAssistant.CODEX) "codexModel" else "opencodeModel"
    private var models = emptyList<CodexSelectionCli.Model>()
    private var model: CodexSelectionCli.Model? = null
    private var preferredEffort = store.getString("codexReasoningEffort", "medium")
    private var preferFast = store.getBoolean("codexFast", false)
    private val efforts get() = if (assistant == SelectionAssistant.CODEX) {
        model?.let { levels.filter(it.efforts::contains) } ?: levels
    } else emptyList()
    private val effort get() = preferredEffort.takeIf { it in efforts } ?: efforts.firstOrNull()
    private val fast get() = preferFast && model?.fast == true

    init {
        minWidth = 0.0
        styleClass.add("codex-options")
        graphic = Region().apply { styleClass.add("codex-options-arrow") }
        contentDisplay = ContentDisplay.RIGHT
        setOnAction { showOptions() }
        updateDescription()
    }

    fun setCatalog(tool: SelectionAssistant, available: List<CodexSelectionCli.Model>) {
        assistant = tool
        models = available
        val saved = store.getString(modelKey, "")
        model = models.firstOrNull { it.id == saved } ?: models.firstOrNull()?.takeIf { tool == SelectionAssistant.CODEX }
        updateDescription()
    }

    fun snapshot() = CodexSelectionCli.Options(model?.id, effort, fast)

    private fun updateDescription() {
        val summary = listOfNotNull(model?.name ?: Locales.str("editor.codex.defaultModel"),
            effort?.let { Locales.str("editor.codex.$it") }, Locales.str("editor.codex.fast").takeIf { fast })
        accessibleText = text + " · " + summary.joinToString(" · ")
        tooltip = Tooltip(accessibleText)
    }

    private fun showOptions() {
        val choices = if (assistant == SelectionAssistant.OPENCODE) listOf(null) + models else models
        val modelItems = choices.map { choice ->
            RadioMenuItem(choice?.name ?: Locales.str("editor.codex.defaultModel")).apply {
                isSelected = choice == model
                setOnAction {
                    model = choice
                    store.setString(modelKey, choice?.id.orEmpty())
                    updateDescription()
                }
            }
        }
        if (assistant == SelectionAssistant.OPENCODE) {
            AppContextMenu(*modelItems.toTypedArray()).show(this, Side.TOP, 0.0, -4.0)
            return
        }
        val modelMenu = Menu(Locales.str("editor.codex.model") + " · " +
            (model?.name ?: Locales.str("editor.codex.defaultModel"))).apply {
            items.setAll(modelItems)
            isDisable = models.isEmpty()
        }
        val effortTitle = listOfNotNull(Locales.str("editor.codex.effort"),
            effort?.let { Locales.str("editor.codex.$it") }).joinToString(" · ")
        val effortMenu = Menu(effortTitle).apply {
            isDisable = efforts.isEmpty()
            for (level in efforts) {
                items.add(RadioMenuItem(Locales.str("editor.codex.$level")).apply {
                    isSelected = level == effort
                    setOnAction {
                        preferredEffort = level
                        store.setString("codexReasoningEffort", level)
                        updateDescription()
                    }
                })
            }
        }
        val fastItem = CheckMenuItem(Locales.str("editor.codex.fast")).apply {
            isDisable = model?.fast != true
            isSelected = fast
            setOnAction {
                preferFast = isSelected
                store.setBoolean("codexFast", preferFast)
                updateDescription()
            }
        }
        AppContextMenu(modelMenu, effortMenu, fastItem).show(this, Side.TOP, 0.0, -4.0)
    }
}
