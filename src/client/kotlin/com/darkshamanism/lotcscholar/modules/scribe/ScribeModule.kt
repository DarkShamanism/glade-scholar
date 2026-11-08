package com.darkshamanism.lotcscholar.modules.scribe

import com.darkshamanism.lotcscholar.core.*
import com.darkshamanism.lotcscholar.ui.modules.scribe.ScribeWindow
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.lwjgl.glfw.GLFW

/**
 * Scribe — the LoTC writing workstation.
 *
 * Accessible from the Hub or via the configurable keybind (no default key — open
 * from the Hub and assign one in MC Controls if desired).
 */
class ScribeModule : GladeModuleProvider, TickableModule, HasScreen, HasKeybinds {

    override val id       = "scribe"
    override val title    = "Scribe"
    override val category = ModuleCategory.ARCHIVE

    private lateinit var ctx: ClientContext
    private var scribeKey: net.minecraft.client.option.KeyBinding? = null
    private var overlayWindow: ScribeWindow? = null

    override fun register(context: ClientContext) {
        ctx = context
        if (overlayWindow == null) {
            overlayWindow = ScribeWindow(context)
        }
        context.windowRegistry.register(overlayWindow!!.createDescriptor())
    }

    override fun registerKeybinds(context: ClientContext) {
        scribeKey = context.keybinds.register(
            "key.lotcscholar.scribe",
            GLFW.GLFW_KEY_UNKNOWN,   // no default — user sets in Controls
        )
    }

    override fun handleTick(client: MinecraftClient) {
        while (scribeKey?.wasPressed() == true) {
            val cur = client.currentScreen
            if (cur !is ScribeScreen) client.setScreen(ScribeScreen(cur, ctx))
        }
    }

    override fun openScreen(parent: Screen?) {
        net.minecraft.client.MinecraftClient.getInstance()
            .setScreen(ScribeScreen(parent, ctx))
    }
}
