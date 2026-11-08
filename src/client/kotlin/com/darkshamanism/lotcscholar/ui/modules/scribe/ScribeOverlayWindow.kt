package com.darkshamanism.lotcscholar.ui.modules.scribe

import com.darkshamanism.lotcscholar.core.ClientContext
import com.darkshamanism.lotcscholar.integration.minecraft.WritableBookHelper
import com.darkshamanism.lotcscholar.services.ScribeDraft
import com.darkshamanism.lotcscholar.ui.hud.*
import com.darkshamanism.lotcscholar.ui.overlay.*
import com.darkshamanism.lotcscholar.ui.theme.UiTheme
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.Items
import net.minecraft.util.Hand
import org.lwjgl.glfw.GLFW

/**
 * Scribe overlay window with in-place editing.
 *
 * Players can write and save notes directly in the overlay while chat is open,
 * without switching to the full Scribe screen.
 */
class ScribeWindow(private val ctx: ClientContext) {

    companion object {
        val WINDOW_ID = WindowId("scribe")
        private const val HEADER_H = 10
        private const val LIST_LINE_H = 14
        private const val EDITOR_LINE_H = 9
        private const val AUTO_SAVE_DELAY_MS = 1200L
        private const val TITLE_FIELD_H = 14
    }

    private enum class Action { NEW, SAVE, INSERT, BOOK, COPY }

    private data class ActionSpec(
        val action: Action,
        val label: String,
        val enabled: Boolean,
    )

    private data class Layout(
        val listX: Int,
        val listY: Int,
        val listW: Int,
        val listH: Int,
        val detailX: Int,
        val detailY: Int,
        val detailW: Int,
        val detailH: Int,
        val titleX: Int,
        val titleY: Int,
        val titleW: Int,
        val titleH: Int,
        val formatX: Int,
        val formatY: Int,
        val editorX: Int,
        val editorY: Int,
        val editorW: Int,
        val editorH: Int,
        val buttonX: Int,
        val buttonY: Int,
    )

    private val listWidget = HudListWidget<ScribeDraft>(
        id = "scribe-list",
        rowHeight = LIST_LINE_H,
    )

    private var editingDraftId: String? = null
    private var titleText = ""
    private var titleCursor = 0
    private var titleFocused = false

    private val editorLines = mutableListOf("")
    private var caretLine = 0
    private var caretCol = 0
    private var editorScroll = 0
    private var editorVisibleRows = 1
    private var editorFocused = false

    private var dirty = false
    private var lastEditAtMs = 0L

    private var statusMessage = ""
    private var statusUntilMs = 0L

    private val quickFormatPages: List<List<Pair<String, String>>> = listOf(
        listOf(
            "\u00A7r" to "R",
            "\u00A7l" to "B",
            "\u00A7o" to "I",
            "\u00A7n" to "U",
            "\u00A7m" to "S",
            "\u00A7c" to "C",
            "\u00A7a" to "A",
            "\u00A79" to "9",
            "\u00A7e" to "E",
        ),
        listOf(
            "\u00A70" to "0",
            "\u00A71" to "1",
            "\u00A72" to "2",
            "\u00A73" to "3",
            "\u00A74" to "4",
            "\u00A75" to "5",
            "\u00A76" to "6",
            "\u00A77" to "7",
            "\u00A78" to "8",
        ),
        listOf(
            "\u00A79" to "9",
            "\u00A7a" to "A",
            "\u00A7b" to "B",
            "\u00A7c" to "C",
            "\u00A7d" to "D",
            "\u00A7e" to "E",
            "\u00A7f" to "F",
            "\u00A7k" to "K",
            "\u00A7r" to "R",
        ),
    )
    private var formatPageIndex = 0

    fun createDescriptor(): WindowDescriptor = WindowDescriptor(
        id = WINDOW_ID,
        title = "Scribe",
        defaultWidth = 220,
        defaultHeight = 240,
        resizable = true,
        iconChar = "\u270E",
        onRender = ::render,
        onMouseClick = ::onClick,
        onMouseScroll = ::onScroll,
        onKeyPress = ::onKeyPress,
        onCharTyped = ::onCharTyped,
    )

    private fun refreshList() {
        val drafts = ctx.scribeDrafts.drafts
        listWidget.items = drafts.map { draft ->
            val pages = ctx.scribeDrafts.estimatePageCount(draft.content)
            HudListWidget.ListItem(
                label = draft.title.ifEmpty { "Untitled" },
                value = draft,
                secondary = "${pages}p",
            )
        }

        if (drafts.isEmpty()) {
            listWidget.selectedIndex = -1
            editingDraftId = null
            titleText = ""
            titleCursor = 0
            titleFocused = false
            editorLines.clear()
            editorLines.add("")
            caretLine = 0
            caretCol = 0
            editorScroll = 0
            editorFocused = false
            dirty = false
            return
        }

        val selectedIdx = drafts.indexOfFirst { it.id == editingDraftId }
        if (selectedIdx >= 0) {
            listWidget.selectedIndex = selectedIdx
            return
        }

        listWidget.selectedIndex = 0
        openDraft(drafts[0], persistCurrent = false)
    }

    private fun selectedDraft(): ScribeDraft? =
        listWidget.items.getOrNull(listWidget.selectedIndex)?.value

    private fun openDraft(draft: ScribeDraft, persistCurrent: Boolean = true) {
        if (persistCurrent) persistCurrentDraft(force = false)

        editingDraftId = draft.id
        titleText = draft.title
        titleCursor = titleText.length
        titleFocused = false
        editorLines.clear()
        editorLines.addAll(draft.content.split("\n"))
        if (editorLines.isEmpty()) editorLines.add("")
        caretLine = 0
        caretCol = 0
        editorScroll = 0
        editorFocused = false
        dirty = false
    }

    private fun createNewDraft() {
        persistCurrentDraft(force = false)
        val draft = ctx.scribeDrafts.create()
        openDraft(draft, persistCurrent = false)
        listWidget.selectedIndex = ctx.scribeDrafts.drafts.indexOfFirst { it.id == draft.id }
        editorFocused = true
        setStatus("New draft created.")
    }

    private fun editedContent(): String = editorLines.joinToString("\n")

    private fun persistCurrentDraft(force: Boolean): Boolean {
        val id = editingDraftId ?: return false
        if (!force && !dirty) return false

        val draft = ctx.scribeDrafts.drafts.firstOrNull { it.id == id } ?: return false
        draft.title = titleText.ifBlank { "Untitled" }
        draft.content = editedContent()
        ctx.scribeDrafts.update(draft)
        dirty = false
        return true
    }

    private fun maybeAutoSave() {
        if (!dirty) return
        val now = System.currentTimeMillis()
        if (now - lastEditAtMs < AUTO_SAVE_DELAY_MS) return
        persistCurrentDraft(force = false)
    }

    private fun markDirty() {
        dirty = true
        lastEditAtMs = System.currentTimeMillis()
    }

    private fun setStatus(message: String, durationMs: Long = 2200L) {
        statusMessage = message
        statusUntilMs = System.currentTimeMillis() + durationMs
    }

    private fun activeStatus(now: Long): String? =
        statusMessage.takeIf { it.isNotBlank() && now <= statusUntilMs }

    private fun clampTitleCursor() {
        titleCursor = titleCursor.coerceIn(0, titleText.length)
    }

    private fun insertTitleChar(c: Char) {
        clampTitleCursor()
        titleText = titleText.substring(0, titleCursor) + c + titleText.substring(titleCursor)
        titleCursor++
        markDirty()
    }

    private fun titleBackspace() {
        clampTitleCursor()
        if (titleCursor <= 0) return
        titleText = titleText.substring(0, titleCursor - 1) + titleText.substring(titleCursor)
        titleCursor--
        markDirty()
    }

    private fun titleDeleteForward() {
        clampTitleCursor()
        if (titleCursor >= titleText.length) return
        titleText = titleText.substring(0, titleCursor) + titleText.substring(titleCursor + 1)
        markDirty()
    }

    private fun clampCaret() {
        if (editorLines.isEmpty()) editorLines.add("")
        caretLine = caretLine.coerceIn(0, editorLines.lastIndex)
        caretCol = caretCol.coerceIn(0, editorLines[caretLine].length)
        val maxScroll = (editorLines.size - editorVisibleRows).coerceAtLeast(0)
        editorScroll = editorScroll.coerceIn(0, maxScroll)
    }

    private fun ensureCaretVisible() {
        clampCaret()
        if (caretLine < editorScroll) editorScroll = caretLine
        if (caretLine >= editorScroll + editorVisibleRows) {
            editorScroll = caretLine - editorVisibleRows + 1
        }
        val maxScroll = (editorLines.size - editorVisibleRows).coerceAtLeast(0)
        editorScroll = editorScroll.coerceIn(0, maxScroll)
    }

    private fun insertChar(c: Char) {
        clampCaret()
        val line = editorLines[caretLine]
        editorLines[caretLine] = line.substring(0, caretCol) + c + line.substring(caretCol)
        caretCol++
        markDirty()
        ensureCaretVisible()
    }

    private fun insertText(text: String) {
        text.forEach { insertChar(it) }
    }

    private fun insertNewLine() {
        clampCaret()
        val line = editorLines[caretLine]
        val before = line.substring(0, caretCol)
        val after = line.substring(caretCol)
        editorLines[caretLine] = before
        editorLines.add(caretLine + 1, after)
        caretLine++
        caretCol = 0
        markDirty()
        ensureCaretVisible()
    }

    private fun backspace() {
        clampCaret()
        if (caretCol > 0) {
            val line = editorLines[caretLine]
            editorLines[caretLine] = line.substring(0, caretCol - 1) + line.substring(caretCol)
            caretCol--
            markDirty()
            ensureCaretVisible()
            return
        }
        if (caretLine > 0) {
            val prev = editorLines[caretLine - 1]
            val cur = editorLines[caretLine]
            val prevLen = prev.length
            editorLines[caretLine - 1] = prev + cur
            editorLines.removeAt(caretLine)
            caretLine--
            caretCol = prevLen
            markDirty()
            ensureCaretVisible()
        }
    }

    private fun deleteForward() {
        clampCaret()
        val line = editorLines[caretLine]
        if (caretCol < line.length) {
            editorLines[caretLine] = line.substring(0, caretCol) + line.substring(caretCol + 1)
            markDirty()
            ensureCaretVisible()
            return
        }
        if (caretLine < editorLines.lastIndex) {
            editorLines[caretLine] = line + editorLines[caretLine + 1]
            editorLines.removeAt(caretLine + 1)
            markDirty()
            ensureCaretVisible()
        }
    }

    private fun moveLeft() {
        clampCaret()
        if (caretCol > 0) {
            caretCol--
        } else if (caretLine > 0) {
            caretLine--
            caretCol = editorLines[caretLine].length
        }
        ensureCaretVisible()
    }

    private fun moveRight() {
        clampCaret()
        val line = editorLines[caretLine]
        if (caretCol < line.length) {
            caretCol++
        } else if (caretLine < editorLines.lastIndex) {
            caretLine++
            caretCol = 0
        }
        ensureCaretVisible()
    }

    private fun formattingCodeForShortcut(keyCode: Int, modifiers: Int): String? {
        val altDown = (modifiers and GLFW.GLFW_MOD_ALT) != 0
        if (!altDown) return null

        return when (keyCode) {
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

    private data class FormatNavLayout(
        val maxButtonRight: Int,
        val prevX: Int,
        val prevW: Int,
        val pageLabel: String,
        val pageX: Int,
        val nextX: Int,
        val nextW: Int,
    )

    private fun activeFormatCodes(): List<Pair<String, String>> {
        if (quickFormatPages.isEmpty()) return emptyList()
        if (formatPageIndex !in quickFormatPages.indices) {
            formatPageIndex = formatPageIndex.coerceIn(0, quickFormatPages.lastIndex)
        }
        return quickFormatPages[formatPageIndex]
    }

    private fun cycleFormatPage(step: Int) {
        if (quickFormatPages.isEmpty()) return
        val size = quickFormatPages.size
        val next = (formatPageIndex + step) % size
        formatPageIndex = if (next < 0) next + size else next
        setStatus("Format page ${formatPageIndex + 1}/$size")
    }

    private fun computeFormatNavLayout(tr: TextRenderer, x: Int, rightX: Int): FormatNavLayout {
        val prevLabel = "<"
        val nextLabel = ">"
        val prevW = tr.getWidth(prevLabel) + UiTheme.Spacing.BTN_PAD_H * 2
        val nextW = tr.getWidth(nextLabel) + UiTheme.Spacing.BTN_PAD_H * 2
        val pageLabel = "${formatPageIndex + 1}/${quickFormatPages.size.coerceAtLeast(1)}"
        val pageW = tr.getWidth(pageLabel)

        val nextX = rightX - nextW
        val pageX = nextX - 4 - pageW
        val prevX = pageX - 4 - prevW
        val maxButtonRight = prevX - 4

        return FormatNavLayout(
            maxButtonRight = maxButtonRight,
            prevX = prevX,
            prevW = prevW,
            pageLabel = pageLabel,
            pageX = pageX,
            nextX = nextX,
            nextW = nextW,
        )
    }

    private inline fun forEachFormatButton(
        tr: TextRenderer,
        x: Int,
        y: Int,
        block: (code: String, label: String, bx: Int, bw: Int) -> Boolean,
    ): Boolean {
        var bx = x
        for ((code, label) in activeFormatCodes()) {
            val bw = tr.getWidth(label) + UiTheme.Spacing.BTN_PAD_H * 2
            if (block(code, label, bx, bw)) return true
            bx += bw + 2
        }
        return false
    }

    private fun insertFormattingCode(code: String) {
        if (editingDraftId == null) createNewDraft()

        if (titleFocused) {
            code.forEach { insertTitleChar(it) }
        } else {
            editorFocused = true
            insertText(code)
        }
        setStatus("Inserted $code")
    }

    private fun heldWritableBookHand(client: MinecraftClient): Hand? {
        val player = client.player ?: return null
        return when {
            player.mainHandStack.isOf(Items.WRITABLE_BOOK) -> Hand.MAIN_HAND
            player.offHandStack.isOf(Items.WRITABLE_BOOK) -> Hand.OFF_HAND
            else -> null
        }
    }

    private fun copyCurrentEntryToHeldBook(): Boolean {
        val client = MinecraftClient.getInstance()
        val player = client.player
        if (player == null) {
            setStatus("Player unavailable.")
            return true
        }

        val content = editedContent()
        if (content.isBlank()) {
            setStatus("Entry is empty.")
            return true
        }

        val hand = heldWritableBookHand(client)
        if (hand == null) {
            setStatus("Hold a Book & Quill first.")
            return true
        }

        val pages = ctx.scribeDrafts.splitIntoPages(content)
        val error = WritableBookHelper.setHeldPages(player, hand, pages)
        if (error != null) {
            setStatus("Book write failed: $error")
        } else {
            setStatus("Entry written to Book & Quill (${pages.size}p).")
        }
        return true
    }

    private fun actionSpecs(content: String): List<ActionSpec> {
        val hasWritableBook = heldWritableBookHand(MinecraftClient.getInstance()) != null
        val firstPage = ctx.scribeDrafts.splitIntoPages(content).firstOrNull() ?: ""
        return listOf(
            ActionSpec(Action.NEW, "New", true),
            ActionSpec(Action.SAVE, "Save", editingDraftId != null),
            ActionSpec(Action.INSERT, "Insert", ctx.chatBridge.isAvailable && firstPage.isNotBlank()),
            ActionSpec(Action.BOOK, "B&Q", content.isNotBlank() && hasWritableBook),
            ActionSpec(Action.COPY, "Copy", content.isNotBlank()),
        )
    }

    private inline fun forEachAction(
        tr: TextRenderer,
        x: Int,
        y: Int,
        content: String,
        block: (ActionSpec, Int, Int) -> Boolean,
    ): Boolean {
        var bx = x
        for (spec in actionSpecs(content)) {
            val bw = tr.getWidth(spec.label) + UiTheme.Spacing.BTN_PAD_H * 2
            if (block(spec, bx, bw)) return true
            bx += bw + 3
        }
        return false
    }

    private fun performAction(action: Action): Boolean {
        return when (action) {
            Action.NEW -> {
                createNewDraft()
                true
            }

            Action.SAVE -> {
                val saved = persistCurrentDraft(force = true)
                setStatus(if (saved) "Draft saved." else "No draft selected.")
                true
            }

            Action.INSERT -> {
                val firstPage = ctx.scribeDrafts.splitIntoPages(editedContent()).firstOrNull() ?: ""
                if (!ctx.chatBridge.isAvailable || firstPage.isBlank()) {
                    setStatus("Chat insert unavailable.")
                } else {
                    ctx.chatBridge.insertAtCursor(firstPage)
                    setStatus("Inserted first page into chat.")
                }
                true
            }

            Action.BOOK -> copyCurrentEntryToHeldBook()

            Action.COPY -> {
                val content = editedContent()
                if (content.isBlank()) {
                    setStatus("Nothing to copy.")
                } else {
                    ctx.clipboard.copy(content)
                    ctx.notification.info("Draft copied")
                    setStatus("Copied to clipboard.")
                }
                true
            }
        }
    }

    private fun computeLayout(ws: WindowState): Layout {
        val cx = ws.contentX
        val cy = ws.contentY
        val cw = ws.contentW
        val ch = ws.contentH

        val remaining = (ch - HEADER_H).coerceAtLeast(40)
        var listH = (remaining * 0.38f).toInt().coerceAtLeast(20)
        var detailH = remaining - listH - UiTheme.Spacing.WIDGET_GAP
        if (detailH < 64) {
            listH = (remaining - UiTheme.Spacing.WIDGET_GAP - 64).coerceAtLeast(20)
            detailH = remaining - listH - UiTheme.Spacing.WIDGET_GAP
        }

        val listY = cy + HEADER_H
        val detailY = listY + listH + UiTheme.Spacing.WIDGET_GAP

        val detailTopH = 44
        val titleX = cx + 4
        val titleY = detailY + 3
        val titleW = (cw - 8).coerceAtLeast(20)
        val titleH = TITLE_FIELD_H
        val formatX = cx + 4
        val formatY = detailY + 27

        val editorY = detailY + detailTopH
        val buttonY = detailY + detailH - HudButton.DEFAULT_H - 3
        val editorH = (buttonY - editorY - 2).coerceAtLeast(12)

        return Layout(
            listX = cx,
            listY = listY,
            listW = cw,
            listH = listH,
            detailX = cx,
            detailY = detailY,
            detailW = cw,
            detailH = detailH,
            titleX = titleX,
            titleY = titleY,
            titleW = titleW,
            titleH = titleH,
            formatX = formatX,
            formatY = formatY,
            editorX = cx + 3,
            editorY = editorY,
            editorW = cw - 6,
            editorH = editorH,
            buttonX = cx + 4,
            buttonY = buttonY,
        )
    }

    private fun render(
        dc: DrawContext,
        tr: TextRenderer,
        ws: WindowState,
        mx: Int,
        my: Int,
        delta: Float,
    ) {
        refreshList()
        maybeAutoSave()

        val now = System.currentTimeMillis()
        val layout = computeLayout(ws)

        dc.drawTextWithShadow(tr, "Drafts", ws.contentX + 2, ws.contentY, UiTheme.Palette.TEXT_SECONDARY)

        listWidget.render(dc, tr, layout.listX, layout.listY, layout.listW, layout.listH, mx, my, delta)

        dc.fill(
            layout.detailX,
            layout.detailY,
            layout.detailX + layout.detailW,
            layout.detailY + layout.detailH,
            UiTheme.Palette.WINDOW_BG,
        )
        dc.drawStrokedRectangle(layout.detailX, layout.detailY, layout.detailW, layout.detailH, UiTheme.Palette.BORDER_FAINT)

        val draft = selectedDraft()
        val content = editedContent()
        val displayTitle = if (titleText.isBlank() && !titleFocused) "Untitled" else titleText
        val pages = ctx.scribeDrafts.estimatePageCount(content)
        val meta = if (draft == null) {
            "Click New to start writing."
        } else {
            "${pages} p | ${content.length} ch | ${if (dirty) "unsaved" else "saved"}"
        }

        dc.fill(
            layout.titleX,
            layout.titleY,
            layout.titleX + layout.titleW,
            layout.titleY + layout.titleH,
            UiTheme.Palette.FIELD_BG,
        )
        dc.drawStrokedRectangle(
            layout.titleX,
            layout.titleY,
            layout.titleW,
            layout.titleH,
            if (titleFocused) UiTheme.Palette.FIELD_BORDER_FOCUS else UiTheme.Palette.FIELD_BORDER,
        )

        val titleColor = if (titleText.isBlank() && !titleFocused) UiTheme.Palette.TEXT_MUTED else UiTheme.Palette.TEXT_TITLE
        dc.drawTextWithShadow(tr, displayTitle, layout.titleX + 3, layout.titleY + 3, titleColor)
        if (titleFocused && (now / 530L) % 2L == 0L) {
            clampTitleCursor()
            val cursorX = layout.titleX + 3 + tr.getWidth(titleText.substring(0, titleCursor))
            dc.fill(cursorX, layout.titleY + 3, cursorX + 1, layout.titleY + 11, UiTheme.Palette.FIELD_CURSOR)
        }

        dc.drawTextWithShadow(tr, meta, layout.detailX + 4, layout.detailY + 19, UiTheme.Palette.TEXT_MUTED)

        renderFormatButtons(
            dc = dc,
            tr = tr,
            x = layout.formatX,
            y = layout.formatY,
            rightX = layout.detailX + layout.detailW - 4,
            mx = mx,
            my = my,
        )

        activeStatus(now)?.let { status ->
            val statusX = layout.detailX + layout.detailW - tr.getWidth(status) - 4
            dc.drawTextWithShadow(tr, status, statusX, layout.detailY + 19, UiTheme.Palette.TEXT_SECONDARY)
        }

        renderEditor(dc, tr, layout.editorX, layout.editorY, layout.editorW, layout.editorH, now)

        renderActions(dc, tr, layout.buttonX, layout.buttonY, content, mx, my)
    }

    private fun renderEditor(
        dc: DrawContext,
        tr: TextRenderer,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        now: Long,
    ) {
        dc.fill(x, y, x + w, y + h, UiTheme.Palette.FIELD_BG)
        dc.drawStrokedRectangle(
            x,
            y,
            w,
            h,
            if (editorFocused) UiTheme.Palette.FIELD_BORDER_FOCUS else UiTheme.Palette.FIELD_BORDER,
        )

        if (editingDraftId == null) {
            dc.drawTextWithShadow(tr, "No draft loaded.", x + 4, y + 4, UiTheme.Palette.TEXT_MUTED)
            return
        }

        editorVisibleRows = ((h - 4) / EDITOR_LINE_H).coerceAtLeast(1)
        clampCaret()

        val maxScroll = (editorLines.size - editorVisibleRows).coerceAtLeast(0)
        editorScroll = editorScroll.coerceIn(0, maxScroll)

        dc.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1)
        for (row in 0 until editorVisibleRows) {
            val lineIdx = editorScroll + row
            if (lineIdx !in editorLines.indices) break

            val lineY = y + 2 + row * EDITOR_LINE_H
            if (editorFocused && lineIdx == caretLine) {
                dc.fill(x + 1, lineY - 1, x + w - 1, lineY + EDITOR_LINE_H, UiTheme.Palette.LIST_ROW_SEL)
            }

            val line = editorLines[lineIdx]
            dc.drawTextWithShadow(tr, line, x + 4, lineY, UiTheme.Palette.TEXT_PRIMARY)

            if (editorFocused && lineIdx == caretLine && (now / 530L) % 2L == 0L) {
                val cursorX = x + 4 + tr.getWidth(line.substring(0, caretCol.coerceIn(0, line.length)))
                dc.fill(cursorX, lineY, cursorX + 1, lineY + 8, UiTheme.Palette.FIELD_CURSOR)
            }
        }
        dc.disableScissor()

        val lineHint = "L${caretLine + 1}/${editorLines.size}"
        dc.drawTextWithShadow(tr, lineHint, x + w - tr.getWidth(lineHint) - 3, y + h - 10, UiTheme.Palette.TEXT_MUTED)
    }

    private fun renderFormatButtons(
        dc: DrawContext,
        tr: TextRenderer,
        x: Int,
        y: Int,
        rightX: Int,
        mx: Int,
        my: Int,
    ) {
        val nav = computeFormatNavLayout(tr, x, rightX)
        forEachFormatButton(tr, x, y) { _, label, bx, bw ->
            if (bx + bw > nav.maxButtonRight) return@forEachFormatButton true
            HudButton(label, onClick = {}, enabled = true)
                .render(dc, tr, bx, y, bw, HudButton.DEFAULT_H, mx, my, 0f)
            false
        }

        HudButton("<", onClick = {}, enabled = quickFormatPages.size > 1)
            .render(dc, tr, nav.prevX, y, nav.prevW, HudButton.DEFAULT_H, mx, my, 0f)
        dc.drawTextWithShadow(tr, nav.pageLabel, nav.pageX, y + 3, UiTheme.Palette.TEXT_MUTED)
        HudButton(">", onClick = {}, enabled = quickFormatPages.size > 1)
            .render(dc, tr, nav.nextX, y, nav.nextW, HudButton.DEFAULT_H, mx, my, 0f)
    }

    private fun renderActions(
        dc: DrawContext,
        tr: TextRenderer,
        x: Int,
        y: Int,
        content: String,
        mx: Int,
        my: Int,
    ) {
        forEachAction(tr, x, y, content) { spec, bx, bw ->
            HudButton(spec.label, onClick = {}, enabled = spec.enabled)
                .render(dc, tr, bx, y, bw, HudButton.DEFAULT_H, mx, my, 0f)
            false
        }
    }

    private fun handleActionClick(
        x: Int,
        y: Int,
        content: String,
        mx: Int,
        my: Int,
    ): Boolean {
        val tr = MinecraftClient.getInstance().textRenderer
        return forEachAction(tr, x, y, content) { spec, bx, bw ->
            if (!spec.enabled) return@forEachAction false
            if (!hitTest(bx, y, bw, HudButton.DEFAULT_H, mx, my)) return@forEachAction false
            performAction(spec.action)
            true
        }
    }

    private fun handleFormatClick(x: Int, y: Int, rightX: Int, mx: Int, my: Int): Boolean {
        val tr = MinecraftClient.getInstance().textRenderer
        val nav = computeFormatNavLayout(tr, x, rightX)

        if (quickFormatPages.size > 1 && hitTest(nav.prevX, y, nav.prevW, HudButton.DEFAULT_H, mx, my)) {
            cycleFormatPage(-1)
            return true
        }
        if (quickFormatPages.size > 1 && hitTest(nav.nextX, y, nav.nextW, HudButton.DEFAULT_H, mx, my)) {
            cycleFormatPage(1)
            return true
        }

        return forEachFormatButton(tr, x, y) { code, _, bx, bw ->
            if (bx + bw > nav.maxButtonRight) return@forEachFormatButton true
            if (!hitTest(bx, y, bw, HudButton.DEFAULT_H, mx, my)) return@forEachFormatButton false
            insertFormattingCode(code)
            true
        }
    }

    private fun handleFormatScroll(layout: Layout, mx: Int, my: Int, dy: Double): Boolean {
        if (quickFormatPages.size <= 1 || dy == 0.0) return false

        val rightX = layout.detailX + layout.detailW - 4
        val rowW = (rightX - layout.formatX).coerceAtLeast(0)
        if (!hitTest(layout.formatX, layout.formatY, rowW, HudButton.DEFAULT_H, mx, my)) {
            return false
        }

        cycleFormatPage(if (dy > 0) -1 else 1)
        return true
    }

    // Input

    private fun onClick(ws: WindowState, mx: Int, my: Int, button: Int): Boolean {
        refreshList()
        val layout = computeLayout(ws)

        val selectedBefore = editingDraftId
        if (listWidget.onClick(layout.listX, layout.listY, layout.listW, layout.listH, mx, my, button)) {
            val chosen = selectedDraft()
            if (chosen != null && chosen.id != selectedBefore) {
                openDraft(chosen, persistCurrent = true)
            }
            editorFocused = false
            titleFocused = false
            return true
        }

        if (hitTest(layout.titleX, layout.titleY, layout.titleW, layout.titleH, mx, my)) {
            if (editingDraftId == null) createNewDraft()
            titleFocused = true
            editorFocused = false
            val tr = MinecraftClient.getInstance().textRenderer
            titleCursor = cursorColForX(tr, titleText, mx - (layout.titleX + 3))
            return true
        }

        if (handleFormatClick(layout.formatX, layout.formatY, layout.detailX + layout.detailW - 4, mx, my)) {
            return true
        }

        if (handleActionClick(layout.buttonX, layout.buttonY, editedContent(), mx, my)) {
            editorFocused = false
            titleFocused = false
            return true
        }

        if (hitTest(layout.editorX, layout.editorY, layout.editorW, layout.editorH, mx, my)) {
            if (editingDraftId == null) createNewDraft()
            editorFocused = true
            titleFocused = false
            clampCaret()

            val tr = MinecraftClient.getInstance().textRenderer
            val relativeY = (my - (layout.editorY + 2)).coerceAtLeast(0)
            val targetLine = (editorScroll + relativeY / EDITOR_LINE_H).coerceIn(0, editorLines.lastIndex)
            caretLine = targetLine
            caretCol = cursorColForX(tr, editorLines[targetLine], mx - (layout.editorX + 4))
            ensureCaretVisible()
            return true
        }

        editorFocused = false
        titleFocused = false
        return false
    }

    private fun onScroll(ws: WindowState, mx: Int, my: Int, dx: Double, dy: Double): Boolean {
        val layout = computeLayout(ws)
        if (listWidget.onScroll(layout.listX, layout.listY, layout.listW, layout.listH, mx, my, dx, dy)) {
            return true
        }

        if (handleFormatScroll(layout, mx, my, dy)) {
            return true
        }

        if (hitTest(layout.editorX, layout.editorY, layout.editorW, layout.editorH, mx, my)) {
            val maxScroll = (editorLines.size - editorVisibleRows).coerceAtLeast(0)
            editorScroll = (editorScroll - dy.toInt()).coerceIn(0, maxScroll)
            return true
        }

        return false
    }

    private fun onKeyPress(ws: WindowState, keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val ctrlDown = (modifiers and GLFW.GLFW_MOD_CONTROL) != 0

        if (ctrlDown && keyCode == GLFW.GLFW_KEY_S) {
            val saved = persistCurrentDraft(force = true)
            setStatus(if (saved) "Draft saved." else "No draft selected.")
            return true
        }

        if (ctrlDown && keyCode == GLFW.GLFW_KEY_N) {
            createNewDraft()
            return true
        }

        formattingCodeForShortcut(keyCode, modifiers)?.let { code ->
            insertFormattingCode(code)
            return true
        }

        if (titleFocused) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    titleFocused = false
                    return true
                }

                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    titleFocused = false
                    editorFocused = true
                    return true
                }

                GLFW.GLFW_KEY_BACKSPACE -> {
                    titleBackspace()
                    return true
                }

                GLFW.GLFW_KEY_DELETE -> {
                    titleDeleteForward()
                    return true
                }

                GLFW.GLFW_KEY_LEFT -> {
                    titleCursor = (titleCursor - 1).coerceAtLeast(0)
                    return true
                }

                GLFW.GLFW_KEY_RIGHT -> {
                    titleCursor = (titleCursor + 1).coerceAtMost(titleText.length)
                    return true
                }

                GLFW.GLFW_KEY_HOME -> {
                    titleCursor = 0
                    return true
                }

                GLFW.GLFW_KEY_END -> {
                    titleCursor = titleText.length
                    return true
                }
            }
            return false
        }

        if (!editorFocused) return false
        if (editingDraftId == null) return false

        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> {
                editorFocused = false
                return true
            }

            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertNewLine()
                return true
            }

            GLFW.GLFW_KEY_BACKSPACE -> {
                backspace()
                return true
            }

            GLFW.GLFW_KEY_DELETE -> {
                deleteForward()
                return true
            }

            GLFW.GLFW_KEY_LEFT -> {
                moveLeft()
                return true
            }

            GLFW.GLFW_KEY_RIGHT -> {
                moveRight()
                return true
            }

            GLFW.GLFW_KEY_UP -> {
                clampCaret()
                caretLine = (caretLine - 1).coerceAtLeast(0)
                caretCol = caretCol.coerceAtMost(editorLines[caretLine].length)
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_DOWN -> {
                clampCaret()
                caretLine = (caretLine + 1).coerceAtMost(editorLines.lastIndex)
                caretCol = caretCol.coerceAtMost(editorLines[caretLine].length)
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_HOME -> {
                if (ctrlDown) {
                    caretLine = 0
                }
                caretCol = 0
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_END -> {
                if (ctrlDown) {
                    caretLine = editorLines.lastIndex
                }
                caretCol = editorLines[caretLine].length
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_PAGE_UP -> {
                editorScroll = (editorScroll - editorVisibleRows).coerceAtLeast(0)
                caretLine = editorScroll.coerceIn(0, editorLines.lastIndex)
                caretCol = caretCol.coerceAtMost(editorLines[caretLine].length)
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_PAGE_DOWN -> {
                val maxScroll = (editorLines.size - editorVisibleRows).coerceAtLeast(0)
                editorScroll = (editorScroll + editorVisibleRows).coerceAtMost(maxScroll)
                caretLine = (editorScroll + editorVisibleRows - 1).coerceIn(0, editorLines.lastIndex)
                caretCol = caretCol.coerceAtMost(editorLines[caretLine].length)
                ensureCaretVisible()
                return true
            }

            GLFW.GLFW_KEY_TAB -> {
                repeat(4) { insertChar(' ') }
                return true
            }
        }

        return false
    }

    private fun onCharTyped(ws: WindowState, char: Char, modifiers: Int): Boolean {
        if (char.code < 32) return false

        if (titleFocused) {
            if ((modifiers and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_ALT)) != 0) return false
            if (editingDraftId == null) createNewDraft()
            insertTitleChar(char)
            return true
        }

        if (!editorFocused) return false
        if ((modifiers and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_ALT)) != 0) return false

        if (editingDraftId == null) createNewDraft()
        insertChar(char)
        return true
    }

    private fun cursorColForX(tr: TextRenderer, text: String, relativeX: Int): Int {
        if (relativeX <= 0) return 0
        for (i in 1..text.length) {
            if (tr.getWidth(text.substring(0, i)) > relativeX) return i - 1
        }
        return text.length
    }
}
