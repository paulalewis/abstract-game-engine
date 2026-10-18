package com.castlefrog.agl.domains.hex

import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HexStateTest {

    private val emptyState: HexState = HexState(boardSize = 5)
    private val hexState: HexState = HexState(boardSize = 5)

    @Before
    fun setup() {
        hexState.setLocation(2, 1, HexState.LOCATION_BLACK)
        hexState.setLocation(1, 1, HexState.LOCATION_BLACK)
        hexState.setLocation(1, 0, HexState.LOCATION_BLACK)
        hexState.setLocation(3, 1, HexState.LOCATION_WHITE)
        hexState.setLocation(3, 3, HexState.LOCATION_WHITE)
    }

    @Test
    fun testCopy() {
        assertEquals(hexState.copy(), hexState)
    }

    @Test
    fun testCopyIsolation() {
        val original = hexState.copy()
        val copy = original.copy()
        original.setLocation(0, 0, HexState.LOCATION_BLACK)
        assertNotEquals(original, copy)
        assertEquals(HexState.LOCATION_EMPTY, copy.getLocation(0, 0))

        copy.setLocation(4, 4, HexState.LOCATION_WHITE)
        assertEquals(HexState.LOCATION_EMPTY, original.getLocation(4, 4))
    }

    @Test
    fun testEquality() {
        val otherHexState = emptyState.copy()
        otherHexState.setLocation(2, 1, HexState.LOCATION_BLACK)
        otherHexState.setLocation(1, 1, HexState.LOCATION_BLACK)
        otherHexState.setLocation(1, 0, HexState.LOCATION_BLACK)
        otherHexState.setLocation(3, 1, HexState.LOCATION_WHITE)
        otherHexState.setLocation(3, 3, HexState.LOCATION_WHITE)
        assertEquals(hexState, otherHexState)
    }

    @Test
    fun copyHasEqualHashCode() {
        assertEquals(hexState.hashCode(), hexState.copy().hashCode())
    }

    @Test
    fun statesBuiltBySameMovesHaveEqualHashCodes() {
        val simulator = HexSimulator(boardSize = 5)
        val moves = listOf(HexAction(2, 2), HexAction(1, 3), HexAction(0, 4))
        fun play(): HexState = moves.foldIndexed(simulator.initialState) { i, state, move ->
            simulator.stateTransition(state, if (i % 2 == 0) listOf(move, null) else listOf(null, move))
        }
        val first = play()
        val second = play()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equalStatesWorkInHashSetAndHashMap() {
        val copy = hexState.copy()
        assertTrue(copy in hashSetOf(hexState))
        assertEquals(1, hashSetOf(hexState, copy).size)
        assertEquals("value", hashMapOf(hexState to "value")[copy])
    }

    @Test
    fun testEqualityNotEqual() {
        val otherHexState = HexState(boardSize = 4)
        otherHexState.setLocation(2, 1, HexState.LOCATION_BLACK)
        otherHexState.setLocation(1, 1, HexState.LOCATION_BLACK)
        otherHexState.setLocation(1, 0, HexState.LOCATION_BLACK)
        otherHexState.setLocation(3, 1, HexState.LOCATION_WHITE)
        otherHexState.setLocation(3, 3, HexState.LOCATION_WHITE)
        assertNotEquals(hexState, otherHexState)
    }

    @Test
    fun testSetLocationOutOfBounds() {
        assertThrows(
            IllegalArgumentException::class.java
        ) { hexState.copy().setLocation(-1, -1, HexState.LOCATION_EMPTY) }
    }

    @Test
    fun testSetLocationOutOfBounds2() {
        assertThrows(
            IllegalArgumentException::class.java
        ) { hexState.copy().setLocation(hexState.boardSize, hexState.boardSize, HexState.LOCATION_EMPTY) }
    }

    @Test
    fun testSetLocation() {
        hexState.copy().setLocation(hexState.boardSize - 1, hexState.boardSize - 1, HexState.LOCATION_EMPTY)
    }

    @Test
    fun testGetLocationOutOfBounds() {
        assertThrows(IllegalArgumentException::class.java) { hexState.getLocation(-1, -1) }
    }

    @Test
    fun testGetLocationOutOfBounds2() {
        assertThrows(
            IllegalArgumentException::class.java
        ) { hexState.getLocation(hexState.boardSize, hexState.boardSize) }
    }

    @Test
    fun testGetLocation() {
        assertEquals(HexState.LOCATION_EMPTY, hexState.getLocation(
                hexState.boardSize - 1,
                hexState.boardSize - 1
            ))
    }

    @Test
    fun testLocationIsEmpty() {
        assertTrue(hexState.isLocationEmpty(0, 1))
    }

    @Test
    fun testLocationIsNotEmpty() {
        assertFalse(hexState.isLocationEmpty(1, 1))
    }

    @Test
    fun cornersRoundTripEmptyBlackWhite() {
        val corners = listOf(0 to 0, 4 to 0, 0 to 4, 4 to 4)
        val state = HexState(boardSize = 5)
        for ((x, y) in corners) {
            assertTrue(state.isLocationEmpty(x, y))
            assertEquals(HexState.LOCATION_EMPTY, state.getLocation(x, y))

            state.setLocation(x, y, HexState.LOCATION_BLACK)
            assertFalse(state.isLocationEmpty(x, y))
            assertEquals(HexState.LOCATION_BLACK, state.getLocation(x, y))

            state.setLocation(x, y, HexState.LOCATION_WHITE)
            assertFalse(state.isLocationEmpty(x, y))
            assertEquals(HexState.LOCATION_WHITE, state.getLocation(x, y))

            state.setLocation(x, y, HexState.LOCATION_EMPTY)
            assertTrue(state.isLocationEmpty(x, y))
            assertEquals(HexState.LOCATION_EMPTY, state.getLocation(x, y))
        }
        assertEquals(0, state.nPieces)
    }

    @Test
    fun adjacentBytesDoNotAlias() {
        val state = HexState(boardSize = 5)
        // On a 5×5 board, (2,1) is bit 7 and (3,1) is bit 8 — last bit of one storage
        // byte and first bit of the next.
        state.setLocation(2, 1, HexState.LOCATION_BLACK)
        state.setLocation(3, 1, HexState.LOCATION_WHITE)

        assertEquals(HexState.LOCATION_BLACK, state.getLocation(2, 1))
        assertEquals(HexState.LOCATION_WHITE, state.getLocation(3, 1))
        assertTrue(state.isLocationEmpty(1, 1))
        assertTrue(state.isLocationEmpty(4, 1))
        assertEquals(2, state.nPieces)
    }

    @Test
    fun testIsLocationEmptyOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) { hexState.isLocationEmpty(-1, -1) }
    }

    @Test
    fun testIsLocationEmptyOutOfRange2() {
        assertThrows(
            IllegalArgumentException::class.java
        ) { hexState.isLocationEmpty(hexState.boardSize, hexState.boardSize) }
    }

    @Test
    fun testGetNPiecesEmpty() {
        assertEquals(0, emptyState.nPieces)
    }

    @Test
    fun testGetNPieces() {
        assertEquals(5, hexState.nPieces)
    }

    @Test
    fun testToString() {
        assertEquals("""
                |- - - - -
                | - - - O -
                |  - - - - -
                |   - X X O -
                |    - X - - -
                """.trimMargin(), hexState.toString())
    }
}