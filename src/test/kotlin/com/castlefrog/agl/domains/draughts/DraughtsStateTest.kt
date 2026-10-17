package com.castlefrog.agl.domains.draughts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DraughtsStateTest {

    @Test
    fun initialBoardHasTwentyMenEach() {
        val state = DraughtsState()
        var white = 0
        var black = 0
        for (x in 0 until DraughtsState.SIZE) {
            for (y in 0 until DraughtsState.SIZE) {
                when (state.get(x, y)) {
                    DraughtsState.WHITE_MAN -> white++
                    DraughtsState.BLACK_MAN -> black++
                }
            }
        }
        assertEquals(20, white)
        assertEquals(20, black)
    }

    @Test
    fun piecesOnlyOnDarkSquares() {
        val state = DraughtsState()
        for (x in 0 until DraughtsState.SIZE) {
            for (y in 0 until DraughtsState.SIZE) {
                if (state.get(x, y) != DraughtsState.EMPTY) {
                    assertTrue(DraughtsState.isDarkSquare(x, y))
                }
            }
        }
    }

    @Test
    fun copyIsolation() {
        val state = DraughtsState()
        val copy = state.copy()
        assertEquals(state, copy)
        assertNotSame(state.board, copy.board)
        copy.set(0, 0, DraughtsState.EMPTY)
        assertNotEquals(state, copy)
    }

    @Test
    fun pendingCapturesCopiedIndependently() {
        val state = DraughtsState()
        state.pendingCaptures.add(DraughtsState.captureKey(2, 2))
        val copy = state.copy()
        assertEquals(state, copy)
        copy.pendingCaptures.add(DraughtsState.captureKey(4, 4))
        assertNotEquals(state, copy)
        assertEquals(1, state.pendingCaptures.size)
    }

    @Test
    fun drawFieldsCopiedAndCompared() {
        val state = DraughtsState()
        state.positionHistory.add(state.positionKey())
        state.kingMoveCount = 7
        state.endgameGroup = DraughtsState.ENDGAME_SIXTEEN
        state.endgameMoveCount = 3
        val copy = state.copy()
        assertEquals(state, copy)
        assertEquals(state.hashCode(), copy.hashCode())
        assertNotSame(state.positionHistory, copy.positionHistory)

        copy.positionHistory.add("other")
        assertNotEquals(state, copy)
        assertEquals(1, state.positionHistory.size)

        for (change in listOf<(DraughtsState) -> Unit>(
            { it.kingMoveCount++ },
            { it.endgameGroup = DraughtsState.ENDGAME_FIVE },
            { it.endgameMoveCount++ }
        )) {
            val changed = state.copy()
            change(changed)
            assertNotEquals(state, changed)
            assertNotEquals(state.hashCode(), changed.hashCode())
        }
    }

    @Test
    fun positionKeyDependsOnPlayerToMove() {
        val state = DraughtsState()
        val other = state.copy()
        other.agentTurn = DraughtsState.TURN_BLACK
        assertNotEquals(state.positionKey(), other.positionKey())
    }
}
