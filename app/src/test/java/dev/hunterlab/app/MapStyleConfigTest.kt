package dev.hunterlab.app

import dev.hunterlab.app.ui.map.MapStyleConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapStyleConfigTest {
    @Test fun noKeyNeverRequestsDemoTiles() {
        for (key in listOf(null, "", "   ")) assertNull(MapStyleConfig.style(key))
        assertFalse(MapStyleConfig.EMPTY.contains("https://"))
    }
    @Test fun keyCannotInjectJsonOrAnotherTileHost() {
        val style = MapStyleConfig.style("fixture\"/&key=other")!!
        assertTrue(style.contains("key=fixture%22%2F%26key%3Dother"))
        assertEquals(1, Regex("https://").findAll(style).count())
        assertTrue(style.contains("https://api.maptiler.com/maps/streets-v4-dark/256/"))
        assertFalse(style.contains("glyphs"))
        assertFalse(style.contains("sprite"))
    }
    @Test fun attributionRemainsPresentInStyle() {
        val style = MapStyleConfig.style("fixture-key")!!
        assertTrue(style.contains("© MapTiler © OpenStreetMap contributors"))
        assertTrue(style.contains("\"type\":\"raster\""))
    }
    @Test fun nativeSdkConfinedToMapRenderer() {
        val root = File("src/main/java")
        val imports = root.walkTopDown().filter { it.extension == "kt" && it.readText().contains("import org.maplibre.android") }.toList()
        assertEquals(listOf("RouteMap.kt"), imports.map { it.name })
        val renderer = imports.single().readText()
        assertFalse(renderer.contains("activateLocationComponent"))
        assertFalse(renderer.contains("OfflineManager"))
        assertFalse(renderer.contains("BleController"))
        assertTrue(renderer.contains("Logger.setVerbosity(Logger.NONE)"))
    }
}
