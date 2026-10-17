package com.castlefrog.agl.domains.draughts

import com.castlefrog.agl.IllegalActionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DraughtsSimulatorTest {

    @Test
    fun initialLegalMovesAreWhitesNineQuietMoves() {
        val simulator = DraughtsSimulator()
        val legal = simulator.calculateLegalActions(simulator.initialState)
        assertEquals(9, legal[0].size)
        assertTrue(legal[1].isEmpty())
        assertTrue(legal[0].contains(DraughtsAction(3, 3, 4, 4)))
    }

    @Test
    fun quietMoveAdvancesPiece() {
        val simulator = DraughtsSimulator()
        val state = simulator.initialState
        val action = DraughtsAction(3, 3, 4, 4)
        assertTrue(simulator.calculateLegalActions(state)[0].contains(action))
        val next = simulator.stateTransition(state, listOf(action, null))
        assertEquals(DraughtsState.EMPTY, next.get(3, 3))
        assertEquals(DraughtsState.WHITE_MAN, next.get(4, 4))
        assertEquals(DraughtsState.TURN_BLACK, next.agentTurn)
    }

    @Test
    fun captureIsMandatory() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(3, 3, DraughtsState.WHITE_MAN)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(3, 3, 5, 5)))
        assertFalse(legal.any { kotlin.math.abs(it.toX - it.fromX) == 1 })
    }

    @Test
    fun captureRemovesPieceAtEndOfSequence() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(3, 3, DraughtsState.WHITE_MAN)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val next = simulator.stateTransition(state, listOf(DraughtsAction(3, 3, 5, 5), null))
        assertEquals(DraughtsState.EMPTY, next.get(4, 4))
        assertEquals(DraughtsState.WHITE_MAN, next.get(5, 5))
        assertTrue(next.pendingCaptures.isEmpty())
        assertEquals(DraughtsState.TURN_BLACK, next.agentTurn)
    }

    @Test
    fun manCanCaptureBackward() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(5, 5, DraughtsState.WHITE_MAN)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(5, 5, 3, 3)))
    }

    @Test
    fun majorityCaptureIsForced() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        // Single capture to the left; double capture to the right.
        state.set(5, 3, DraughtsState.WHITE_MAN)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.set(6, 4, DraughtsState.BLACK_MAN)
        state.set(8, 6, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(5, 3, 7, 5)))
        assertFalse(legal.contains(DraughtsAction(5, 3, 3, 5)))
    }

    @Test
    fun majorityCaptureCountsCycleThatLandsOnOrigin() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        // 4-jump loop: last landing is the origin. Without vacating origin the
        // engine counts this as 3 and incorrectly ties a real 3-capture.
        state.set(2, 4, DraughtsState.WHITE_MAN)
        state.set(3, 5, DraughtsState.BLACK_MAN)
        state.set(5, 5, DraughtsState.BLACK_MAN)
        state.set(5, 3, DraughtsState.BLACK_MAN)
        state.set(3, 3, DraughtsState.BLACK_MAN)
        // Disjoint 3-capture.
        state.set(9, 3, DraughtsState.WHITE_MAN)
        state.set(8, 4, DraughtsState.BLACK_MAN)
        state.set(6, 6, DraughtsState.BLACK_MAN)
        state.set(4, 8, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(2, 4, 4, 6)))
        assertTrue(legal.contains(DraughtsAction(2, 4, 4, 2)))
        assertFalse(legal.contains(DraughtsAction(9, 3, 7, 5)))
    }

    @Test
    fun kingWindmillCycleThatReturnsToOriginIsForced() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        // 4-jump windmill around (2,4). Extra men at (6,8) and (8,6) give the
        // (4,2) branch length 4 without returning home. The (4,6) branch is a
        // true return-to-origin cycle and is only length 4 if origin is vacated.
        state.set(2, 4, DraughtsState.WHITE_KING)
        state.set(3, 5, DraughtsState.BLACK_MAN)
        state.set(5, 5, DraughtsState.BLACK_MAN)
        state.set(5, 3, DraughtsState.BLACK_MAN)
        state.set(3, 3, DraughtsState.BLACK_MAN)
        state.set(6, 8, DraughtsState.BLACK_MAN)
        state.set(8, 6, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(2, 4, 4, 6)))
        assertTrue(legal.contains(DraughtsAction(2, 4, 4, 2)))
        assertFalse(legal.contains(DraughtsAction(2, 4, 5, 7)))
        assertFalse(legal.contains(DraughtsAction(2, 4, 6, 8)))
    }

    @Test
    fun capturedPieceStaysDuringMultiJump() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(1, 1, DraughtsState.WHITE_MAN)
        state.set(2, 2, DraughtsState.BLACK_MAN)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val mid = simulator.stateTransition(state, listOf(DraughtsAction(1, 1, 3, 3), null))
        assertEquals(DraughtsState.BLACK_MAN, mid.get(2, 2))
        assertEquals(DraughtsState.TURN_WHITE, mid.agentTurn)
        assertEquals(3, mid.mustContinueFromX)
        assertEquals(3, mid.mustContinueFromY)
        assertTrue(mid.isPendingCapture(2, 2))

        val end = simulator.stateTransition(mid, listOf(DraughtsAction(3, 3, 5, 5), null))
        assertEquals(DraughtsState.EMPTY, end.get(2, 2))
        assertEquals(DraughtsState.EMPTY, end.get(4, 4))
        assertEquals(DraughtsState.WHITE_MAN, end.get(5, 5))
        assertEquals(DraughtsState.TURN_BLACK, end.agentTurn)
        assertTrue(end.pendingCaptures.isEmpty())
    }

    @Test
    fun flyingKingSlidesAnyDistance() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 0, DraughtsState.WHITE_KING)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(0, 0, 1, 1)))
        assertTrue(legal.contains(DraughtsAction(0, 0, 5, 5)))
        assertTrue(legal.contains(DraughtsAction(0, 0, 9, 9)))
    }

    @Test
    fun flyingKingCapturesAtDistanceAndChoosesLanding() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 0, DraughtsState.WHITE_KING)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(DraughtsAction(0, 0, 5, 5)))
        assertTrue(legal.contains(DraughtsAction(0, 0, 7, 7)))
        assertTrue(legal.contains(DraughtsAction(0, 0, 9, 9)))
        assertFalse(legal.contains(DraughtsAction(0, 0, 2, 2)))

        val next = simulator.stateTransition(state, listOf(DraughtsAction(0, 0, 7, 7), null))
        assertEquals(DraughtsState.EMPTY, next.get(4, 4))
        assertEquals(DraughtsState.WHITE_KING, next.get(7, 7))
    }

    @Test
    fun flyingKingMustLandWhereLongerCaptureContinues() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 2, DraughtsState.WHITE_KING)
        state.set(2, 4, DraughtsState.BLACK_MAN)
        state.set(5, 7, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val legal = simulator.calculateLegalActions(state)[0]
        // Landing on (3,5) or (4,6) can continue over (5,7); (6,8) and (7,9) cannot.
        assertTrue(legal.contains(DraughtsAction(0, 2, 3, 5)))
        assertTrue(legal.contains(DraughtsAction(0, 2, 4, 6)))
        assertFalse(legal.contains(DraughtsAction(0, 2, 6, 8)))
        assertFalse(legal.contains(DraughtsAction(0, 2, 7, 9)))
    }

    @Test
    fun promotionOnLastRank() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 8, DraughtsState.WHITE_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val next = simulator.stateTransition(state, listOf(DraughtsAction(0, 8, 1, 9), null))
        assertEquals(DraughtsState.WHITE_KING, next.get(1, 9))
    }

    @Test
    fun manDoesNotPromoteMidCapture() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(5, 7, DraughtsState.WHITE_MAN)
        state.set(6, 8, DraughtsState.BLACK_MAN)
        state.set(8, 8, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val mid = simulator.stateTransition(state, listOf(DraughtsAction(5, 7, 7, 9), null))
        assertEquals(DraughtsState.WHITE_MAN, mid.get(7, 9))
        assertEquals(DraughtsState.TURN_WHITE, mid.agentTurn)
        assertTrue(mid.mustContinueFromX == 7 && mid.mustContinueFromY == 9)

        val end = simulator.stateTransition(mid, listOf(DraughtsAction(7, 9, 9, 7), null))
        assertEquals(DraughtsState.WHITE_MAN, end.get(9, 7))
        assertEquals(DraughtsState.TURN_BLACK, end.agentTurn)
    }

    @Test
    fun manPromotesWhenCaptureEndsOnLastRank() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(5, 7, DraughtsState.WHITE_MAN)
        state.set(6, 8, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val next = simulator.stateTransition(state, listOf(DraughtsAction(5, 7, 7, 9), null))
        assertEquals(DraughtsState.WHITE_KING, next.get(7, 9))
        assertEquals(DraughtsState.TURN_BLACK, next.agentTurn)
    }

    @Test
    fun noMovesMeansLoss() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(1, 1, DraughtsState.BLACK_MAN)
        state.agentTurn = DraughtsState.TURN_WHITE
        val rewards = simulator.calculateRewards(state)
        assertEquals(-1, rewards[0])
        assertEquals(1, rewards[1])
    }

    @Test
    fun emptyActionsThrows() {
        val simulator = DraughtsSimulator()
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, emptyList())
        }
    }

    @Test
    fun illegalMoveThrows() {
        val simulator = DraughtsSimulator()
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(
                simulator.initialState,
                listOf(DraughtsAction(0, 0, 1, 1), null)
            )
        }
    }

    // Draw rules. The white king T walks the triangle (2,2) -> (4,4) -> (3,3) -> (2,2)
    // while the black king shuffles (9,1) <-> (8,2). Neither can capture, and the
    // two cycles only repeat a position (with the same player to move) after 12 plies.

    @Test
    fun threefoldRepetitionDrawsOnThirdOccurrence() {
        val simulator = DraughtsSimulator()
        var state = kingsWithMen()
        // (2,2) <-> (3,3) and (9,1) <-> (8,2) return to the start every 4 plies.
        val cycle = listOf(
            DraughtsAction(2, 2, 3, 3),
            DraughtsAction(9, 1, 8, 2),
            DraughtsAction(3, 3, 2, 2),
            DraughtsAction(8, 2, 9, 1)
        )
        state = play(simulator, state, cycle)
        assertFalse("second occurrence is not a draw", isOver(simulator, state))
        state = play(simulator, state, cycle.dropLast(1))
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, cycle.takeLast(1))
        assertTrue("third occurrence is a draw", isOver(simulator, state))
        assertNeutral(simulator.calculateRewards(state))
    }

    @Test
    fun repetitionNeedsTheSamePlayerToMove() {
        val simulator = DraughtsSimulator()
        val start = kingsWithMen()
        // The board of the start position occurs again after 5 plies with black to move,
        // then after 12 plies with white to move: three times, but only twice with white to move.
        var state = start
        for (ply in 0 until 12) {
            state = play(simulator, state, listOf(triangleAction(state, ply)))
            if (ply == 4) {
                assertTrue(state.board.indices.all { state.board[it].contentEquals(start.board[it]) })
                assertEquals(DraughtsState.TURN_BLACK, state.agentTurn)
            }
            assertFalse("ply $ply", isOver(simulator, state))
        }
        assertEquals(start.positionKey(), state.positionKey())
        assertTrue(simulator.calculateLegalActions(state)[0].isNotEmpty())
    }

    @Test
    fun manMoveResetsRepetitionAndKingMoveCount() {
        val simulator = DraughtsSimulator()
        var state = kingsWithMen()
        state = play(simulator, state, listOf(DraughtsAction(2, 2, 3, 3), DraughtsAction(9, 1, 8, 2)))
        assertEquals(2, state.positionHistory.size)
        assertEquals(2, state.kingMoveCount)
        state = play(simulator, state, listOf(DraughtsAction(0, 2, 1, 3)))
        assertTrue(state.positionHistory.isEmpty())
        assertEquals(0, state.kingMoveCount)
    }

    @Test
    fun captureResetsRepetitionAndKingMoveCount() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 0, DraughtsState.WHITE_KING)
        state.set(4, 4, DraughtsState.BLACK_MAN)
        state.set(9, 1, DraughtsState.BLACK_KING)
        state.positionHistory.add(state.positionKey())
        state.kingMoveCount = 49
        val next = simulator.stateTransition(state, listOf(DraughtsAction(0, 0, 5, 5), null))
        assertTrue(next.positionHistory.isEmpty())
        assertEquals(0, next.kingMoveCount)
    }

    @Test
    fun twentyFiveKingMovesEachDraw() {
        val simulator = DraughtsSimulator()
        var state = kingsWithMen()
        state = play(simulator, state, listOf(DraughtsAction(2, 2, 4, 4)))
        assertEquals(1, state.kingMoveCount)
        // 24 moves each, then one more each.
        state.kingMoveCount = 48
        state.positionHistory.clear()
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, listOf(DraughtsAction(9, 1, 8, 2)))
        assertEquals(49, state.kingMoveCount)
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, listOf(DraughtsAction(4, 4, 3, 3)))
        assertEquals(50, state.kingMoveCount)
        assertTrue(isOver(simulator, state))
        assertNeutral(simulator.calculateRewards(state))
    }

    @Test
    fun threeKingsAgainstOneKingDrawsAfterSixteenMovesEach() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(2, 2, DraughtsState.WHITE_KING)
        state.set(0, 8, DraughtsState.WHITE_KING)
        state.set(0, 4, DraughtsState.WHITE_KING)
        state.set(1, 5, DraughtsState.BLACK_MAN)
        state.set(9, 1, DraughtsState.BLACK_KING)
        // Capturing the last black man first reaches three kings against one king.
        var next = simulator.stateTransition(state, listOf(DraughtsAction(0, 4, 2, 6), null))
        assertEquals(DraughtsState.ENDGAME_SIXTEEN, next.endgameGroup)
        assertEquals(0, next.endgameMoveCount)
        next = play(simulator, next, listOf(DraughtsAction(9, 1, 8, 2), DraughtsAction(2, 2, 4, 4)))
        assertEquals(2, next.endgameMoveCount)
        // 15 moves each, then one more each.
        next.endgameMoveCount = 30
        next = play(simulator, next, listOf(DraughtsAction(8, 2, 9, 1)))
        assertFalse(isOver(simulator, next))
        next = play(simulator, next, listOf(DraughtsAction(4, 4, 3, 3)))
        assertEquals(32, next.endgameMoveCount)
        assertTrue(isOver(simulator, next))
        assertNeutral(simulator.calculateRewards(next))
    }

    @Test
    fun oneKingAgainstOneKingDrawsAfterFiveMovesEach() {
        val simulator = DraughtsSimulator()
        var state = emptyBoard()
        state.set(2, 2, DraughtsState.WHITE_KING)
        state.set(9, 1, DraughtsState.BLACK_KING)
        state.endgameGroup = DraughtsState.ENDGAME_FIVE
        for (ply in 0 until 10) {
            assertFalse("ply $ply", isOver(simulator, state))
            state = play(simulator, state, listOf(triangleAction(state, ply)))
        }
        assertEquals(10, state.endgameMoveCount)
        assertTrue(isOver(simulator, state))
        assertNeutral(simulator.calculateRewards(state))
    }

    @Test
    fun reachingTheFiveMoveGroupRestartsTheCount() {
        val simulator = DraughtsSimulator()
        var state = emptyBoard()
        state.set(2, 2, DraughtsState.WHITE_KING)
        state.set(0, 8, DraughtsState.WHITE_KING)
        state.set(7, 3, DraughtsState.WHITE_KING)
        state.set(6, 4, DraughtsState.BLACK_KING)
        state.agentTurn = DraughtsState.TURN_BLACK
        state.endgameGroup = DraughtsState.ENDGAME_SIXTEEN
        state.endgameMoveCount = 25
        // Black takes a king: two kings against one king.
        state = simulator.stateTransition(state, listOf(null, DraughtsAction(6, 4, 8, 2)))
        assertEquals(DraughtsState.ENDGAME_FIVE, state.endgameGroup)
        assertEquals(0, state.endgameMoveCount)
        for (ply in 0 until 10) {
            assertFalse("ply $ply", isOver(simulator, state))
            state = play(simulator, state, listOf(triangleAction(state, ply)))
        }
        assertTrue(isOver(simulator, state))
    }

    @Test
    fun noMoveLosesEvenWhenADrawRuleHolds() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(1, 1, DraughtsState.BLACK_MAN)
        state.set(9, 9, DraughtsState.WHITE_KING)
        state.agentTurn = DraughtsState.TURN_BLACK
        state.set(0, 0, DraughtsState.WHITE_MAN)
        state.set(2, 0, DraughtsState.WHITE_MAN)
        state.kingMoveCount = 50
        assertTrue(simulator.calculateLegalActions(state)[1].isEmpty())
        val rewards = simulator.calculateRewards(state)
        assertEquals(1, rewards[0])
        assertEquals(-1, rewards[1])
    }

    @Test
    fun moveThatCompletesEndgameLimitAndBlocksOpponentWins() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(9, 9, DraughtsState.BLACK_KING)
        state.set(8, 8, DraughtsState.WHITE_KING)
        state.set(5, 5, DraughtsState.WHITE_KING)
        state.set(0, 2, DraughtsState.WHITE_KING)
        state.endgameGroup = DraughtsState.ENDGAME_SIXTEEN
        state.endgameMoveCount = 31
        val next = simulator.stateTransition(state, listOf(DraughtsAction(5, 5, 7, 7), null))
        assertEquals(32, next.endgameMoveCount)
        assertTrue(isOver(simulator, next))
        val rewards = simulator.calculateRewards(next)
        assertEquals(1, rewards[0])
        assertEquals(-1, rewards[1])
    }

    @Test
    fun captureOfLastPieceWinsWhenDrawCountersAreFull() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 0, DraughtsState.WHITE_KING)
        state.set(4, 4, DraughtsState.BLACK_KING)
        state.kingMoveCount = 49
        state.endgameGroup = DraughtsState.ENDGAME_FIVE
        state.endgameMoveCount = 9
        val next = simulator.stateTransition(state, listOf(DraughtsAction(0, 0, 5, 5), null))
        val rewards = simulator.calculateRewards(next)
        assertEquals(1, rewards[0])
        assertEquals(-1, rewards[1])
    }

    @Test
    fun multiJumpCountsAsOneMove() {
        val simulator = DraughtsSimulator()
        val state = emptyBoard()
        state.set(0, 0, DraughtsState.WHITE_KING)
        state.set(2, 2, DraughtsState.BLACK_MAN)
        state.set(5, 1, DraughtsState.BLACK_MAN)
        state.set(9, 7, DraughtsState.BLACK_MAN)
        state.set(9, 9, DraughtsState.BLACK_KING)
        state.positionHistory.add("earlier")
        state.kingMoveCount = 10
        val mid = simulator.stateTransition(state, listOf(DraughtsAction(0, 0, 3, 3), null))
        assertEquals(3, mid.mustContinueFromX)
        // Nothing is counted in the middle of the capture.
        assertEquals(listOf("earlier"), mid.positionHistory)
        assertEquals(10, mid.kingMoveCount)
        assertEquals(DraughtsState.ENDGAME_NONE, mid.endgameGroup)
        assertFalse(isOver(simulator, mid))

        val end = simulator.stateTransition(mid, listOf(DraughtsAction(3, 3, 6, 0), null))
        assertEquals(DraughtsState.TURN_BLACK, end.agentTurn)
        assertTrue(end.positionHistory.isEmpty())
        assertEquals(0, end.kingMoveCount)
        // King against king and man: the five-move group, reached by this one move.
        assertEquals(DraughtsState.ENDGAME_FIVE, end.endgameGroup)
        assertEquals(0, end.endgameMoveCount)
    }

    private fun emptyBoard(): DraughtsState {
        val state = DraughtsState()
        for (x in 0 until DraughtsState.SIZE) {
            for (y in 0 until DraughtsState.SIZE) {
                state.set(x, y, DraughtsState.EMPTY)
            }
        }
        return state
    }

    /** White king, black king, and a man each, so no endgame limit applies. */
    private fun kingsWithMen(): DraughtsState {
        val state = emptyBoard()
        state.set(2, 2, DraughtsState.WHITE_KING)
        state.set(0, 2, DraughtsState.WHITE_MAN)
        state.set(9, 1, DraughtsState.BLACK_KING)
        state.set(9, 9, DraughtsState.BLACK_MAN)
        return state
    }

    /** Next move of the white triangle (2,2) -> (4,4) -> (3,3) or the black shuffle (9,1) <-> (8,2). */
    private fun triangleAction(state: DraughtsState, ply: Int): DraughtsAction {
        return if (state.agentTurn == DraughtsState.TURN_WHITE) {
            when {
                state.get(2, 2) == DraughtsState.WHITE_KING -> DraughtsAction(2, 2, 4, 4)
                state.get(4, 4) == DraughtsState.WHITE_KING -> DraughtsAction(4, 4, 3, 3)
                else -> DraughtsAction(3, 3, 2, 2)
            }
        } else {
            if (state.get(9, 1) == DraughtsState.BLACK_KING) DraughtsAction(9, 1, 8, 2) else DraughtsAction(8, 2, 9, 1)
        }.also { assertTrue("ply $ply: $it", legalFor(state).contains(it)) }
    }

    private fun legalFor(state: DraughtsState): Set<DraughtsAction> =
        DraughtsSimulator().calculateLegalActions(state)[state.agentTurn.toInt()]

    private fun play(simulator: DraughtsSimulator, start: DraughtsState, moves: List<DraughtsAction>): DraughtsState {
        var state = start
        for (move in moves) {
            val actions = if (state.agentTurn == DraughtsState.TURN_WHITE) listOf(move, null) else listOf(null, move)
            state = simulator.stateTransition(state, actions)
        }
        return state
    }

    private fun isOver(simulator: DraughtsSimulator, state: DraughtsState): Boolean =
        simulator.calculateLegalActions(state).all { it.isEmpty() }

    private fun assertNeutral(rewards: IntArray) {
        assertEquals(0, rewards[0])
        assertEquals(0, rewards[1])
    }
}
