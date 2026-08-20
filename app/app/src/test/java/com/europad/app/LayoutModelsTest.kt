package com.europad.app

import com.europad.app.ui.DeckRect
import com.europad.app.ui.ElementPosition
import com.europad.app.ui.LayoutPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModelsTest {

    // ============================
    // ElementPosition Tests
    // ============================

    @Test
    fun `ElementPosition stores all required fields`() {
        val position = ElementPosition(
            id = "WHEEL",
            cx = 0.5f,
            cy = 0.6f,
            w = 0.3f,
            h = 0.4f
        )

        assertEquals("WHEEL", position.id)
        assertEquals(0.5f, position.cx, 0.0001f)
        assertEquals(0.6f, position.cy, 0.0001f)
        assertEquals(0.3f, position.w, 0.0001f)
        assertEquals(0.4f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition accepts minimum boundary values`() {
        val position = ElementPosition(
            id = "TEST",
            cx = 0.0f,
            cy = 0.0f,
            w = 0.0f,
            h = 0.0f
        )

        assertEquals(0.0f, position.cx, 0.0001f)
        assertEquals(0.0f, position.cy, 0.0001f)
        assertEquals(0.0f, position.w, 0.0001f)
        assertEquals(0.0f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition accepts maximum boundary values`() {
        val position = ElementPosition(
            id = "TEST",
            cx = 1.0f,
            cy = 1.0f,
            w = 1.0f,
            h = 1.0f
        )

        assertEquals(1.0f, position.cx, 0.0001f)
        assertEquals(1.0f, position.cy, 0.0001f)
        assertEquals(1.0f, position.w, 0.0001f)
        assertEquals(1.0f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps cx below range via create`() {
        val position = ElementPosition.create("TEST", -0.1f, 0.5f, 0.1f, 0.1f)
        assertEquals(0.0f, position.cx, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps cx above range via create`() {
        val position = ElementPosition.create("TEST", 1.1f, 0.5f, 0.1f, 0.1f)
        assertEquals(1.0f, position.cx, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps cy below range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, -0.1f, 0.1f, 0.1f)
        assertEquals(0.0f, position.cy, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps cy above range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, 1.1f, 0.1f, 0.1f)
        assertEquals(1.0f, position.cy, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps w below range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, 0.5f, -0.1f, 0.1f)
        assertEquals(0.0f, position.w, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps w above range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, 0.5f, 1.1f, 0.1f)
        assertEquals(1.0f, position.w, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps h below range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, 0.5f, 0.1f, -0.1f)
        assertEquals(0.0f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition clamps h above range via create`() {
        val position = ElementPosition.create("TEST", 0.5f, 0.5f, 0.1f, 1.1f)
        assertEquals(1.0f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition converts to DeckRect correctly`() {
        val position = ElementPosition(
            id = "WHEEL",
            cx = 0.2f,
            cy = 0.65f,
            w = 0.278f,
            h = 0.5f
        )

        val deckRect = position.toDeckRect()

        assertEquals(0.2f, deckRect.cx, 0.0001f)
        assertEquals(0.65f, deckRect.cy, 0.0001f)
        assertEquals(0.278f, deckRect.w, 0.0001f)
        assertEquals(0.5f, deckRect.h, 0.0001f)
    }

    @Test
    fun `ElementPosition can be created from DeckRect`() {
        val deckRect = DeckRect(cx = 0.3f, cy = 0.7f, w = 0.2f, h = 0.15f)

        val position = ElementPosition.fromDeckRect("BRAKE", deckRect)

        assertEquals("BRAKE", position.id)
        assertEquals(0.3f, position.cx, 0.0001f)
        assertEquals(0.7f, position.cy, 0.0001f)
        assertEquals(0.2f, position.w, 0.0001f)
        assertEquals(0.15f, position.h, 0.0001f)
    }

    // ============================
    // JSON Serialization Tests
    // ============================

    @Test
    fun `ElementPosition can be serialized to JSON`() {
        val position = ElementPosition(
            id = "LIGHTS",
            cx = 0.06f,
            cy = 0.10f,
            w = 0.019f,
            h = 0.13f
        )

        val json = Json.encodeToString(position)

        assertTrue(json.contains("\"id\":\"LIGHTS\""))
        assertTrue(json.contains("\"cx\":0.06"))
        assertTrue(json.contains("\"cy\":0.1"))
        assertTrue(json.contains("\"w\":0.019"))
        assertTrue(json.contains("\"h\":0.13"))
    }

    @Test
    fun `ElementPosition can be deserialized from JSON`() {
        val json = """{"id":"ACCEL","cx":0.85,"cy":0.63,"w":0.105,"h":0.46}"""

        val position = Json.decodeFromString<ElementPosition>(json)

        assertEquals("ACCEL", position.id)
        assertEquals(0.85f, position.cx, 0.0001f)
        assertEquals(0.63f, position.cy, 0.0001f)
        assertEquals(0.105f, position.w, 0.0001f)
        assertEquals(0.46f, position.h, 0.0001f)
    }

    @Test
    fun `ElementPosition serialization round-trip preserves values`() {
        val original = ElementPosition(
            id = "WHEEL",
            cx = 0.2f,
            cy = 0.65f,
            w = 0.278f,
            h = 0.5f
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<ElementPosition>(json)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.cx, deserialized.cx, 0.0001f)
        assertEquals(original.cy, deserialized.cy, 0.0001f)
        assertEquals(original.w, deserialized.w, 0.0001f)
        assertEquals(original.h, deserialized.h, 0.0001f)
    }

    // ============================
    // LayoutPreset Tests
    // ============================

    @Test
    fun `LayoutPreset can be serialized to JSON`() {
        val elements = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.2f, 0.65f, 0.278f, 0.5f)
        )

        val preset = LayoutPreset(
            version = 1,
            mode = "wheel",
            elements = elements,
            timestamp = 1699876543210L
        )

        val json = Json { encodeDefaults = true }.encodeToString(preset)

        assertTrue(json.contains("\"version\":1"))
        assertTrue(json.contains("\"mode\":\"wheel\""))
        assertTrue(json.contains("\"LIGHTS\""))
        assertTrue(json.contains("\"WHEEL\""))
        assertTrue(json.contains("\"timestamp\":1699876543210"))
    }

    @Test
    fun `LayoutPreset can be deserialized from JSON`() {
        val json = """
            {
                "version": 1,
                "mode": "wheel",
                "elements": {
                    "LIGHTS": {"id":"LIGHTS","cx":0.06,"cy":0.10,"w":0.019,"h":0.13}
                },
                "timestamp": 1699876543210
            }
        """.trimIndent()

        val preset = Json.decodeFromString<LayoutPreset>(json)

        assertEquals(1, preset.version)
        assertEquals("wheel", preset.mode)
        assertEquals(1, preset.elements.size)
        assertTrue(preset.elements.containsKey("LIGHTS"))
        assertEquals(1699876543210L, preset.timestamp)
    }

    @Test
    fun `LayoutPreset serialization round-trip preserves all data`() {
        val elements = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.2f, 0.65f, 0.278f, 0.5f),
            "ACCEL" to ElementPosition("ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )

        val original = LayoutPreset(
            version = 1,
            mode = "gyro",
            elements = elements,
            timestamp = 1699876543210L
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<LayoutPreset>(json)

        assertEquals(original.version, deserialized.version)
        assertEquals(original.mode, deserialized.mode)
        assertEquals(original.elements.size, deserialized.elements.size)
        assertEquals(original.timestamp, deserialized.timestamp)

        for ((key, originalPos) in original.elements) {
            val deserializedPos = deserialized.elements[key]!!
            assertEquals(originalPos.id, deserializedPos.id)
            assertEquals(originalPos.cx, deserializedPos.cx, 0.0001f)
            assertEquals(originalPos.cy, deserializedPos.cy, 0.0001f)
            assertEquals(originalPos.w, deserializedPos.w, 0.0001f)
            assertEquals(originalPos.h, deserializedPos.h, 0.0001f)
        }
    }

    @Test
    fun `LayoutPreset supports empty elements map`() {
        val preset = LayoutPreset(
            version = 1,
            mode = "wheel",
            elements = emptyMap(),
            timestamp = System.currentTimeMillis()
        )

        val json = Json.encodeToString(preset)
        val deserialized = Json.decodeFromString<LayoutPreset>(json)

        assertTrue(deserialized.elements.isEmpty())
    }
}
