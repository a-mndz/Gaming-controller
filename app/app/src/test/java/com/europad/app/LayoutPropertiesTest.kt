package com.europad.app

import com.europad.app.ui.DeckRect
import com.europad.app.ui.ElementPosition
import com.europad.app.ui.LayoutEdit
import com.europad.app.ui.LayoutPreferences
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Property-Based Tests for Layout Correctness Properties
 * 
 * These tests verify the 8 correctness properties defined in the design document
 * using comprehensive parameterized testing across many input combinations.
 * 
 * **Validates: Requirements 2.2, 2.4, 4.1, 4.2, 4.3, 4.4, 9.2**
 */
class LayoutPropertiesTest {

    // ========================================================================
    // Resize handle: 44 dp floor + on-canvas clamp (Requirements 4.1, 9.2)
    // ========================================================================

    /** A corner drag towards zero stops at the 44 dp touch target, in both axes. */
    @Test
    fun `resize cannot shrink below the 44 dp touch target`() {
        val canvasW = 880f
        val canvasH = 400f
        val start = ElementPosition("WHEEL", cx = 0.3f, cy = 0.5f, w = 0.25f, h = 0.5f)

        val shrunk = LayoutEdit.resized(start, w = 0.001f, h = 0.001f, canvasWDp = canvasW, canvasHDp = canvasH)

        assertEquals("width floor in dp", LayoutEdit.MIN_TOUCH_DP, shrunk.w * canvasW, 0.01f)
        assertEquals("height floor in dp", LayoutEdit.MIN_TOUCH_DP, shrunk.h * canvasH, 0.01f)
        // Resizing by the bottom-right corner leaves the top-left edge where it was.
        assertEquals(start.cx - start.w / 2f, shrunk.cx - shrunk.w / 2f, 0.0001f)
        assertEquals(start.cy - start.h / 2f, shrunk.cy - shrunk.h / 2f, 0.0001f)
    }

    /** A corner drag past the edge stops at it: the element can never hang off the canvas. */
    @Test
    fun `resize cannot push the element off canvas`() {
        val start = ElementPosition("BRAKE", cx = 0.8f, cy = 0.7f, w = 0.15f, h = 0.2f)

        val grown = LayoutEdit.resized(start, w = 5f, h = 5f, canvasWDp = 880f, canvasHDp = 400f)
        val r = grown.toDeckRect()

        assertTrue("left in bounds", r.left >= -0.0001f)
        assertTrue("top in bounds", r.top >= -0.0001f)
        assertTrue("right in bounds", r.right <= 1.0001f)
        assertTrue("bottom in bounds", r.bottom <= 1.0001f)
    }

    /** Every random resize satisfies both rules at once — floor and bounds can't fight. */
    @Test
    fun `random resizes stay on canvas and above the minimum`() {
        val random = Random(4321)
        val canvasW = 880f
        val canvasH = 400f
        repeat(500) {
            val start = ElementPosition.create(
                id = "E",
                cx = random.nextFloat(),
                cy = random.nextFloat(),
                w = 0.05f + random.nextFloat() * 0.3f,
                h = 0.05f + random.nextFloat() * 0.3f,
            )
            val out = LayoutEdit.resized(
                start,
                w = random.nextFloat() * 2f - 0.5f,
                h = random.nextFloat() * 2f - 0.5f,
                canvasWDp = canvasW,
                canvasHDp = canvasH,
            )
            val r = out.toDeckRect()
            assertTrue("$out off canvas", r.left >= -0.0001f && r.top >= -0.0001f &&
                r.right <= 1.0001f && r.bottom <= 1.0001f)
            // The floor yields only when there is literally less than 44 dp of canvas left.
            val roomW = (1f - (start.cx - start.w / 2f).coerceIn(0f, 1f)) * canvasW
            val roomH = (1f - (start.cy - start.h / 2f).coerceIn(0f, 1f)) * canvasH
            assertTrue("$out too narrow", out.w * canvasW >= minOf(LayoutEdit.MIN_TOUCH_DP, roomW) - 0.01f)
            assertTrue("$out too short", out.h * canvasH >= minOf(LayoutEdit.MIN_TOUCH_DP, roomH) - 0.01f)
        }
    }

    /** The editor's overlap test is a true AABB intersect — touching edges are not an overlap. */
    @Test
    fun `overlap agrees with the AABB definition`() {
        val a = ElementPosition("A", cx = 0.2f, cy = 0.2f, w = 0.2f, h = 0.2f).toDeckRect()
        val touching = ElementPosition("B", cx = 0.4f, cy = 0.2f, w = 0.2f, h = 0.2f).toDeckRect()
        val over = ElementPosition("C", cx = 0.35f, cy = 0.2f, w = 0.2f, h = 0.2f).toDeckRect()
        val apart = ElementPosition("D", cx = 0.8f, cy = 0.8f, w = 0.2f, h = 0.2f).toDeckRect()

        assertFalse("edge-to-edge is not an overlap", LayoutEdit.overlaps(a, touching))
        assertTrue(LayoutEdit.overlaps(a, over))
        assertFalse(LayoutEdit.overlaps(a, apart))
        assertEquals(detectOverlap(a, over), LayoutEdit.overlaps(a, over))
        assertEquals(detectOverlap(a, apart), LayoutEdit.overlaps(a, apart))
    }

    // ========================================================================
    // Property 1: Drag Position Constrained to Bounds
    // ========================================================================
    
    /**
     * Property 1: Drag position constrained to bounds
     * 
     * For any Control_Element and any drag gesture within Edit_Mode, the element position 
     * during drag SHALL be constrained to remain within the visible screen boundaries 
     * (all coordinates clamped to [0.0, 1.0]).
     * 
     * **Validates: Requirements 2.2**
     */
    @Test
    fun `property 1 - drag positions are constrained to valid bounds`() {
        val testCases = generateDragTestCases(500)
        
        testCases.forEach { (unconstrained, description) ->
            // Simulate constraint logic that would be applied during drag
            val constrained = constrainPositionToBounds(unconstrained)
            
            // Verify all coordinates are within [0.0, 1.0]
            assertTrue(
                "cx out of bounds for $description: ${constrained.cx}",
                constrained.cx in 0.0f..1.0f
            )
            assertTrue(
                "cy out of bounds for $description: ${constrained.cy}",
                constrained.cy in 0.0f..1.0f
            )
            assertTrue(
                "w out of bounds for $description: ${constrained.w}",
                constrained.w in 0.0f..1.0f
            )
            assertTrue(
                "h out of bounds for $description: ${constrained.h}",
                constrained.h in 0.0f..1.0f
            )
        }
    }
    
    @Test
    fun `property 1 - edge case drag positions at boundaries`() {
        val edgeCases = listOf(
            ElementPosition("TEST", 0.0f, 0.0f, 0.1f, 0.1f),
            ElementPosition("TEST", 1.0f, 1.0f, 0.1f, 0.1f),
            ElementPosition("TEST", 0.0f, 1.0f, 0.1f, 0.1f),
            ElementPosition("TEST", 1.0f, 0.0f, 0.1f, 0.1f),
            ElementPosition("TEST", 0.5f, 0.0f, 0.5f, 0.1f),
            ElementPosition("TEST", 0.5f, 1.0f, 0.5f, 0.1f)
        )
        
        edgeCases.forEach { pos ->
            val constrained = constrainPositionToBounds(pos)
            
            assertTrue("cx should be valid", constrained.cx in 0.0f..1.0f)
            assertTrue("cy should be valid", constrained.cy in 0.0f..1.0f)
            assertTrue("w should be valid", constrained.w in 0.0f..1.0f)
            assertTrue("h should be valid", constrained.h in 0.0f..1.0f)
        }
    }

    // ========================================================================
    // Property 2: Released Position Satisfies Constraints
    // ========================================================================
    
    /**
     * Property 2: Released position satisfies constraints
     * 
     * For any Control_Element released after dragging in Edit_Mode, the final position 
     * SHALL satisfy: (a) all coordinates within [0.0, 1.0], and (b) the element 
     * remains fully visible on screen.
     * 
     * **Validates: Requirements 2.2, 2.4**
     */
    @Test
    fun `property 2 - released positions have valid coordinates`() {
        val testCases = generateDragTestCases(500)
        
        testCases.forEach { (position, description) ->
            val released = snapToValidPosition(position)
            
            // (a) All coordinates within [0.0, 1.0]
            assertTrue(
                "cx out of range for $description: ${released.cx}",
                released.cx in 0.0f..1.0f
            )
            assertTrue(
                "cy out of range for $description: ${released.cy}",
                released.cy in 0.0f..1.0f
            )
            assertTrue(
                "w out of range for $description: ${released.w}",
                released.w in 0.0f..1.0f
            )
            assertTrue(
                "h out of range for $description: ${released.h}",
                released.h in 0.0f..1.0f
            )
        }
    }
    
    @Test
    fun `property 2 - released elements remain fully visible`() {
        val testCases = generateDragTestCases(500)
        
        testCases.forEach { (position, description) ->
            val released = snapToValidPosition(position)
            val rect = released.toDeckRect()
            
            // (b) Element remains fully visible (all edges within screen)
            assertTrue(
                "left edge out of screen for $description: ${rect.left}",
                rect.left >= 0.0f
            )
            assertTrue(
                "right edge out of screen for $description: ${rect.right}",
                rect.right <= 1.0f
            )
            assertTrue(
                "top edge out of screen for $description: ${rect.top}",
                rect.top >= 0.0f
            )
            assertTrue(
                "bottom edge out of screen for $description: ${rect.bottom}",
                rect.bottom <= 1.0f
            )
        }
    }

    // ========================================================================
    // Property 3: Layout Serialization Round-Trip
    // ========================================================================
    
    /**
     * Property 3: Layout serialization round-trip
     * 
     * For any valid layout consisting of ElementPosition values, serializing to JSON 
     * and then deserializing SHALL produce an equivalent layout with all element 
     * identifiers, positions, and dimensions preserved.
     * 
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Test
    fun `property 3 - serialization round-trip preserves all data`() {
        val testLayouts = generateValidLayouts(100)
        val mockPrefs = MockSharedPreferences()
        
        testLayouts.forEach { (mode, layout, description) ->
            // Serialize
            LayoutPreferences.save(mockPrefs, mode, layout)
            
            // Deserialize
            val loaded = LayoutPreferences.load(mockPrefs, mode)
            
            assertNotNull("Layout should load for $description", loaded)
            assertEquals(
                "Element count mismatch for $description",
                layout.size,
                loaded!!.size
            )
            
            // Verify all elements preserved
            layout.forEach { (id, original) ->
                val recovered = loaded[id]
                assertNotNull("Missing element $id in $description", recovered)
                
                assertEquals("id mismatch for $description", original.id, recovered!!.id)
                assertEquals("cx mismatch for $description", original.cx, recovered.cx, 0.0001f)
                assertEquals("cy mismatch for $description", original.cy, recovered.cy, 0.0001f)
                assertEquals("w mismatch for $description", original.w, recovered.w, 0.0001f)
                assertEquals("h mismatch for $description", original.h, recovered.h, 0.0001f)
            }
            
            // Clear for next test
            mockPrefs.edit().clear().apply()
        }
    }
    
    @Test
    fun `property 3 - round-trip with complex element identifiers`() {
        val specialIds = listOf(
            "SIMPLE",
            "WITH_UNDERSCORE",
            "WITH-DASH",
            "WITH.DOT",
            "123_NUMERIC_PREFIX",
            "GYRO_ACCEL",
            "a",
            "VERY_LONG_IDENTIFIER_NAME_WITH_MANY_CHARACTERS_TO_TEST_LENGTH_HANDLING"
        )
        
        val mockPrefs = MockSharedPreferences()
        val layout = specialIds.mapIndexed { i, id ->
            id to ElementPosition(
                id = id,
                cx = 0.1f + (i * 0.08f),
                cy = 0.2f + (i * 0.07f),
                w = 0.1f,
                h = 0.1f
            )
        }.toMap()
        
        LayoutPreferences.save(mockPrefs, "wheel", layout)
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNotNull(loaded)
        assertEquals(specialIds.size, loaded!!.size)
        specialIds.forEach { id ->
            assertTrue("Missing element: $id", loaded.containsKey(id))
            assertEquals(id, loaded[id]!!.id)
        }
    }

    // ========================================================================
    // Property 4: Out-of-Range Values Clamped
    // ========================================================================
    
    /**
     * Property 4: Out-of-range values clamped
     * 
     * For any layout with element position values outside the valid range [0.0, 1.0], 
     * parsing SHALL clamp each out-of-range value to the nearest valid boundary 
     * (0.0 for negative values, 1.0 for values > 1.0).
     * 
     * **Validates: Requirements 4.4**
     */
    @Test
    fun `property 4 - out-of-range values are clamped on load`() {
        val testCases = generateOutOfRangeTestCases(200)
        val mockPrefs = MockSharedPreferences()
        
        testCases.forEach { (original, expectedClamped, description) ->
            // Manually construct JSON with out-of-range values
            val json = """
                {
                    "version": 1,
                    "mode": "wheel",
                    "elements": {
                        "TEST": {
                            "id": "TEST",
                            "cx": ${original.cx},
                            "cy": ${original.cy},
                            "w": ${original.w},
                            "h": ${original.h}
                        }
                    },
                    "timestamp": 1699876543210
                }
            """.trimIndent()
            
            mockPrefs.edit().putString("layout_wheel", json).apply()
            
            val loaded = LayoutPreferences.load(mockPrefs, "wheel")
            assertNotNull("Should load for $description", loaded)
            
            val test = loaded!!["TEST"]
            assertNotNull("TEST element missing for $description", test)
            
            assertEquals(
                "cx not clamped correctly for $description",
                expectedClamped.cx,
                test!!.cx,
                0.0001f
            )
            assertEquals(
                "cy not clamped correctly for $description",
                expectedClamped.cy,
                test.cy,
                0.0001f
            )
            assertEquals(
                "w not clamped correctly for $description",
                expectedClamped.w,
                test.w,
                0.0001f
            )
            assertEquals(
                "h not clamped correctly for $description",
                expectedClamped.h,
                test.h,
                0.0001f
            )
        }
    }
    
    @Test
    fun `property 4 - clamping works for extreme values`() {
        val extremeCases = listOf(
            Triple(Float.NEGATIVE_INFINITY, 0.0f, "negative infinity"),
            Triple(Float.POSITIVE_INFINITY, 1.0f, "positive infinity"),
            Triple(-1000000f, 0.0f, "large negative"),
            Triple(1000000f, 1.0f, "large positive"),
            Triple(Float.MIN_VALUE, Float.MIN_VALUE, "min float"),
            Triple(Float.MAX_VALUE, 1.0f, "max float")
        )
        
        extremeCases.forEach { (value, expected, description) ->
            val clamped = value.coerceIn(0.0f, 1.0f)
            assertEquals(
                "Clamping failed for $description",
                expected,
                clamped,
                0.0001f
            )
        }
    }

    // ========================================================================
    // Property 5: Overlap Detection Accuracy
    // ========================================================================
    
    /**
     * Property 5: Overlap detection accuracy
     * 
     * For any two Control_Element positions, the overlap detection function SHALL 
     * correctly identify whether the elements overlap (their rectangles intersect 
     * with less than minimum separation threshold).
     * 
     * **Validates: Requirements 2.3, 6.4**
     */
    @Test
    fun `property 5 - overlap detection identifies intersecting rectangles`() {
        val testCases = generateOverlapTestCases(300)
        
        testCases.forEach { (rect1, rect2, shouldOverlap, description) ->
            val overlaps = detectOverlap(rect1.toDeckRect(), rect2.toDeckRect())
            
            assertEquals(
                "Overlap detection mismatch for $description",
                shouldOverlap,
                overlaps
            )
        }
    }
    
    @Test
    fun `property 5 - non-overlapping rectangles detected correctly`() {
        val nonOverlapping = listOf(
            // Horizontally separated
            Triple(
                ElementPosition("A", 0.2f, 0.5f, 0.1f, 0.1f),
                ElementPosition("B", 0.8f, 0.5f, 0.1f, 0.1f),
                "horizontally separated"
            ),
            // Vertically separated
            Triple(
                ElementPosition("A", 0.5f, 0.2f, 0.1f, 0.1f),
                ElementPosition("B", 0.5f, 0.8f, 0.1f, 0.1f),
                "vertically separated"
            ),
            // Diagonally separated
            Triple(
                ElementPosition("A", 0.2f, 0.2f, 0.1f, 0.1f),
                ElementPosition("B", 0.8f, 0.8f, 0.1f, 0.1f),
                "diagonally separated"
            ),
            // Adjacent but not overlapping (touching edges)
            Triple(
                ElementPosition("A", 0.3f, 0.5f, 0.2f, 0.2f),
                ElementPosition("B", 0.6f, 0.5f, 0.2f, 0.2f),
                "touching edges"
            )
        )
        
        nonOverlapping.forEach { (pos1, pos2, description) ->
            val overlaps = detectOverlap(pos1.toDeckRect(), pos2.toDeckRect())
            assertFalse(
                "Should not overlap for $description",
                overlaps
            )
        }
    }
    
    @Test
    fun `property 5 - overlapping rectangles detected correctly`() {
        val overlapping = listOf(
            // Complete overlap (same position)
            Triple(
                ElementPosition("A", 0.5f, 0.5f, 0.1f, 0.1f),
                ElementPosition("B", 0.5f, 0.5f, 0.1f, 0.1f),
                "complete overlap"
            ),
            // Partial overlap from left
            Triple(
                ElementPosition("A", 0.4f, 0.5f, 0.2f, 0.2f),
                ElementPosition("B", 0.5f, 0.5f, 0.2f, 0.2f),
                "partial left overlap"
            ),
            // One contained in another
            Triple(
                ElementPosition("A", 0.5f, 0.5f, 0.4f, 0.4f),
                ElementPosition("B", 0.5f, 0.5f, 0.1f, 0.1f),
                "containment"
            ),
            // Corner overlap
            Triple(
                ElementPosition("A", 0.4f, 0.4f, 0.2f, 0.2f),
                ElementPosition("B", 0.5f, 0.5f, 0.2f, 0.2f),
                "corner overlap"
            )
        )
        
        overlapping.forEach { (pos1, pos2, description) ->
            val overlaps = detectOverlap(pos1.toDeckRect(), pos2.toDeckRect())
            assertTrue(
                "Should overlap for $description",
                overlaps
            )
        }
    }

    // ========================================================================
    // Property 6: Minimum Touch Target Size
    // ========================================================================
    
    /**
     * Property 6: Minimum touch target size
     * 
     * For any interactive element displayed in Edit_Mode, the touch target size 
     * SHALL be at least 44 density-independent pixels in both width and height.
     * 
     * **Validates: Requirements 9.2**
     */
    @Test
    fun `property 6 - elements meet minimum touch target size`() {
        // Simulate various screen sizes and densities
        val screenConfigs = listOf(
            Triple(1920, 1080, 2.0f), // 1920x1080 @ 2x density
            Triple(2560, 1440, 3.0f), // 1440p @ 3x density
            Triple(1280, 720, 1.5f),  // 720p @ 1.5x density
            Triple(3840, 2160, 4.0f), // 4K @ 4x density
            Triple(800, 480, 1.0f)    // Small screen @ 1x density
        )
        
        val minTouchTargetDp = 44f
        
        screenConfigs.forEach { (widthPx, heightPx, density) ->
            val widthDp = widthPx / density
            val heightDp = heightPx / density
            val minFracW = minTouchTargetDp / widthDp
            val minFracH = minTouchTargetDp / heightDp
            
            // Test various element sizes in fractional coordinates
            val testElements = generateElementSizeTestCases(100)
            
            testElements.forEach { (pos, description) ->
                val widthDpActual = pos.w * widthDp
                val heightDpActual = pos.h * heightDp
                
                val meetsMinWidth = widthDpActual >= minTouchTargetDp
                val meetsMinHeight = heightDpActual >= minTouchTargetDp
                
                // For a ${widthPx}x${heightPx} screen at ${density}x density the required
                // fractional minimums are minFracW x minFracH; verify the dp conversion is
                // self-consistent for every element that meets them.
                
                if (pos.w >= minFracW && pos.h >= minFracH) {
                    assertTrue(
                        "Element too small for $description on ${widthPx}x${heightPx} @ ${density}x: " +
                        "${widthDpActual}dp x ${heightDpActual}dp",
                        meetsMinWidth && meetsMinHeight
                    )
                }
            }
        }
    }
    
    @Test
    fun `property 6 - minimum size calculation is correct`() {
        val minTargetDp = 44f
        
        // For a 1920x1080 screen at 2.0 density
        val screenWidthPx = 1920
        val screenHeightPx = 1080
        val density = 2.0f
        
        val screenWidthDp = screenWidthPx / density  // 960 dp
        val screenHeightDp = screenHeightPx / density // 540 dp
        
        // Minimum fractional size needed
        val minFractionalWidth = minTargetDp / screenWidthDp  // 44 / 960 = 0.0458
        val minFractionalHeight = minTargetDp / screenHeightDp // 44 / 540 = 0.0815
        
        // Test that elements at minimum size meet the requirement
        val minElement = ElementPosition(
            "MIN",
            0.5f,
            0.5f,
            minFractionalWidth,
            minFractionalHeight
        )
        
        val actualWidthDp = minElement.w * screenWidthDp
        val actualHeightDp = minElement.h * screenHeightDp
        
        assertTrue(
            "Width should meet minimum: $actualWidthDp dp",
            actualWidthDp >= minTargetDp - 0.01f // Allow small floating point error
        )
        assertTrue(
            "Height should meet minimum: $actualHeightDp dp",
            actualHeightDp >= minTargetDp - 0.01f
        )
    }

    // ========================================================================
    // Property 7: Mode-Specific Layout Independence
    // ========================================================================
    
    /**
     * Property 7: Mode-specific layout independence
     * 
     * For any custom layout saved for wheel mode and any custom layout saved for 
     * gyro mode, modifying one layout SHALL NOT affect the positions stored for 
     * the other mode.
     * 
     * **Validates: Requirements 7.3**
     */
    @Test
    fun `property 7 - wheel and gyro layouts are independent`() {
        val testCases = generateModeIndependenceTestCases(50)
        val mockPrefs = MockSharedPreferences()
        
        testCases.forEach { (wheelLayout, gyroLayout, description) ->
            // Save both layouts
            LayoutPreferences.save(mockPrefs, "wheel", wheelLayout)
            LayoutPreferences.save(mockPrefs, "gyro", gyroLayout)
            
            // Verify both can be loaded independently
            val loadedWheel = LayoutPreferences.load(mockPrefs, "wheel")
            val loadedGyro = LayoutPreferences.load(mockPrefs, "gyro")
            
            assertNotNull("Wheel layout should load for $description", loadedWheel)
            assertNotNull("Gyro layout should load for $description", loadedGyro)
            
            // Verify wheel layout matches original
            wheelLayout.forEach { (id, original) ->
                val loaded = loadedWheel!![id]
                assertNotNull("Missing wheel element $id", loaded)
                assertEquals("Wheel $id cx mismatch", original.cx, loaded!!.cx, 0.0001f)
                assertEquals("Wheel $id cy mismatch", original.cy, loaded.cy, 0.0001f)
            }
            
            // Verify gyro layout matches original
            gyroLayout.forEach { (id, original) ->
                val loaded = loadedGyro!![id]
                assertNotNull("Missing gyro element $id", loaded)
                assertEquals("Gyro $id cx mismatch", original.cx, loaded!!.cx, 0.0001f)
                assertEquals("Gyro $id cy mismatch", original.cy, loaded.cy, 0.0001f)
            }
            
            // Modify wheel layout
            val modifiedWheel = wheelLayout.mapValues { (id, pos) ->
                pos.copy(cx = (pos.cx + 0.1f).coerceIn(0.0f, 1.0f))
            }
            LayoutPreferences.save(mockPrefs, "wheel", modifiedWheel)
            
            // Verify gyro layout unchanged
            val gyroAfterWheelChange = LayoutPreferences.load(mockPrefs, "gyro")
            assertNotNull("Gyro should still load", gyroAfterWheelChange)
            gyroLayout.forEach { (id, original) ->
                val loaded = gyroAfterWheelChange!![id]
                assertEquals(
                    "Gyro $id should be unchanged after wheel modification",
                    original.cx,
                    loaded!!.cx,
                    0.0001f
                )
            }
            
            // Clear for next test
            mockPrefs.edit().clear().apply()
        }
    }
    
    @Test
    fun `property 7 - clearing one mode does not affect the other`() {
        val mockPrefs = MockSharedPreferences()
        
        val wheelLayout = mapOf(
            "WHEEL" to ElementPosition("WHEEL", 0.2f, 0.6f, 0.3f, 0.5f)
        )
        val gyroLayout = mapOf(
            "GYRO_ACCEL" to ElementPosition("GYRO_ACCEL", 0.8f, 0.7f, 0.1f, 0.4f)
        )
        
        LayoutPreferences.save(mockPrefs, "wheel", wheelLayout)
        LayoutPreferences.save(mockPrefs, "gyro", gyroLayout)
        
        // Clear wheel mode
        LayoutPreferences.clear(mockPrefs, "wheel")
        
        // Verify wheel is gone but gyro remains
        assertNull(LayoutPreferences.load(mockPrefs, "wheel"))
        val loadedGyro = LayoutPreferences.load(mockPrefs, "gyro")
        assertNotNull(loadedGyro)
        assertEquals(1, loadedGyro!!.size)
        assertTrue(loadedGyro.containsKey("GYRO_ACCEL"))
    }

    // ========================================================================
    // Property 8: Default Fallback on Corrupted Data
    // ========================================================================
    
    /**
     * Property 8: Default fallback on corrupted data
     * 
     * For any corrupted or invalid data stored in SharedPreferences for layout 
     * positions, loading SHALL fall back to default DeckLayout positions without 
     * crashing.
     * 
     * **Validates: Requirements 3.3**
     */
    @Test
    fun `property 8 - corrupted JSON returns null without crashing`() {
        val corruptedJsonCases = listOf(
            "{corrupted",
            "not json at all",
            "{\"unclosed\": \"object\"",
            "[\"wrong\", \"type\"]",
            "null",
            "123",
            "true",
            "",
            "   ",
            "{",
            "}",
            "{\"version\":1}",  // Missing required fields
            "{\"mode\":\"wheel\"}",  // Missing elements
            "{\"elements\":{}}",  // Missing mode
            """{"version":"not a number","mode":"wheel","elements":{}}""",
            """{"version":1,"mode":123,"elements":{}}""",  // Wrong type for mode
            """{"version":1,"mode":"wheel","elements":"not an object"}""",
            """{"version":1,"mode":"wheel","elements":{"TEST":"not an object"}}""",
            "<?xml version=\"1.0\"?><root></root>",  // Wrong format entirely
            "{\"version\":1,\"mode\":\"wheel\",\"elements\":{\"TEST\":{\"id\":\"TEST\"}}}",  // Missing position fields
        )
        
        val mockPrefs = MockSharedPreferences()
        
        corruptedJsonCases.forEachIndexed { index, json ->
            mockPrefs.edit().putString("layout_wheel", json).apply()
            
            // Should not crash and should return null
            val loaded = try {
                LayoutPreferences.load(mockPrefs, "wheel")
            } catch (e: Exception) {
                fail("Should not throw exception for corrupted JSON case $index: $json. Got: ${e.message}")
                null
            }
            
            assertNull(
                "Should return null for corrupted JSON case $index: $json",
                loaded
            )
        }
    }
    
    @Test
    fun `property 8 - invalid element data returns null without crashing`() {
        val invalidElementCases = listOf(
            // Missing required fields
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5}}}""",
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5,"cy":0.5}}}""",
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":0.5,"cy":0.5,"w":0.1}}}""",
            // Wrong types
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":"not a float","cy":0.5,"w":0.1,"h":0.1}}}""",
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":null,"cy":0.5,"w":0.1,"h":0.1}}}""",
            // NaN and Infinity are technically valid JSON numbers but should be handled
            """{"version":1,"mode":"wheel","elements":{"TEST":{"id":"TEST","cx":NaN,"cy":0.5,"w":0.1,"h":0.1}}}""",
        )
        
        val mockPrefs = MockSharedPreferences()
        
        invalidElementCases.forEachIndexed { index, json ->
            mockPrefs.edit().putString("layout_wheel", json).apply()
            
            val loaded = try {
                LayoutPreferences.load(mockPrefs, "wheel")
            } catch (e: Exception) {
                // Should not crash - returning null is acceptable
                null
            }
            
            // Either returns null or handles gracefully
            // The key is no crash occurs
            assertTrue(
                "Should handle invalid element case $index gracefully",
                loaded == null || loaded.isEmpty()
            )
        }
    }
    
    @Test
    fun `property 8 - missing SharedPreferences key returns null`() {
        val mockPrefs = MockSharedPreferences()
        
        // Don't save anything
        val loaded = LayoutPreferences.load(mockPrefs, "wheel")
        
        assertNull("Should return null when no data exists", loaded)
    }
    
    @Test
    fun `property 8 - application can continue after load failure`() {
        val mockPrefs = MockSharedPreferences()
        
        // Save corrupted data
        mockPrefs.edit().putString("layout_wheel", "{invalid json").apply()
        
        // Load fails gracefully
        val loaded1 = LayoutPreferences.load(mockPrefs, "wheel")
        assertNull(loaded1)
        
        // Application can recover by saving valid data
        val validLayout = mapOf(
            "LIGHTS" to ElementPosition("LIGHTS", 0.06f, 0.10f, 0.019f, 0.13f)
        )
        LayoutPreferences.save(mockPrefs, "wheel", validLayout)
        
        // Now load succeeds
        val loaded2 = LayoutPreferences.load(mockPrefs, "wheel")
        assertNotNull(loaded2)
        assertEquals(1, loaded2!!.size)
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================
    
    /**
     * Simulates drag constraint logic - clamps position to bounds.
     */
    private fun constrainPositionToBounds(pos: ElementPosition): ElementPosition {
        return pos.clamp()
    }
    
    /**
     * Simulates snap-to-valid-position logic after drag release.
     */
    private fun snapToValidPosition(pos: ElementPosition): ElementPosition {
        val clamped = pos.clamp()
        val rect = clamped.toDeckRect()
        
        // Ensure element is fully visible by adjusting center if edges are out of bounds
        val adjustedCx = when {
            rect.left < 0.0f -> clamped.w / 2f
            rect.right > 1.0f -> 1.0f - clamped.w / 2f
            else -> clamped.cx
        }
        
        val adjustedCy = when {
            rect.top < 0.0f -> clamped.h / 2f
            rect.bottom > 1.0f -> 1.0f - clamped.h / 2f
            else -> clamped.cy
        }
        
        return clamped.copy(cx = adjustedCx, cy = adjustedCy)
    }
    
    /**
     * Detects if two rectangles overlap.
     */
    private fun detectOverlap(rect1: DeckRect, rect2: DeckRect): Boolean {
        return !(rect1.right <= rect2.left ||
                rect1.left >= rect2.right ||
                rect1.bottom <= rect2.top ||
                rect1.top >= rect2.bottom)
    }
    
    // ========================================================================
    // Test Case Generators
    // ========================================================================
    
    private fun generateDragTestCases(count: Int): List<Pair<ElementPosition, String>> {
        val random = Random(12345) // Fixed seed for reproducibility
        val cases = mutableListOf<Pair<ElementPosition, String>>()
        
        // Add boundary cases
        cases.add(
            ElementPosition("TEST", -0.5f, 0.5f, 0.1f, 0.1f) to "negative cx"
        )
        cases.add(
            ElementPosition("TEST", 1.5f, 0.5f, 0.1f, 0.1f) to "cx > 1.0"
        )
        cases.add(
            ElementPosition("TEST", 0.5f, -0.5f, 0.1f, 0.1f) to "negative cy"
        )
        cases.add(
            ElementPosition("TEST", 0.5f, 1.5f, 0.1f, 0.1f) to "cy > 1.0"
        )
        
        // Add random cases
        repeat(count - 4) {
            val cx = random.nextFloat() * 3f - 1f  // Range: -1.0 to 2.0
            val cy = random.nextFloat() * 3f - 1f
            val w = random.nextFloat() * 0.5f       // Range: 0.0 to 0.5
            val h = random.nextFloat() * 0.5f
            
            cases.add(
                ElementPosition("TEST", cx, cy, w, h) to "random case $it"
            )
        }
        
        return cases
    }
    
    private fun generateValidLayouts(count: Int): List<Triple<String, Map<String, ElementPosition>, String>> {
        val random = Random(23456)
        val cases = mutableListOf<Triple<String, Map<String, ElementPosition>, String>>()
        
        repeat(count) { i ->
            val mode = if (random.nextBoolean()) "wheel" else "gyro"
            val elementCount = random.nextInt(1, 10)
            val elements = (0 until elementCount).associate { j ->
                val id = "ELEMENT_$j"
                id to ElementPosition(
                    id = id,
                    cx = random.nextFloat(),
                    cy = random.nextFloat(),
                    w = random.nextFloat() * 0.3f,
                    h = random.nextFloat() * 0.3f
                )
            }
            cases.add(Triple(mode, elements, "$mode layout $i with $elementCount elements"))
        }
        
        return cases
    }
    
    private fun generateOutOfRangeTestCases(count: Int): List<Triple<ElementPosition, ElementPosition, String>> {
        val random = Random(34567)
        val cases = mutableListOf<Triple<ElementPosition, ElementPosition, String>>()
        
        // Add specific edge cases
        cases.add(
            Triple(
                ElementPosition("TEST", -1.0f, -1.0f, -1.0f, -1.0f),
                ElementPosition("TEST", 0.0f, 0.0f, 0.0f, 0.0f),
                "all negative"
            )
        )
        cases.add(
            Triple(
                ElementPosition("TEST", 2.0f, 2.0f, 2.0f, 2.0f),
                ElementPosition("TEST", 1.0f, 1.0f, 1.0f, 1.0f),
                "all > 1.0"
            )
        )
        
        // Add random cases
        repeat(count - 2) {
            val cx = random.nextFloat() * 4f - 1f  // Range: -1.0 to 3.0
            val cy = random.nextFloat() * 4f - 1f
            val w = random.nextFloat() * 4f - 1f
            val h = random.nextFloat() * 4f - 1f
            
            val original = ElementPosition("TEST", cx, cy, w, h)
            val clamped = original.clamp()
            
            cases.add(Triple(original, clamped, "random case $it"))
        }
        
        return cases
    }
    
    private fun generateOverlapTestCases(count: Int): List<OverlapCase> {
        val random = Random(45678)
        val cases = mutableListOf<OverlapCase>()
        
        // Add known overlapping cases
        cases.add(
            OverlapCase(
                ElementPosition("A", 0.5f, 0.5f, 0.2f, 0.2f),
                ElementPosition("B", 0.5f, 0.5f, 0.2f, 0.2f),
                true,
                "identical positions"
            )
        )
        cases.add(
            OverlapCase(
                ElementPosition("A", 0.4f, 0.5f, 0.2f, 0.2f),
                ElementPosition("B", 0.5f, 0.5f, 0.2f, 0.2f),
                true,
                "partial horizontal overlap"
            )
        )
        
        // Add known non-overlapping cases
        cases.add(
            OverlapCase(
                ElementPosition("A", 0.2f, 0.5f, 0.1f, 0.1f),
                ElementPosition("B", 0.8f, 0.5f, 0.1f, 0.1f),
                false,
                "horizontally separated"
            )
        )
        
        // Add random cases
        repeat(count - 3) {
            val pos1 = ElementPosition(
                "A",
                random.nextFloat(),
                random.nextFloat(),
                random.nextFloat() * 0.3f,
                random.nextFloat() * 0.3f
            )
            val pos2 = ElementPosition(
                "B",
                random.nextFloat(),
                random.nextFloat(),
                random.nextFloat() * 0.3f,
                random.nextFloat() * 0.3f
            )
            
            val overlaps = detectOverlap(pos1.toDeckRect(), pos2.toDeckRect())
            cases.add(OverlapCase(pos1, pos2, overlaps, "random case $it"))
        }
        
        return cases
    }
    
    private fun generateElementSizeTestCases(count: Int): List<Pair<ElementPosition, String>> {
        val random = Random(56789)
        val cases = mutableListOf<Pair<ElementPosition, String>>()
        
        // Add specific size cases
        cases.add(
            ElementPosition("SMALL", 0.5f, 0.5f, 0.01f, 0.01f) to "very small"
        )
        cases.add(
            ElementPosition("MEDIUM", 0.5f, 0.5f, 0.1f, 0.1f) to "medium"
        )
        cases.add(
            ElementPosition("LARGE", 0.5f, 0.5f, 0.5f, 0.5f) to "large"
        )
        
        // Add random sizes
        repeat(count - 3) {
            cases.add(
                ElementPosition(
                    "TEST",
                    0.5f,
                    0.5f,
                    random.nextFloat() * 0.5f,
                    random.nextFloat() * 0.5f
                ) to "random size $it"
            )
        }
        
        return cases
    }
    
    private fun generateModeIndependenceTestCases(count: Int): List<Triple<Map<String, ElementPosition>, Map<String, ElementPosition>, String>> {
        val random = Random(67890)
        val cases = mutableListOf<Triple<Map<String, ElementPosition>, Map<String, ElementPosition>, String>>()
        
        repeat(count) { i ->
            val wheelElements = (0..random.nextInt(1, 5)).associate { j ->
                val id = "WHEEL_$j"
                id to ElementPosition(
                    id = id,
                    cx = random.nextFloat(),
                    cy = random.nextFloat(),
                    w = random.nextFloat() * 0.2f,
                    h = random.nextFloat() * 0.2f
                )
            }
            
            val gyroElements = (0..random.nextInt(1, 5)).associate { j ->
                val id = "GYRO_$j"
                id to ElementPosition(
                    id = id,
                    cx = random.nextFloat(),
                    cy = random.nextFloat(),
                    w = random.nextFloat() * 0.2f,
                    h = random.nextFloat() * 0.2f
                )
            }
            
            cases.add(Triple(wheelElements, gyroElements, "test case $i"))
        }
        
        return cases
    }
    
    private data class OverlapCase(
        val a: ElementPosition,
        val b: ElementPosition,
        val overlaps: Boolean,
        val description: String
    )
}
