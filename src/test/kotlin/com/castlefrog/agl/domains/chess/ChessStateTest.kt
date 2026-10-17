package com.castlefrog.agl.domains.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ChessStateTest {

    @Test
    fun initialPositionHasKings() {
        val state = ChessState()
        assertEquals(ChessState.KING.toByte(), state.get(4, 0))
        assertEquals((-ChessState.KING).toByte(), state.get(4, 7))
    }

    @Test
    fun copyIsolation() {
        val state = ChessState()
        val copy = state.copy()
        assertEquals(state, copy)
        assertNotSame(state.board, copy.board)
        copy.set(0, 1, ChessState.EMPTY)
        assertNotEquals(state, copy)
        assertEquals(ChessState.PAWN.toByte(), state.get(0, 1))
    }

    @Test
    fun toStringShowsRanks() {
        val state = ChessState()
        val lines = state.toString().lines()
        assertEquals(8, lines.size)
        assertEquals("r n b q k b n r", lines[0])
        assertEquals("R N B Q K B N R", lines[7])
    }
    @Test
    fun copyEqualsAndHashCodeIncludePositionHistory() {
        val state = ChessState()
        state.positionHistory.add("a")
        val copy = state.copy()
        assertEquals(state, copy)
        assertEquals(state.hashCode(), copy.hashCode())
        assertNotSame(state.positionHistory, copy.positionHistory)
        copy.positionHistory.add("b")
        assertEquals(listOf("a"), state.positionHistory)
        assertNotEquals(state, copy)
        assertNotEquals(state.hashCode(), copy.hashCode())
    }
}
