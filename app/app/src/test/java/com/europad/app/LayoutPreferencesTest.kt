package com.europad.app

import android.content.SharedPreferences
import com.europad.app.ui.ElementPosition
import com.europad.app.ui.LayoutPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LayoutPreferences persistence layer.
 * 
 * Tests verify:
 * - AC 3.1: Store positions in SharedPreferences on save
 * - AC 3.3: Load returns null for corrupted/inaccessible data
 * - AC 3.4: Clear removes stored custom positions
 * - AC 4.2: Deserialize fractional positions correctly
 * - AC 4.4: Clamp out-of-range values to [0.0, 1.0]
 */
class LayoutPreferencesTest {

    private lateinit var mockPrefs: MockSharedPreferences

    @Before
    fun setup() {
        mockPrefs = MockSharedPreferences()
    }

    // AC 3.1: Store positions in SharedPreferences on save
    @Test
    fun `save stores positions in SharedPreferences for wheel mode`() {
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", positions)

        assertTrue(mockPrefs.contains("layout_wheel"))
        assertNotNull(mockPrefs.getString("layout_wheel", null))
    }

    @Test
    fun `save produces valid JSON with expected structure`() {
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", positions)

        val json = mockPrefs.getString("layout_wheel", null)
        assertNotNull(json)
        
        // Verify JSON contains expected fields
        assertTrue(json!!.contains("\"version\""))
        assertTrue(json.contains("\"mode\""))
        assertTrue(json.contains("\"elements\""))
        assertTrue(json.contains("\"timestamp\""))
        assertTrue(json.contains("\"LIGHTS\""))
        assertTrue(json.contains("\"WHEEL\""))
        assertTrue(json.contains("\"cx\""))
        assertTrue(json.contains("\"cy\""))
        assertTrue(json.contains("\"w\""))
        assertTrue(json.contains("\"h\""))
        
        // Verify it can be parsed back
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        assertNotNull(loaded)
    }

    @Test
    fun `save stores positions in SharedPreferences for gyro mode`() {
        val positions = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )

        LayoutPreferences.save(mockPrefs, "gyro", positions)

        assertTrue(mockPrefs.contains("layout_gyro"))
        assertNotNull(mockPrefs.getString("layout_gyro", null))
    }

    // AC 4.2: Deserialize fractional positions correctly
    @Test
    fun `load deserializes fractional positions correctly`() {
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f),
            "ACCEL" to ElementPosition("ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", positions)
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")

        assertNotNull(loaded)
        assertEquals(3, loaded!!.size)
        
        // Verify each position was deserialized correctly
        val lights = loaded["LIGHTS"]
        assertNotNull(lights)
        assertEquals("LIGHTS", lights!!.id)
        assertEquals(0.06f, lights.cx, 0.0001f)
        assertEquals(0.10f, lights.cy, 0.0001f)
        assertEquals(0.019f, lights.w, 0.0001f)
        assertEquals(0.13f, lights.h, 0.0001f)
    }

    // AC 3.3: Load returns null for corrupted/inaccessible data
    @Test
    fun `load returns null when no data exists`() {
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        assertNull(loaded)
    }

    @Test
    fun `load returns null for corrupted JSON`() {
        mockPrefs.edit().putString("layout_wheel", "{corrupted json}").apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        assertNull(loaded)
    }

    @Test
    fun `load returns null for malformed JSON structure`() {
        mockPrefs.edit().putString("layout_wheel", "{\"mode\":\"wheel\"}").apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        assertNull(loaded)
    }

    @Test
    fun `load returns null for empty elements map`() {
        val json = """{"version":1,"mode":"wheel","elements":{},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        assertNull(loaded)
    }

    // AC 4.4: Clamp out-of-range values to [0.0, 1.0]
    @Test
    fun `load clamps cx values outside valid range`() {
        val json = """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":-0.5,"cy":0.5,"w":0.1,"h":0.1}},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(0.0f, test!!.cx, 0.0001f) // Clamped to 0.0
    }

    @Test
    fun `load clamps cy values outside valid range`() {
        val json = """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5,"cy":1.5,"w":0.1,"h":0.1}},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(1.0f, test!!.cy, 0.0001f) // Clamped to 1.0
    }

    @Test
    fun `load clamps width values outside valid range`() {
        val json = """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5,"cy":0.5,"w":2.0,"h":0.1}},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(1.0f, test!!.w, 0.0001f) // Clamped to 1.0
    }

    @Test
    fun `load clamps height values outside valid range`() {
        val json = """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5,"cy":0.5,"w":0.1,"h":-0.2}},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(0.0f, test!!.h, 0.0001f) // Clamped to 0.0
    }

    @Test
    fun `load clamps multiple out-of-range values in same element`() {
        val json = """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":-5.0,"cy":10.0,"w":-1.0,"h":3.0}},"timestamp":1699876543210}"""
        mockPrefs.edit().putString("layout_wheel", json).apply()
        
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(0.0f, test!!.cx, 0.0001f)
        assertEquals(1.0f, test.cy, 0.0001f)
        assertEquals(0.0f, test.w, 0.0001f)
        assertEquals(1.0f, test.h, 0.0001f)
    }

    @Test
    fun `save clamps out-of-range values before storing`() {
        val positions = mapOf(
            "TEST" to ElementPosition("TEST", -0.5f, 1.5f, 2.0f, -0.2f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", positions)
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")

        assertNotNull(loaded)
        val test = loaded!!["TEST"]
        assertNotNull(test)
        assertEquals(0.0f, test!!.cx, 0.0001f)
        assertEquals(1.0f, test.cy, 0.0001f)
        assertEquals(1.0f, test.w, 0.0001f)
        assertEquals(0.0f, test.h, 0.0001f)
    }

    // AC 3.4: Clear removes stored custom positions
    @Test
    fun `clear removes stored custom positions for wheel mode`() {
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f)
        )
        LayoutPreferences.save(mockPrefs, "wheel", positions)
        assertTrue(mockPrefs.contains("layout_wheel"))

        LayoutPreferences.clear(mockPrefs, "wheel")

        assertFalse(mockPrefs.contains("layout_wheel"))
        assertNull(LayoutPreferences.load(mockPrefs, "wheel"))
    }

    @Test
    fun `clear removes stored custom positions for gyro mode`() {
        val positions = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )
        LayoutPreferences.save(mockPrefs, "gyro", positions)
        assertTrue(mockPrefs.contains("layout_gyro"))

        LayoutPreferences.clear(mockPrefs, "gyro")

        assertFalse(mockPrefs.contains("layout_gyro"))
        assertNull(LayoutPreferences.load(mockPrefs, "gyro"))
    }

    @Test
    fun `clear does not affect other mode's layout`() {
        val wheelPositions = mapOf(
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f)
        )
        val gyroPositions = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )
        
        LayoutPreferences.save(mockPrefs, "wheel", wheelPositions)
        LayoutPreferences.save(mockPrefs, "gyro", gyroPositions)

        LayoutPreferences.clear(mockPrefs, "wheel")

        assertNull(LayoutPreferences.load(mockPrefs, "wheel"))
        assertNotNull(LayoutPreferences.load(mockPrefs, "gyro"))
    }

    @Test
    fun `clearAll removes both wheel and gyro layouts`() {
        val wheelPositions = mapOf(
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f)
        )
        val gyroPositions = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )
        
        LayoutPreferences.save(mockPrefs, "wheel", wheelPositions)
        LayoutPreferences.save(mockPrefs, "gyro", gyroPositions)

        LayoutPreferences.clearAll(mockPrefs)

        assertNull(LayoutPreferences.load(mockPrefs, "wheel"))
        assertNull(LayoutPreferences.load(mockPrefs, "gyro"))
    }

    // Mode-specific storage independence
    @Test
    fun `wheel and gyro modes use separate storage keys`() {
        val wheelPositions = mapOf(
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f)
        )
        val gyroPositions = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.85f, 0.63f, 0.105f, 0.46f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", wheelPositions)
        LayoutPreferences.save(mockPrefs, "gyro", gyroPositions)

        val loadedWheel = LayoutPreferences.load(mockPrefs, "wheel")
        val loadedGyro = LayoutPreferences.load(mockPrefs, "gyro")

        assertNotNull(loadedWheel)
        assertNotNull(loadedGyro)
        assertTrue(loadedWheel!!.containsKey("WHEEL"))
        assertFalse(loadedWheel.containsKey("GYRO_ACCEL"))
        assertTrue(loadedGyro!!.containsKey("GYRO_ACCEL"))
        assertFalse(loadedGyro.containsKey("WHEEL"))
    }

    @Test
    fun `hasCustomLayout returns true when layout exists`() {
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f)
        )
        LayoutPreferences.save(mockPrefs, "wheel", positions)

        assertTrue(LayoutPreferences.hasCustomLayout(mockPrefs, "wheel"))
    }

    @Test
    fun `hasCustomLayout returns false when layout does not exist`() {
        assertFalse(LayoutPreferences.hasCustomLayout(mockPrefs, "wheel"))
    }

    @Test
    fun `round-trip serialization preserves all data`() {
        val original = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f),
            "WIPER" to ElementPosition("WIPER", 0.06f, 0.26f, 0.019f, 0.13f),
            "VIPER" to ElementPosition("VIPER", 0.06f, 0.42f, 0.019f, 0.13f),
            "HANDBRAKE" to ElementPosition("HANDBRAKE", 0.94f, 0.10f, 0.019f, 0.13f),
            "WHEEL" to ElementPosition("WHEEL", 0.20f, 0.65f, 0.278f, 0.50f),
            "ACCEL" to ElementPosition("ACCEL", 0.85f, 0.63f, 0.105f, 0.46f),
            "BRAKE" to ElementPosition("BRAKE", 0.70f, 0.63f, 0.105f, 0.46f)
        )

        LayoutPreferences.save(mockPrefs, "wheel", original)
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")

        assertNotNull(loaded)
        assertEquals(original.size, loaded!!.size)
        
        original.forEach { (id, expectedPos) ->
            val actualPos = loaded[id]
            assertNotNull("Missing element: $id", actualPos)
            assertEquals(expectedPos.id, actualPos!!.id)
            assertEquals(expectedPos.cx, actualPos.cx, 0.0001f)
            assertEquals(expectedPos.cy, actualPos.cy, 0.0001f)
            assertEquals(expectedPos.w, actualPos.w, 0.0001f)
            assertEquals(expectedPos.h, actualPos.h, 0.0001f)
        }
    }

    // AC 9.1: Brief instruction displayed on first edit mode entry
    @Test
    fun `hasSeenEditModeInstructions returns false by default`() {
        assertFalse(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
    }

    @Test
    fun `hasSeenEditModeInstructions returns true after marking as seen`() {
        assertFalse(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
        
        LayoutPreferences.markEditModeInstructionsSeen(mockPrefs)
        
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
    }

    @Test
    fun `markEditModeInstructionsSeen persists across checks`() {
        LayoutPreferences.markEditModeInstructionsSeen(mockPrefs)
        
        // Check multiple times to verify persistence
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
    }

    @Test
    fun `first-time flag is independent from layout data`() {
        // Set first-time flag
        LayoutPreferences.markEditModeInstructionsSeen(mockPrefs)
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
        
        // Save layout data
        val positions = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f)
        )
        LayoutPreferences.save(mockPrefs, "wheel", positions)
        
        // First-time flag should still be set
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
        
        // Clear layout data
        LayoutPreferences.clear(mockPrefs, "wheel")
        
        // First-time flag should still be set (not affected by clear)
        assertTrue(LayoutPreferences.hasSeenEditModeInstructions(mockPrefs))
    }
}

/**
 * Mock implementation of SharedPreferences for testing.
 */
class MockSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val editor = MockEditor()

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        data[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        data[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        data[key] as? Float ?: defValue

    override fun getString(key: String, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun edit(): SharedPreferences.Editor = editor

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    inner class MockEditor : SharedPreferences.Editor {
        private val edits = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clearCalled = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            edits[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removes.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearCalled = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearCalled) {
                data.clear()
                clearCalled = false
            }
            removes.forEach { data.remove(it) }
            removes.clear()
            data.putAll(edits)
            edits.clear()
        }
    }
}
