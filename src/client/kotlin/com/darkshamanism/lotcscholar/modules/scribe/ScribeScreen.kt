package com.darkshamanism.lotcscholar.modules.scribe

import com.darkshamanism.lotcscholar.core.ClientContext
import com.darkshamanism.lotcscholar.integration.minecraft.MinecraftBookFormatter
import com.darkshamanism.lotcscholar.integration.minecraft.WritableBookHelper
import com.darkshamanism.lotcscholar.services.*
import com.darkshamanism.lotcscholar.ui.components.*
import com.darkshamanism.lotcscholar.ui.widgets.TabButton
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.Hand
import org.lwjgl.glfw.GLFW
import java.io.File

// ── Stendhal import helper ────────────────────────────────────────────────────

private object StendhalImport {
    fun booksDir(): File = FabricLoader.getInstance().gameDir
        .resolve(".config/stendhal/books").toFile()

    fun list(): List<File> = booksDir().listFiles { f -> f.extension == "txt" || f.extension == "book" }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun read(f: File): String = try { f.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
    fun nameOf(f: File): String = f.nameWithoutExtension
}

// ── Unicode character sets (matches Stendhal's selector) ─────────────────────

private val UNICODE_CHARS: List<Pair<String, String>> = listOf(
    // Symbols / hearts / misc
    "♥" to "Heart",      "☠" to "Skull",   "→" to "Arrow R",   "★" to "Star",
    "☢" to "Radioactive","⚠" to "Warning",  "☀" to "Sun",       "☺" to "Smiley",
    "♩" to "Note",       "✉" to "Envelope", "↗" to "NE Arrow",  "✂" to "Scissors",
    "♪" to "Music",      "♫" to "Music2",   "♬" to "Music3",    "♭" to "Flat",
    "♮" to "Natural",    "♯" to "Sharp",    "◆" to "Diamond",   "✿" to "Flower",
    "❀" to "Flower2",    "✦" to "Star4",    "≡" to "Lines",     "⏮" to "Skip B",
    "⏭" to "Skip F",     "⏩" to "Fast F",  "⏪" to "Fast B",   "⏫" to "Fast U",
    "⏬" to "Fast D",     "◀" to "Left",     "▲" to "Up",        "▼" to "Down",
    "▶" to "Right",      "▪" to "Sq Sm",    "■" to "Square",    "●" to "Circle",
    "⏻" to "Power",      "⚓" to "Anchor",  "⛉" to "No Entry",  "↙" to "SW Arrow",
    "✓" to "Check",      "⁈" to "?!",       "‽" to "Interro",   "‼" to "Double !",
    "⁇" to "??",         "♀" to "Female",   "♂" to "Male",      "☿" to "Mercury",
    "⚢" to "FF",         "⚣" to "MM",       "⚤" to "MF",        "⚥" to "FM",
    "⚲" to "Neuter",     "⚀" to "Die 1",   "⚁" to "Die 2",     "⚂" to "Die 3",
    "⚃" to "Die 4",      "⚄" to "Die 5",   "⚅" to "Die 6",     "⛆" to "Rain",
    "∞" to "Infinity",   "⌛" to "Hourglass","⌚" to "Watch",    "⚑" to "Flag",
    "⚐" to "Flag2",      "♻" to "Recycle",  "❄" to "Snowflake", "⛇" to "Poison",
    "⚡" to "Lightning",  "⚔" to "Swords",   "⚙" to "Gear",      "⚜" to "Fleur",
    "✒" to "Pen",        "✏" to "Pencil",   "←" to "Arrow L",   "↑" to "Arrow U",
    "↓" to "Arrow D",    "✝" to "Cross",    "↔" to "L-R Arrow", "☮" to "Peace",
    "Ω" to "Omega",      "α" to "Alpha",    "β" to "Beta",      "γ" to "Gamma",
    "δ" to "Delta",      "ε" to "Epsilon",  "ζ" to "Zeta",      "η" to "Eta",
    "θ" to "Theta",      "ι" to "Iota",     "κ" to "Kappa",     "λ" to "Lambda",
    "μ" to "Mu",         "ν" to "Nu",       "ξ" to "Xi",        "π" to "Pi",
    "ρ" to "Rho",        "σ" to "Sigma",    "τ" to "Tau",       "υ" to "Upsilon",
    "φ" to "Phi",        "χ" to "Chi",      "ψ" to "Psi",       "ω" to "omega",
    "☽" to "Moon",       "✶" to "6-Star",   "✵" to "5-Star",    "✴" to "8-Star",
    "☁" to "Cloud",      "⛅" to "P.Cloudy", "☂" to "Umbrella",  "☃" to "Snowman",
    "☻" to "Smiley2",    "☼" to "Sun2",     "⌂" to "House",     "€" to "Euro",
    "Ї" to "Yi",         "Ё" to "Yo",       "З" to "Ze",        "Э" to "E",
    "Ж" to "Zhe",        "Ш" to "Sha",      "ц" to "tse",       "¢" to "Cent",
    "З" to "Ze2",        "Ξ" to "Xi2",      "Ж" to "Zhe2",      "Э" to "E2",
    "ж" to "zhe",        "ш" to "sha",      "э" to "e",         "Θ" to "Theta2",
)

// ── Formatting codes ──────────────────────────────────────────────────────────

private data class FmtCode(val code: String, val label: String, val color: Int)

private val FORMAT_COLORS: List<FmtCode> = listOf(
    FmtCode("§0", "■", 0xFF000000.toInt()), FmtCode("§8", "■", 0xFF555555.toInt()),
    FmtCode("§7", "■", 0xFFAAAAAA.toInt()), FmtCode("§f", "■", 0xFFFFFFFF.toInt()),
    FmtCode("§5", "■", 0xFFAA00AA.toInt()), FmtCode("§d", "■", 0xFFFF55FF.toInt()),
    FmtCode("§1", "■", 0xFF0000AA.toInt()), FmtCode("§9", "■", 0xFF5555FF.toInt()),
    FmtCode("§3", "■", 0xFF00AAAA.toInt()), FmtCode("§b", "■", 0xFF55FFFF.toInt()),
    FmtCode("§2", "■", 0xFF00AA00.toInt()), FmtCode("§a", "■", 0xFF55FF55.toInt()),
    FmtCode("§e", "■", 0xFFFFFF55.toInt()), FmtCode("§6", "■", 0xFFFFAA00.toInt()),
    FmtCode("§c", "■", 0xFFFF5555.toInt()), FmtCode("§4", "■", 0xFFAA0000.toInt()),
)
    private val FORMAT_STYLES: List<FmtCode> = listOf(
        FmtCode("§l", "B",  0xFFE8C84A.toInt()),
        FmtCode("§o", "I",  0xFFE8C84A.toInt()),
        FmtCode("§n", "U",  0xFFE8C84A.toInt()),
        FmtCode("§m", "S",  0xFFE8C84A.toInt()),
        FmtCode("§k", "obf",0xFFE8C84A.toInt()),
        FmtCode("§r", "rst",0xFFCC4444.toInt()),
    )

// ── Screen ────────────────────────────────────────────────────────────────────

class ScribeScreen(
    private val parent: Screen?,
    private val ctx:    ClientContext,
) : com.darkshamanism.lotcscholar.ui.compat.LegacyScreen(Text.literal("Scribe")) {

    // ── Panels ─────────────────────────────────────────────────────────────────
    private enum class LeftTab { DRAFTS, IMPORT }
    private var leftTab = LeftTab.DRAFTS

    private enum class BottomPanel { NONE, UNICODE, FORMATTING, CITATION }
    private var bottomPanel = BottomPanel.NONE

    private enum class ToolbarAction {
        SAVE,
        SPLIT,
        BOOK,
        DELETE,
        UNICODE,
        FORMATTING,
        CITATION,
        CLOSE,
    }

    private data class ToolbarButtonRect(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val label: String,
        val action: ToolbarAction,
    ) {
        fun contains(mx: Double, my: Double): Boolean =
            mx >= x && mx < x + w && my >= y && my < y + h
    }

    // ── Draft state ────────────────────────────────────────────────────────────
    private var currentDraft: ScribeDraft? = null
    private val lines = mutableListOf<String>("")
    private var lineScroll  = 0
    private var focusedLine = -1
    private var editWidget: TextFieldWidget? = null

    // ── Scroll offsets ─────────────────────────────────────────────────────────
    private var leftListScroll = 0
    private var unicodeScroll  = 0

    // ── Fields ─────────────────────────────────────────────────────────────────
    private lateinit var titleField:  TextFieldWidget
    private lateinit var authorField: TextFieldWidget
    private lateinit var searchField: TextFieldWidget   // cite / unicode search

    // ── Archive citation ────────────────────────────────────────────────────────
    private val citResults  = mutableListOf<ArchiveBook>()
    private var citSelected = -1
    private var citLoading  = false

    // ── Unicode state ───────────────────────────────────────────────────────────
    private var unicodeFilter = ""
    private val filteredUnicode get() = if (unicodeFilter.isBlank()) UNICODE_CHARS
        else UNICODE_CHARS.filter { (ch, name) ->
            name.contains(unicodeFilter, ignoreCase = true) || ch == unicodeFilter }
    private var unicodeHovered = -1

    // ── Stendhal import ─────────────────────────────────────────────────────────
    private var stendhalFiles: List<File> = emptyList()
    private var stendhalSelected = -1

    // ── Status ──────────────────────────────────────────────────────────────────
    private var statusMsg   = ""
    private var statusError = false

    // ── Layout ─────────────────────────────────────────────────────────────────
    private val topBarH    = 28
    private val leftW      = 138
    private val bottomPanH = 64    // height of bottom panel when open
    private val lineH      = 10
    private val colSwatch  = 14    // color swatch size

    private var edLeft = 0; private var edW   = 0
    private var edTop  = 0; private var edBot = 0   // content pane y-range
    private var toolbarButtons = emptyList<ToolbarButtonRect>()

    // ── Init ───────────────────────────────────────────────────────────────────

    override fun init() {
        clearChildren()
        editWidget = null

        edLeft = 6 + leftW + 4
        edW    = width - edLeft - 6

        // ── Fields (title / author strip) ──────────────────────────────────────
        val fh = 14; val gapY = 3
        val titleY  = topBarH + 6
        val authorY = titleY + fh + gapY
        edTop = authorY + fh + gapY + 8      // 8px for char-count info
        // Bottom: leave room for toolbar row + bottom panel (when open)
        val toolbarH = 20
        val panelH   = if (bottomPanel != BottomPanel.NONE) bottomPanH else 0
        edBot = height - 8 - toolbarH - panelH - 4
        if (edBot < edTop + 20) edBot = edTop + 20

        titleField = TextFieldWidget(textRenderer, edLeft + 36, titleY, edW - 40, fh,
            Text.literal("Title")).also {
            it.setMaxLength(100); it.text = currentDraft?.title ?: ""
            it.setPlaceholder(Text.literal("Title..."))
            it.setChangedListener { v -> currentDraft?.title = v }
        }
        addDrawableChild(titleField)

        authorField = TextFieldWidget(textRenderer, edLeft + 36, authorY, edW - 40, fh,
            Text.literal("Author")).also {
            it.setMaxLength(100); it.text = currentDraft?.author ?: ""
            it.setPlaceholder(Text.literal("Author..."))
            it.setChangedListener { v -> currentDraft?.author = v }
        }
        addDrawableChild(authorField)

        // ── Toolbar strip (above bottom panel) ────────────────────────────────
                val tbY = edBot + 4
        val btnGap = 4

        val compactToolbar = edW < 420
        val toggleW = if (compactToolbar) 28 else 32
        val rightStart = edLeft + edW - (toggleW * 3 + btnGap * 2)

        val widths = intArrayOf(
            if (compactToolbar) 58 else 92, // Save
            if (compactToolbar) 58 else 82, // Split
            if (compactToolbar) 56 else 70, // Book
            if (compactToolbar) 48 else 58, // Delete
        )
        val minWidths = intArrayOf(44, 44, 44, 40)
        val gapsLeft = btnGap * (widths.size - 1)
        val availableLeft = (rightStart - edLeft - btnGap).coerceAtLeast(0)

        var totalLeft = widths.sum() + gapsLeft
        if (totalLeft > availableLeft) {
            val minTotal = minWidths.sum() + gapsLeft
            val target = availableLeft.coerceAtLeast(minTotal)
            val span = (widths.sum() - minWidths.sum()).coerceAtLeast(1)
            val ratio = ((target - minTotal).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            for (i in widths.indices) {
                widths[i] = minWidths[i] + ((widths[i] - minWidths[i]) * ratio).toInt()
            }
            totalLeft = widths.sum() + gapsLeft
            var slack = availableLeft - totalLeft
            var idx = widths.lastIndex
            while (slack > 0 && widths.isNotEmpty()) {
                widths[idx] += 1
                slack -= 1
                idx -= 1
                if (idx < 0) idx = widths.lastIndex
            }
        }

        toolbarButtons = buildList {
            var btnX = edLeft
            add(
                ToolbarButtonRect(
                    x = btnX,
                    y = tbY,
                    w = widths[0],
                    h = toolbarH,
                    label = if (widths[0] < 80) "Save" else "Save [Ctrl+S]",
                    action = ToolbarAction.SAVE,
                ),
            )
            btnX += widths[0] + btnGap

            add(
                ToolbarButtonRect(
                    x = btnX,
                    y = tbY,
                    w = widths[1],
                    h = toolbarH,
                    label = if (widths[1] < 70) "Split" else "Split Pages",
                    action = ToolbarAction.SPLIT,
                ),
            )
            btnX += widths[1] + btnGap

            add(
                ToolbarButtonRect(
                    x = btnX,
                    y = tbY,
                    w = widths[2],
                    h = toolbarH,
                    label = if (widths[2] < 62) "B&Q" else "To B&Q",
                    action = ToolbarAction.BOOK,
                ),
            )
            btnX += widths[2] + btnGap

            add(
                ToolbarButtonRect(
                    x = btnX,
                    y = tbY,
                    w = widths[3],
                    h = toolbarH,
                    label = if (widths[3] < 52) "Del" else "Delete",
                    action = ToolbarAction.DELETE,
                ),
            )

            add(
                ToolbarButtonRect(
                    x = rightStart,
                    y = tbY,
                    w = toggleW,
                    h = toolbarH,
                    label = "SYM",
                    action = ToolbarAction.UNICODE,
                ),
            )
            add(
                ToolbarButtonRect(
                    x = rightStart + toggleW + btnGap,
                    y = tbY,
                    w = toggleW,
                    h = toolbarH,
                    label = "FMT",
                    action = ToolbarAction.FORMATTING,
                ),
            )
            add(
                ToolbarButtonRect(
                    x = rightStart + (toggleW + btnGap) * 2,
                    y = tbY,
                    w = toggleW,
                    h = toolbarH,
                    label = "CITE",
                    action = ToolbarAction.CITATION,
                ),
            )
            add(
                ToolbarButtonRect(
                    x = width - 22,
                    y = 4,
                    w = 18,
                    h = 18,
                    label = "X",
                    action = ToolbarAction.CLOSE,
                ),
            )
        }

        // ── Search field (citation / unicode filter) ───────────────────────────
        val panY = tbY + toolbarH + 2
        searchField = TextFieldWidget(textRenderer, edLeft, panY, edW / 2, 14, Text.literal("Search")).also {
            it.setMaxLength(60)
            it.visible = bottomPanel == BottomPanel.CITATION || bottomPanel == BottomPanel.UNICODE
            when (bottomPanel) {
                BottomPanel.CITATION -> it.setPlaceholder(Text.literal("Search archive..."))
                BottomPanel.UNICODE  -> it.setPlaceholder(Text.literal("Filter symbols..."))
                else -> {}
            }
            it.setChangedListener { v ->
                when (bottomPanel) {
                    BottomPanel.UNICODE  -> { unicodeFilter = v; unicodeScroll = 0 }
                    else -> {}
                }
            }
        }
        addDrawableChild(searchField)

        // Load Stendhal files if on import tab
        if (leftTab == LeftTab.IMPORT) stendhalFiles = StendhalImport.list()
    }

    private fun togglePanel(p: BottomPanel) {
        bottomPanel = if (bottomPanel == p) BottomPanel.NONE else p
        if (bottomPanel == BottomPanel.UNICODE) unicodeFilter = ""
        init()
    }

    // ── Line editing ───────────────────────────────────────────────────────────

    private fun focusLine(li: Int) {
        commitEdit()
        val target = li.coerceIn(0, lines.size)
        if (target == lines.size) lines.add("")
        focusedLine = target

        val vis = (edBot - edTop) / lineH
        if (target < lineScroll) lineScroll = target
        if (target >= lineScroll + vis) lineScroll = target - vis + 1

        val fy = edTop + (target - lineScroll) * lineH - 1
        editWidget = TextFieldWidget(textRenderer, edLeft + 2, fy, edW - 4, lineH + 2,
            Text.literal("")).also {
            it.setMaxLength(500); it.text = lines[target]; it.isFocused = true
        }
        addDrawableChild(editWidget!!); setFocused(editWidget)
    }

    private fun commitEdit() {
        val w = editWidget ?: return
        if (focusedLine in lines.indices) lines[focusedLine] = w.text
        remove(w); editWidget = null; focusedLine = -1
    }

    private fun insertAtCursor(text: String) {
        val w = editWidget
        if (w != null) {
            val cur = w.text; val pos = w.cursor
            w.text = cur.substring(0, pos) + text + cur.substring(pos)
            w.setCursor(pos + text.length, false)
        } else if (focusedLine in lines.indices) {
            lines[focusedLine] = lines[focusedLine] + text
            focusLine(focusedLine)
            editWidget?.setCursor(editWidget!!.text.length, false)
        } else {
            // Append to last line
            val last = lines.lastIndex
            lines[last] = lines[last] + text
        }
    }

    private fun syncDraft() { currentDraft?.content = lines.joinToString("\n") }

    private fun loadDraft(d: ScribeDraft) {
        commitEdit(); currentDraft = d
        lines.clear(); lines.addAll(d.content.split("\n").ifEmpty { listOf("") })
        lineScroll = 0; leftListScroll = 0; init()
    }

    // ── Draft operations ────────────────────────────────────────────────────────

    private fun doSave() {
        commitEdit(); syncDraft()
        val d = currentDraft ?: ctx.scribeDrafts.create().also { currentDraft = it }
        d.title   = titleField.text.ifBlank { "Untitled" }
        d.author  = authorField.text
        d.content = lines.joinToString("\n")
        ctx.scribeDrafts.update(d)
        status("Saved \"${d.title.take(28)}\"", false)
    }

    private fun doDelete() {
        commitEdit()
        currentDraft?.let { ctx.scribeDrafts.delete(it.id) }
        currentDraft = null; lines.clear(); lines.add(""); lineScroll = 0
        status("Draft deleted.", false); init()
    }

    private fun doSplit() {
        commitEdit(); syncDraft()
        val pages = ctx.scribeDrafts.splitIntoPages(lines.joinToString("\n"))
        status("Split into ${pages.size} page${if (pages.size == 1) "" else "s"}. Use To B&Q to write.", false)
    }

    private fun doCopyToBook() {
        commitEdit(); syncDraft()
        val mc = client ?: return
        val player = mc.player
        if (player == null) {
            status("Join a world before writing to a book.", true)
            return
        }
        val hand = when {
            player.mainHandStack.isOf(Items.WRITABLE_BOOK) -> Hand.MAIN_HAND
            player.offHandStack.isOf(Items.WRITABLE_BOOK)  -> Hand.OFF_HAND
            else -> null
        }
        if (hand == null) {
            status("Hold a Book & Quill in your main hand or off-hand.", true)
            return
        }

        val pages = ctx.scribeDrafts.splitIntoPages(lines.joinToString("\n"))
        if (pages.all { it.isBlank() }) {
            status("Nothing to write yet.", true)
            return
        }

        val maxPages = MinecraftBookFormatter.MAX_PAGES
        val err = WritableBookHelper.setHeldPages(player, hand, pages, maxPages)
        if (err != null) {
            status("Book write failed: $err", true)
            return
        }

        val writtenPages = minOf(pages.size, maxPages)
        val overflowPages = (pages.size - writtenPages).coerceAtLeast(0)
        if (overflowPages > 0) {
            status(
                "Wrote $writtenPages/$maxPages pages. $overflowPages page${if (overflowPages == 1) "" else "s"} remain for another book.",
                false,
            )
        } else {
            status("Current entry written to Book & Quill ($writtenPages page${if (writtenPages == 1) "" else "s"}).", false)
        }
    }

    // ── Stendhal import ─────────────────────────────────────────────────────────

    private fun doStendhalImport(file: File) {
        val text  = StendhalImport.read(file); if (text.isBlank()) { status("File empty.", true); return }
        val draft = ctx.scribeDrafts.create()
        draft.title   = StendhalImport.nameOf(file)
        draft.content = text
        ctx.scribeDrafts.update(draft)
        loadDraft(draft)
        status("Imported \"${draft.title}\" from Stendhal.", false)
    }

    // ── Citation ────────────────────────────────────────────────────────────────

    private fun doCitSearch() {
        val q = searchField.text.trim(); if (q.isBlank() || citLoading) return
        citLoading = true; citSelected = -1; citResults.clear()
        ctx.archive.fetchBooks(query = q, limit = 6,
            onSuccess = { books, _ -> client?.execute { citResults.addAll(books); citLoading = false } },
            onError   = { e        -> client?.execute { status(e, true); citLoading = false } })
    }

    private fun insertCitation(b: ArchiveBook) {
        commitEdit()
        val cite = "\"${b.title}\" (${b.author})"
        val li   = if (focusedLine in lines.indices) focusedLine else lines.lastIndex
        lines[li] = lines[li].trimEnd() + (if (lines[li].isBlank()) cite else " $cite")
        status("Citation inserted.", false)
    }

    private fun formatCodeShortcut(kc: Int, mods: Int): String? {
        val altDown = (mods and GLFW.GLFW_MOD_ALT) != 0
        if (!altDown) return null

        return when (kc) {
            GLFW.GLFW_KEY_B -> "\u00A7l"
            GLFW.GLFW_KEY_I -> "\u00A7o"
            GLFW.GLFW_KEY_U -> "\u00A7n"
            GLFW.GLFW_KEY_M -> "\u00A7m"
            GLFW.GLFW_KEY_R -> "\u00A7r"
            GLFW.GLFW_KEY_1 -> "\u00A7c"
            GLFW.GLFW_KEY_2 -> "\u00A7a"
            GLFW.GLFW_KEY_3 -> "\u00A79"
            GLFW.GLFW_KEY_4 -> "\u00A7e"
            else -> null
        }
    }

    private fun status(msg: String, err: Boolean) { statusMsg = msg; statusError = err }

    private fun handleToolbarAction(action: ToolbarAction): Boolean {
        when (action) {
            ToolbarAction.SAVE -> doSave()
            ToolbarAction.SPLIT -> doSplit()
            ToolbarAction.BOOK -> doCopyToBook()
            ToolbarAction.DELETE -> doDelete()
            ToolbarAction.UNICODE -> togglePanel(BottomPanel.UNICODE)
            ToolbarAction.FORMATTING -> togglePanel(BottomPanel.FORMATTING)
            ToolbarAction.CITATION -> togglePanel(BottomPanel.CITATION)
            ToolbarAction.CLOSE -> close()
        }
        return true
    }

    private fun renderToolbarButtons(dc: DrawContext, mx: Int, my: Int) {
        toolbarButtons.forEach { button ->
            val active = when (button.action) {
                ToolbarAction.UNICODE -> bottomPanel == BottomPanel.UNICODE
                ToolbarAction.FORMATTING -> bottomPanel == BottomPanel.FORMATTING
                ToolbarAction.CITATION -> bottomPanel == BottomPanel.CITATION
                else -> false
            }
            TabButton.render(
                dc = dc,
                tr = textRenderer,
                x = button.x,
                y = button.y,
                w = button.w,
                h = button.h,
                label = button.label,
                active = active,
                hovered = button.contains(mx.toDouble(), my.toDouble()),
            )
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun keyPressed(kc: Int, sc: Int, mods: Int): Boolean {
        // Ctrl+S save
        if (kc == 83 && hasControlDownCompat()) { doSave(); return true }

        // Alt formatting shortcuts for fast writing
        formatCodeShortcut(kc, mods)?.let { code ->
            insertAtCursor(code)
            status("Inserted format code $code", false)
            return true
        }

        // Escape
        if (kc == 256) {
            if (editWidget != null) { commitEdit(); return true }
            if (bottomPanel != BottomPanel.NONE) { bottomPanel = BottomPanel.NONE; init(); return true }
            close(); return true
        }
        // Enter = new line in editor
        if ((kc == 257 || kc == 335) && editWidget != null && focused === editWidget) {
            val widget = editWidget ?: return true
            val lineIndex = focusedLine.coerceIn(0, lines.lastIndex)
            val cur  = widget.text; val pos = widget.cursor.coerceIn(0, widget.text.length)
            val before = cur.substring(0, pos); val after = cur.substring(pos)
            remove(widget)
            editWidget = null
            focusedLine = -1
            lines[lineIndex] = before
            lines.add(lineIndex + 1, after)
            focusLine(lineIndex + 1)
            return true
        }
        // Backspace on empty line — merge up
        if (kc == 259 && editWidget?.text?.isEmpty() == true && focusedLine > 0) {
            val current = focusedLine.coerceIn(1, lines.lastIndex)
            val prev = current - 1
            val prevLen = lines[prev].length
            editWidget?.let { remove(it) }
            editWidget = null
            focusedLine = -1
            lines.removeAt(current)
            focusLine(prev)
            editWidget?.setCursor(prevLen, false)
            return true
        }
        // Enter in citation search
        if ((kc == 257 || kc == 335) && focused === searchField &&
            bottomPanel == BottomPanel.CITATION) {
            doCitSearch(); return true
        }
        return super.keyPressed(kc, sc, mods)
    }

    override fun mouseClicked(mx: Double, my: Double, btn: Int): Boolean {
        toolbarButtons.firstOrNull { it.contains(mx, my) }?.let { button ->
            return handleToolbarAction(button.action)
        }

        // ── Left panel ──────────────────────────────────────────────────────────
        if (mx >= 6 && mx < 6 + leftW && my >= topBarH) {
            val tabW = leftW / 2
            // Tab strip
            if (my >= topBarH && my < topBarH + 14) {
                when {
                    mx < 6 + tabW       -> { leftTab = LeftTab.DRAFTS;    leftListScroll = 0; init() }
                    else                 -> {
                        leftTab = LeftTab.IMPORT
                        stendhalFiles = StendhalImport.list()
                        leftListScroll = 0; init()
                    }
                }
                return true
            }
            // New Draft button
            val newBtnY = topBarH + 16
            if (my >= newBtnY && my < newBtnY + 16 && leftTab == LeftTab.DRAFTS) {
                commitEdit(); loadDraft(ctx.scribeDrafts.create()); return true
            }
            // List rows
            val listTop = newBtnY + (if (leftTab == LeftTab.DRAFTS) 18 else 2)
            val rowH = 18
            val vis  = ((height - listTop - 42) / rowH).coerceAtLeast(0)
            val list: List<Pair<String, () -> Unit>> = when (leftTab) {
                LeftTab.DRAFTS -> ctx.scribeDrafts.drafts.drop(leftListScroll).take(vis).mapIndexed { vi, d ->
                    d.title to { commitEdit(); loadDraft(d) }
                }
                LeftTab.IMPORT -> stendhalFiles.drop(leftListScroll).take(vis).map { f ->
                    StendhalImport.nameOf(f) to { doStendhalImport(f) }
                }
            }
            list.forEachIndexed { i, (_, action) ->
                val ry = listTop + i * rowH
                if (my >= ry && my < ry + rowH) { action(); return true }
            }
        }

        // ── Editor content area ─────────────────────────────────────────────────
        if (mx >= edLeft && mx < edLeft + edW && my >= edTop && my < edBot) {
            val li = lineScroll + ((my - edTop) / lineH).toInt()
            focusLine(li.coerceIn(0, lines.size)); return true
        }

        // ── Unicode panel ───────────────────────────────────────────────────────
        if (bottomPanel == BottomPanel.UNICODE) {
            val panY   = edBot + 4 + 24   // below toolbar + search row
            val cols   = 16; val cellW = (edW / cols); val cellH = 16
            val chars  = filteredUnicode
            val visR   = ((bottomPanH - 22) / cellH).coerceAtLeast(1)
            val startI = unicodeScroll * cols
            for (row in 0 until visR) {
                for (col in 0 until cols) {
                    val idx = startI + row * cols + col
                    if (idx >= chars.size) break
                    val cx = edLeft + col * cellW; val cy = panY + row * cellH
                    if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH) {
                        insertAtCursor(chars[idx].first)
                        return true
                    }
                }
            }
        }

        // ── Formatting panel ────────────────────────────────────────────────────
        if (bottomPanel == BottomPanel.FORMATTING) {
            val panY = edBot + 4 + 2
            // Color swatches row 1 (first 8) + row 2 (next 8)
            val swW = colSwatch + 2
            FORMAT_COLORS.forEachIndexed { i, fc ->
                val row = i / 8; val col = i % 8
                val sx  = edLeft + col * swW; val sy = panY + row * (colSwatch + 3)
                if (mx >= sx && mx < sx + colSwatch && my >= sy && my < sy + colSwatch) {
                    insertAtCursor(fc.code); return true
                }
            }
            // Style buttons row below colors
            val styleY = panY + 2 * (colSwatch + 3) + 4
            val styleW = 28
            FORMAT_STYLES.forEachIndexed { i, fs ->
                val sx = edLeft + i * (styleW + 2)
                if (mx >= sx && mx < sx + styleW && my >= styleY && my < styleY + 16) {
                    insertAtCursor(fs.code); return true
                }
            }
        }

        // ── Citation panel ──────────────────────────────────────────────────────
        if (bottomPanel == BottomPanel.CITATION) {
            val panY  = edBot + 4 + 20
            val rowH  = 10
            citResults.take(4).forEachIndexed { i, b ->
                val ry = panY + i * rowH
                if (my >= ry && my < ry + rowH && mx >= edLeft && mx < edLeft + edW - 60) {
                    citSelected = i; return true
                }
            }
            // Insert button
            if (citSelected >= 0) {
                val ibX = edLeft + edW - 56; val ibY = panY + citSelected * rowH
                if (mx >= ibX && mx < ibX + 54 && my >= ibY && my < ibY + rowH) {
                    citResults.getOrNull(citSelected)?.let { insertCitation(it) }; return true
                }
            }
            // Search button
            val sbX = edLeft + edW / 2 + 2; val sbY = edBot + 4 + 2
            if (mx >= sbX && mx < sbX + 44 && my >= sbY && my < sbY + 14) {
                doCitSearch(); return true
            }
        }

        return super.mouseClicked(mx, my, btn)
    }

    override fun mouseScrolled(mx: Double, my: Double, hA: Double, vA: Double): Boolean {
        // Left panel
        if (mx >= 6 && mx < 6 + leftW) {
            val max = when (leftTab) {
                LeftTab.DRAFTS    -> maxOf(0, ctx.scribeDrafts.drafts.size - 4)
                LeftTab.IMPORT    -> maxOf(0, stendhalFiles.size - 4)
            }
            leftListScroll = (leftListScroll - vA.toInt()).coerceIn(0, max); return true
        }
        // Editor
        if (mx >= edLeft && my >= edTop && my < edBot) {
            val vis = (edBot - edTop) / lineH
            lineScroll = (lineScroll - vA.toInt()).coerceIn(0, maxOf(0, lines.size - vis)); return true
        }
        // Unicode scroll
        if (bottomPanel == BottomPanel.UNICODE && my > edBot) {
            val cols  = 16; val maxRow = maxOf(0, Math.ceil(filteredUnicode.size.toDouble() / cols).toInt() - 2)
            unicodeScroll = (unicodeScroll - vA.toInt()).coerceIn(0, maxRow); return true
        }
        return super.mouseScrolled(mx, my, hA, vA)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun render(dc: DrawContext, mx: Int, my: Int, delta: Float) {
        val P = ParchmentTheme

        // ── Top bar ────────────────────────────────────────────────────────────
        dc.fill(0, 0, width, topBarH, P.BG_INSET)
        dc.fill(0, topBarH - 1, width, topBarH, P.BORDER_GOLD)
        dc.drawCenteredTextWithShadow(textRenderer, Text.literal("The Farglade - Scribe"),
            width / 2, 8, P.TEXT_ACCENT)
        dc.drawCenteredTextWithShadow(textRenderer, Text.literal(P.ORNAMENT),
            width / 2, 18, P.TEXT_SUBTITLE)

        // ── Left panel ─────────────────────────────────────────────────────────
        dc.fill(6, topBarH, 6 + leftW, height - 6, P.BG_INSET)
        dc.drawStrokedRectangle(6, topBarH, leftW, height - topBarH - 6, P.BORDER_GOLD)
        renderLeftPanel(dc, mx, my)

        // ── Editor panel ───────────────────────────────────────────────────────
        dc.fill(edLeft, topBarH, edLeft + edW, height - 6, P.BG_PANEL)
        dc.drawStrokedRectangle(edLeft, topBarH, edW, height - topBarH - 6, P.BORDER_GOLD)
        renderEditor(dc, mx, my)
        renderToolbarButtons(dc, mx, my)

        // ── Bottom panel ────────────────────────────────────────────────────────
        when (bottomPanel) {
            BottomPanel.UNICODE     -> renderUnicodePanel(dc, mx, my)
            BottomPanel.FORMATTING  -> renderFormattingPanel(dc, mx, my)
            BottomPanel.CITATION    -> renderCitationPanel(dc, mx, my)
            BottomPanel.NONE        -> {}
        }

        // ── Status bar ─────────────────────────────────────────────────────────
        dc.drawNoticeBanner(textRenderer, statusMsg, statusError, width, height - 51 - (if (bottomPanel != BottomPanel.NONE) bottomPanH else 0))

        super.render(dc, mx, my, delta)
        if (!ctx.config.current.ui.reducedMotion) drawOpenFadeWash(dc)
    }

    // ── Left panel render ───────────────────────────────────────────────────────

    private fun renderLeftPanel(dc: DrawContext, mx: Int, my: Int) {
        val P    = ParchmentTheme
        // Tab strip
        val tabW = leftW / 2
        listOf("Drafts" to LeftTab.DRAFTS, "Import" to LeftTab.IMPORT)
            .forEachIndexed { i, (label, tab) ->
                val tx  = 6 + i * tabW; val sel = tab == leftTab
                val hov = mx >= tx && mx < tx + tabW && my >= topBarH && my < topBarH + 14
                dc.fill(tx, topBarH, tx + tabW - 1, topBarH + 14,
                    when { sel -> P.BG_ROW_SEL; hov -> P.BG_ROW_HOVER; else -> P.BG_INSET })
                if (sel) dc.fill(tx, topBarH + 12, tx + tabW - 1, topBarH + 14, P.BORDER_GOLD)
                dc.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                    tx + tabW / 2, topBarH + 3, if (sel) P.TEXT_ACCENT else P.TEXT_LIGHT)
            }

        val rowH = 18

        when (leftTab) {
            LeftTab.DRAFTS -> {
                val newBtnY = topBarH + 16
                val hov     = mx >= 10 && mx < 6 + leftW - 4 && my >= newBtnY && my < newBtnY + 16
                dc.fill(10, newBtnY, 6 + leftW - 4, newBtnY + 16,
                    if (hov) P.BORDER_MUTED else P.BG_MID)
                dc.drawStrokedRectangle(10, newBtnY, leftW - 8, 16, P.BORDER_MUTED)
                dc.drawCenteredTextWithShadow(textRenderer, Text.literal("+ New Draft"),
                    6 + leftW / 2, newBtnY + 4, P.TEXT_ACCENT)

                val listTop = newBtnY + 18
                val vis     = ((height - listTop - 42) / rowH).coerceAtLeast(0)
                val drafts  = ctx.scribeDrafts.drafts
                if (drafts.isEmpty()) {
                    dc.drawTextWithShadow(textRenderer, Text.literal("No drafts yet."),
                        10, listTop + 4, P.TEXT_MUTED)
                } else {
                    drafts.drop(leftListScroll).take(vis).forEachIndexed { vi, d ->
                        val ry  = listTop + vi * rowH
                        val sel = d.id == currentDraft?.id
                        val hv  = mx >= 6 && mx < 6 + leftW && my >= ry && my < ry + rowH
                        if (sel || hv) dc.fill(7, ry, 6 + leftW - 1, ry + rowH,
                            if (sel) P.BG_ROW_SEL else P.BG_ROW_HOVER)
                        if (sel) dc.fill(6, ry, 9, ry + rowH, P.BORDER_GOLD)
                        dc.drawTextWithShadow(textRenderer,
                            Text.literal(textRenderer.truncateToWidth(d.title, leftW - 14)),
                            12, ry + 5, if (sel) P.TEXT_ACCENT else P.TEXT_LIGHT)
                    }
                }
            }

            LeftTab.IMPORT -> {
                val listTop = topBarH + 18
                val vis     = ((height - listTop - 42) / rowH).coerceAtLeast(0)
                if (stendhalFiles.isEmpty()) {
                    val dir = StendhalImport.booksDir().absolutePath
                    dc.drawTextWithShadow(textRenderer,
                        Text.literal(textRenderer.truncateToWidth("No files in:", leftW - 14)),
                        10, listTop + 4, P.TEXT_MUTED)
                    dc.drawTextWithShadow(textRenderer,
                        Text.literal(textRenderer.truncateToWidth(dir, leftW - 14)),
                        10, listTop + 14, P.TEXT_MUTED)
                } else {
                    stendhalFiles.drop(leftListScroll).take(vis).forEachIndexed { vi, f ->
                        val ry  = listTop + vi * rowH
                        val sel = vi + leftListScroll == stendhalSelected
                        val hv  = mx >= 6 && mx < 6 + leftW && my >= ry && my < ry + rowH
                        if (sel || hv) dc.fill(7, ry, 6 + leftW - 1, ry + rowH,
                            if (sel) P.BG_ROW_SEL else P.BG_ROW_HOVER)
                        dc.drawTextWithShadow(textRenderer,
                            Text.literal(textRenderer.truncateToWidth(StendhalImport.nameOf(f), leftW - 14)),
                            12, ry + 5, P.TEXT_LIGHT)
                    }
                }
            }
        }

    }

    // ── Editor render ───────────────────────────────────────────────────────────

    private fun renderEditor(dc: DrawContext, mx: Int, my: Int) {
        val P = ParchmentTheme

        // Title / Author labels
        val fh = 14; val gapY = 3
        val titleY  = topBarH + 6
        val authorY = titleY + fh + gapY
        dc.drawTextWithShadow(textRenderer, Text.literal("Title"),  edLeft + 4, titleY + 3,  P.TEXT_MUTED)
        dc.drawTextWithShadow(textRenderer, Text.literal("Author"), edLeft + 4, authorY + 3, P.TEXT_MUTED)

        // Char count + page estimate
        val content      = lines.joinToString("\n")
        val charCount    = content.length
        val pageEstimate = ctx.scribeDrafts.estimatePageCount(content)
        val overColor    = if (charCount % 250 > 220) P.TEXT_ERROR else P.TEXT_MUTED
        dc.drawTextWithShadow(textRenderer,
            Text.literal("~$pageEstimate pg | $charCount ch"),
            edLeft + 4, edTop - 8, overColor)

        // Draft name in top-right of editor if saved
        currentDraft?.let {
            dc.drawTextWithShadow(textRenderer,
                Text.literal(textRenderer.truncateToWidth("* ${it.title}", edW - 50)),
                edLeft + edW - textRenderer.getWidth(textRenderer.truncateToWidth("* ${it.title}", edW - 50)) - 4,
                topBarH + 6, P.TEXT_SUBTITLE)
        }

        // Content area background (parchment to match book writing)
        dc.fill(edLeft + 1, edTop - 2, edLeft + edW - 1, edBot + 1, P.BG_PANEL)
        dc.fill(edLeft + 1, edTop - 2, edLeft + edW - 1, edTop - 1, P.BORDER_MUTED)
        dc.fill(edLeft + 1, edBot,     edLeft + edW - 1, edBot + 1, P.BORDER_MUTED)

        val vis = (edBot - edTop) / lineH
        dc.enableScissor(edLeft + 2, edTop, edLeft + edW - 2, edBot)
        for (vi in 0 until vis) {
            val li = vi + lineScroll; if (li >= lines.size) break
            val ly = edTop + vi * lineH
            if (li == focusedLine) {
                dc.fill(edLeft + 2, ly - 1, edLeft + edW - 2, ly + lineH, P.BG_ROW_SEL)
            } else {
                // Render with § color codes parsed
                dc.drawText(textRenderer, Text.literal(lines[li]), edLeft + 4, ly, P.TEXT_PRIMARY, false)
            }
        }
        dc.disableScissor()

        // Line-number hint
        val totalL = lines.size; val curL = if (focusedLine >= 0) focusedLine + 1 else totalL
        dc.drawTextWithShadow(textRenderer, Text.literal("L$curL/$totalL"),
            edLeft + edW - textRenderer.getWidth("L$totalL/$totalL") - 4, edBot - 9, P.TEXT_MUTED)
    }

    // ── Unicode panel render ─────────────────────────────────────────────────────

    private fun renderUnicodePanel(dc: DrawContext, mx: Int, my: Int) {
        val P     = ParchmentTheme
        val panY  = edBot + 4 + 24
        val cols  = 16; val cellW = edW / cols; val cellH = 16
        val chars = filteredUnicode
        val visR  = ((bottomPanH - 22) / cellH).coerceAtLeast(1)

        dc.drawTextWithShadow(textRenderer, Text.literal("SYMBOLS - click to insert"),
            edLeft + 4, edBot + 28, P.TEXT_ACCENT)

        dc.enableScissor(edLeft + 1, panY - 2, edLeft + edW - 1, panY + visR * cellH + 2)
        val startI = unicodeScroll * cols
        for (row in 0 until visR) {
            for (col in 0 until cols) {
                val idx = startI + row * cols + col; if (idx >= chars.size) break
                val cx  = edLeft + col * cellW; val cy = panY + row * cellH
                val hov = mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH
                if (hov) {
                    unicodeHovered = idx
                    dc.fill(cx, cy, cx + cellW, cy + cellH, P.BG_ROW_SEL)
                }
                dc.drawCenteredTextWithShadow(textRenderer, Text.literal(chars[idx].first),
                    cx + cellW / 2, cy + 4, P.TEXT_PRIMARY)
            }
        }
        dc.disableScissor()

        // Tooltip for hovered char
        if (unicodeHovered in chars.indices) {
            val (ch, name) = chars[unicodeHovered]
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("$ch  $name"),
                edLeft + edW / 2, panY + visR * cellH + 4, P.TEXT_SUBTITLE)
        }

        // Scroll indicator
        val totalRows = Math.ceil(chars.size.toDouble() / cols).toInt()
        if (totalRows > visR) {
            dc.drawTextWithShadow(textRenderer,
                Text.literal("Scroll | row ${unicodeScroll + 1}/$totalRows"),
                edLeft + edW - 80, edBot + 28, P.TEXT_MUTED)
        }
    }

    // ── Formatting panel render ────────────────────────────────────────────────

    private fun renderFormattingPanel(dc: DrawContext, mx: Int, my: Int) {
        val P    = ParchmentTheme
        val panY = edBot + 4 + 2

        dc.drawTextWithShadow(textRenderer, Text.literal("COLOUR & FORMAT - click to insert code"),
            edLeft + 4, panY - 2, P.TEXT_ACCENT)

        // Color swatches
        val swW = colSwatch + 2
        FORMAT_COLORS.forEachIndexed { i, fc ->
            val row = i / 8; val col = i % 8
            val sx  = edLeft + col * swW; val sy = panY + 8 + row * (colSwatch + 3)
            val hov = mx >= sx && mx < sx + colSwatch && my >= sy && my < sy + colSwatch
            dc.fill(sx, sy, sx + colSwatch, sy + colSwatch, fc.color)
            if (hov) dc.drawStrokedRectangle(sx, sy, colSwatch, colSwatch, P.BORDER_GOLD)
            // Label the code on hover
            if (hov) {
                dc.drawTextWithShadow(textRenderer, Text.literal(fc.code),
                    edLeft + 8 * swW + 8, sy + 4, P.TEXT_ACCENT)
            }
        }

        // Style buttons
        val styleY = panY + 8 + 2 * (colSwatch + 3) + 6
        val styleW = 32
        FORMAT_STYLES.forEachIndexed { i, fs ->
            val sx  = edLeft + i * (styleW + 2)
            val hov = mx >= sx && mx < sx + styleW && my >= styleY && my < styleY + 16
            dc.fill(sx, styleY, sx + styleW, styleY + 16, if (hov) P.BORDER_MUTED else P.BG_MID)
            dc.drawStrokedRectangle(sx, styleY, styleW, 16, P.BORDER_MUTED)
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal(fs.label),
                sx + styleW / 2, styleY + 4, fs.color)
        }
        // Preview + keyboard shortcuts
        dc.drawTextWithShadow(textRenderer, Text.literal("\u00A7r resets all formatting"),
            edLeft + FORMAT_STYLES.size * (styleW + 2) + 8, styleY + 1, P.TEXT_MUTED)
        dc.drawTextWithShadow(textRenderer, Text.literal("Shortcuts: Alt+B/I/U/M/R, Alt+1..4"),
            edLeft + FORMAT_STYLES.size * (styleW + 2) + 8, styleY + 9, P.TEXT_MUTED)
    }

    // ── Citation panel render ──────────────────────────────────────────────────

    private fun renderCitationPanel(dc: DrawContext, mx: Int, my: Int) {
        val P    = ParchmentTheme
        val panY = edBot + 4 + 20
        val rowH = 10

        dc.drawTextWithShadow(textRenderer, Text.literal("CITE ARCHIVE"), edLeft + 4, edBot + 6, P.TEXT_ACCENT)
        // search field is already drawn as DrawableChild at edBot+6 left half
        val sbX = edLeft + edW / 2 + 2; val sbY = edBot + 4 + 2
        val sbH = mx >= sbX && mx < sbX + 44 && my >= sbY && my < sbY + 14
        dc.fill(sbX, sbY, sbX + 44, sbY + 14, if (sbH) P.BORDER_MUTED else P.BG_MID)
        dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Search"), sbX + 22, sbY + 3, P.TEXT_LIGHT)

        if (citLoading) {
            dc.drawTextWithShadow(textRenderer, Text.literal("Searching..."), edLeft + 4, panY, P.TEXT_MUTED)
        } else {
            citResults.take(4).forEachIndexed { i, b ->
                val ry  = panY + i * rowH
                val sel = i == citSelected
                val hov = mx >= edLeft + 4 && mx < edLeft + edW - 64 && my >= ry && my < ry + rowH
                if (sel || hov) dc.fill(edLeft + 2, ry, edLeft + edW - 64, ry + rowH,
                    if (sel) P.BG_ROW_SEL else P.BG_ROW_HOVER)
                dc.drawTextWithShadow(textRenderer,
                    Text.literal(textRenderer.truncateToWidth("${b.title} - ${b.author}", edW - 80)),
                    edLeft + 4, ry + 1, if (sel) P.TEXT_ACCENT else P.TEXT_LIGHT)
                if (sel) {
                    val ibX = edLeft + edW - 58; val ibH = mx >= ibX && mx < ibX + 54 && my >= ry && my < ry + rowH
                    dc.fill(ibX, ry, ibX + 56, ry + rowH, if (ibH) P.BORDER_MUTED else P.BG_MID)
                    dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Insert"),
                        ibX + 28, ry + 1, P.TEXT_LIGHT)
                }
            }
        }
    }

    override fun close() { commitEdit(); syncDraft(); client?.setScreen(parent) }
    override fun shouldPause() = false
}

