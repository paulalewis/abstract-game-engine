package com.castlefrog.agl.domains.chess

import com.castlefrog.agl.IllegalActionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSimulatorTest {

    @Test
    fun initialLegalMovesIncludePawnAndKnight() {
        val simulator = ChessSimulator()
        val legal = simulator.calculateLegalActions(simulator.initialState)[0]
        assertTrue(legal.contains(ChessAction(4, 1, 4, 3))) // e2-e4
        assertTrue(legal.contains(ChessAction(1, 0, 2, 2))) // Nb1-c3
        assertEquals(20, legal.size)
    }

    @Test
    fun pawnDoublePushSetsEnPassant() {
        val simulator = ChessSimulator()
        val next = simulator.stateTransition(
            simulator.initialState,
            listOf(ChessAction(4, 1, 4, 3), null)
        )
        assertEquals(4, next.enPassantX)
        assertEquals(ChessState.TURN_BLACK, next.agentTurn)
        assertEquals(ChessState.EMPTY, next.get(4, 1))
        assertEquals(ChessState.PAWN.toByte(), next.get(4, 3))
    }

    @Test
    fun scholarsMateCheckmate() {
        val simulator = ChessSimulator()
        var state = simulator.initialState
        // 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6?? 4. Qxf7#
        state = simulator.stateTransition(state, listOf(ChessAction(4, 1, 4, 3), null))
        state = simulator.stateTransition(state, listOf(null, ChessAction(4, 6, 4, 4)))
        state = simulator.stateTransition(state, listOf(ChessAction(3, 0, 7, 4), null)) // Qd1-h5
        state = simulator.stateTransition(state, listOf(null, ChessAction(1, 7, 2, 5))) // Nb8-c6
        state = simulator.stateTransition(state, listOf(ChessAction(5, 0, 2, 3), null)) // Bf1-c4
        state = simulator.stateTransition(state, listOf(null, ChessAction(6, 7, 5, 5))) // Ng8-f6
        state = simulator.stateTransition(state, listOf(ChessAction(7, 4, 5, 6), null)) // Qh5xf7
        assertTrue(simulator.isInCheck(state, 1))
        assertTrue(simulator.calculateLegalActions(state)[1].isEmpty())
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(state))
    }

    @Test
    fun quietCheckmateThatTicksClockTo100IsWinNotDraw() {
        val simulator = ChessSimulator()
        val state = ChessState()
        // KR vs K: white king a6, white rook h7, black king a8. Quiet Rh7-h8# mates.
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        state.set(0, 5, ChessState.KING.toByte())
        state.set(7, 6, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        state.agentTurn = ChessState.TURN_WHITE
        state.castlingRights = 0
        state.enPassantX = -1
        state.halfmoveClock = 99
        val mated = simulator.stateTransition(state, listOf(ChessAction(7, 6, 7, 7), null))
        assertEquals(100, mated.halfmoveClock)
        assertEquals(ChessState.TURN_BLACK, mated.agentTurn)
        assertTrue(simulator.isInCheck(mated, 1))
        assertTrue(simulator.calculateLegalActions(mated)[1].isEmpty())
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(mated))
    }

    @Test
    fun cannotLeaveKingInCheck() {
        val simulator = ChessSimulator()
        val state = ChessState()
        // Clear and put white king e1, black rook e8 — white to move cannot move unrelated piece
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        state.set(4, 0, ChessState.KING.toByte())
        state.set(4, 7, (-ChessState.ROOK).toByte())
        state.set(0, 0, ChessState.ROOK.toByte())
        state.agentTurn = ChessState.TURN_WHITE
        state.castlingRights = 0
        val legal = simulator.calculateLegalActions(state)[0]
        // Moving rook on a-file does not resolve check
        assertFalse(legal.contains(ChessAction(0, 0, 0, 1)))
        // King can step off the file
        assertTrue(legal.any { it.fromX == 4 && it.fromY == 0 })
    }

    @Test
    fun emptyActionsThrows() {
        val simulator = ChessSimulator()
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, emptyList())
        }
    }

    @Test
    fun illegalMoveThrows() {
        val simulator = ChessSimulator()
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(
                simulator.initialState,
                listOf(ChessAction(0, 0, 0, 2), null) // rook blocked
            )
        }
    }

    @Test
    fun rewardsNeutralAtStart() {
        val simulator = ChessSimulator()
        assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(simulator.initialState))
    }

    @Test
    fun knightsDevelopWithoutCapture() {
        val simulator = ChessSimulator()
        val state = simulator.stateTransition(
            simulator.initialState,
            listOf(ChessAction(6, 0, 5, 2), null) // Ng1-f3
        )
        assertEquals(ChessState.KNIGHT.toByte(), state.get(5, 2))
        assertEquals(ChessState.EMPTY, state.get(6, 0))
    }

    private fun play(simulator: ChessSimulator, state: ChessState, vararg moves: ChessAction): ChessState {
        var s = state
        for (move in moves) {
            val actions = if (s.agentTurn == ChessState.TURN_WHITE) listOf(move, null) else listOf(null, move)
            s = simulator.stateTransition(s, actions)
        }
        return s
    }

    private fun isOver(simulator: ChessSimulator, state: ChessState): Boolean =
        simulator.calculateLegalActions(state).all { it.isEmpty() }

    private val whiteKnightOut = ChessAction(6, 0, 5, 2) // Ng1-f3
    private val whiteKnightBack = ChessAction(5, 2, 6, 0) // Nf3-g1
    private val blackKnightOut = ChessAction(6, 7, 5, 5) // Ng8-f6
    private val blackKnightBack = ChessAction(5, 5, 6, 7) // Nf6-g8

    @Test
    fun knightShuffleDrawsOnThirdOccurrenceOfStartingPosition() {
        val simulator = ChessSimulator()
        var state = simulator.initialState
        val cycle = listOf(whiteKnightOut, blackKnightOut, whiteKnightBack, blackKnightBack)
        // First cycle: starting position occurs for the second time. Nothing has occurred three times.
        for (move in cycle) {
            assertFalse(isOver(simulator, state))
            state = play(simulator, state, move)
        }
        assertEquals(simulator.initialState.board.map { it.toList() }, state.board.map { it.toList() })
        assertFalse(isOver(simulator, state))
        // Second cycle: every position before the last occurs only twice.
        for (move in cycle) {
            assertFalse(isOver(simulator, state))
            state = play(simulator, state, move)
        }
        // Starting position for the third time: drawn.
        assertEquals(8, state.positionHistory.size)
        assertTrue(simulator.isThreefoldRepetition(state))
        assertTrue(isOver(simulator, state))
        assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(whiteKnightOut, null))
        }
    }

    @Test
    fun pawnMoveClearsPositionHistory() {
        val simulator = ChessSimulator()
        var state = play(
            simulator,
            simulator.initialState,
            whiteKnightOut, blackKnightOut, whiteKnightBack, blackKnightBack // start position, 2nd time
        )
        assertEquals(4, state.positionHistory.size)
        state = play(simulator, state, ChessAction(4, 1, 4, 3)) // e2-e4
        assertEquals(0, state.halfmoveClock)
        assertTrue(state.positionHistory.isEmpty())
        // The position after e4 needs three occurrences of its own.
        state = play(simulator, state, blackKnightOut, whiteKnightOut, blackKnightBack, whiteKnightBack)
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, blackKnightOut, whiteKnightOut, blackKnightBack)
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, whiteKnightBack)
        assertTrue(isOver(simulator, state))
    }

    @Test
    fun captureClearsPositionHistory() {
        val simulator = ChessSimulator()
        // 1. Nf3 Nf6 2. Ng1 Ng8 3. Nc3 d5 4. Nxd5
        var state = play(
            simulator,
            simulator.initialState,
            whiteKnightOut, blackKnightOut, whiteKnightBack, blackKnightBack,
            ChessAction(1, 0, 2, 2), // Nb1-c3
            ChessAction(3, 6, 3, 4) // d7-d5
        )
        state = play(simulator, state, ChessAction(2, 2, 3, 4)) // Nc3xd5
        assertEquals(0, state.halfmoveClock)
        assertTrue(state.positionHistory.isEmpty())
        state = play(simulator, state, ChessAction(1, 7, 2, 5)) // Nb8-c6, a quiet move is recorded again
        assertEquals(1, state.positionHistory.size)
    }

    @Test
    fun lostCastlingRightsMakePositionsDiffer() {
        val simulator = ChessSimulator()
        // 1. e4 e5, then the kings walk out and back, losing castling rights.
        var state = play(simulator, simulator.initialState, ChessAction(4, 1, 4, 3), ChessAction(4, 6, 4, 4))
        val kingCycle = arrayOf(
            ChessAction(4, 0, 4, 1), // Ke1-e2
            ChessAction(4, 7, 4, 6), // Ke8-e7
            ChessAction(4, 1, 4, 0), // Ke2-e1
            ChessAction(4, 6, 4, 7) // Ke7-e8
        )
        state = play(simulator, state, *kingCycle)
        state = play(simulator, state, *kingCycle)
        // The board after 1. e4 e5 has now occurred three times, but only twice without castling rights.
        assertEquals(0, state.castlingRights)
        assertFalse(simulator.isThreefoldRepetition(state))
        assertFalse(isOver(simulator, state))
        // Ke2 with no castling rights left has occurred only twice; the first time black could still castle.
        state = play(simulator, state, kingCycle[0])
        assertFalse(isOver(simulator, state))
        // Kings on e2 and e7 with no castling rights: third time.
        state = play(simulator, state, kingCycle[1])
        assertTrue(isOver(simulator, state))
    }

    @Test
    fun sideToMoveMakesPositionsDiffer() {
        val simulator = ChessSimulator()
        // White king walks a triangle a1-b1-b2-a1 while black's king shuffles h8-g8-h8, so the
        // starting board recurs with either side to move.
        val start = ChessState()
        for (x in 0 until 8) for (y in 0 until 8) start.set(x, y, ChessState.EMPTY)
        start.set(0, 0, ChessState.KING.toByte())
        start.set(7, 7, (-ChessState.KING).toByte())
        start.castlingRights = 0
        val whiteMoves = listOf(ChessAction(0, 0, 1, 0), ChessAction(1, 0, 1, 1), ChessAction(1, 1, 0, 0))
        val blackMoves = listOf(ChessAction(7, 7, 6, 7), ChessAction(6, 7, 7, 7))
        var state = start
        var ply = 0
        val startBoard = start.board.map { it.toList() }
        var startBoardSeen = 1
        while (!isOver(simulator, state)) {
            val move = if (ply % 2 == 0) whiteMoves[(ply / 2) % 3] else blackMoves[(ply / 2) % 2]
            state = play(simulator, state, move)
            ply++
            if (state.board.map { it.toList() } == startBoard) startBoardSeen++
        }
        // Plies 0, 5, 12, 17 and 24 show the starting board; only plies 0, 12 and 24 have white to move.
        assertEquals(24, ply)
        assertEquals(5, startBoardSeen)
        assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
    }

    @Test
    fun unusableEnPassantDoesNotMakePositionsDiffer() {
        val simulator = ChessSimulator()
        // 1. e4: en passant file is set, but no black pawn can capture.
        var state = play(simulator, simulator.initialState, ChessAction(4, 1, 4, 3))
        assertEquals(4, state.enPassantX)
        val cycle = arrayOf(blackKnightOut, whiteKnightOut, blackKnightBack, whiteKnightBack)
        state = play(simulator, state, *cycle)
        assertEquals(-1, state.enPassantX)
        assertFalse(isOver(simulator, state))
        state = play(simulator, state, *cycle)
        // Third occurrence of the position after 1. e4, counting the one with the unusable en passant file.
        assertTrue(isOver(simulator, state))
    }

    @Test
    fun usableEnPassantMakesPositionsDiffer() {
        val simulator = ChessSimulator()
        // 1. Nc3 e5 2. Nb1 e4 3. d4: black's e-pawn can capture en passant on d3.
        var state = play(
            simulator,
            simulator.initialState,
            ChessAction(1, 0, 2, 2),
            ChessAction(4, 6, 4, 4),
            ChessAction(2, 2, 1, 0),
            ChessAction(4, 4, 4, 3),
            ChessAction(3, 1, 3, 3)
        )
        assertTrue(simulator.calculateLegalActions(state)[1].contains(ChessAction(4, 3, 3, 2)))
        val cycle = arrayOf(blackKnightOut, whiteKnightOut, blackKnightBack, whiteKnightBack)
        state = play(simulator, state, *cycle)
        state = play(simulator, state, *cycle)
        // Same board three times, but only twice without the en passant capture available.
        assertFalse(isOver(simulator, state))
        // The position after ...Nf6 has no en passant in any occurrence: third time.
        state = play(simulator, state, blackKnightOut)
        assertTrue(isOver(simulator, state))
    }

    @Test
    fun pinnedEnPassantCapturerDoesNotCountInPositionKey() {
        val simulator = ChessSimulator()
        // White king a5, white pawn b5, black rook h5, black pawn c7, black king h8; black to move.
        var state = ChessState()
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        state.set(0, 4, ChessState.KING.toByte())
        state.set(1, 4, ChessState.PAWN.toByte())
        state.set(7, 4, (-ChessState.ROOK).toByte())
        state.set(2, 6, (-ChessState.PAWN).toByte())
        state.set(7, 7, (-ChessState.KING).toByte())
        state.agentTurn = ChessState.TURN_BLACK
        state.castlingRights = 0
        state = play(simulator, state, ChessAction(2, 6, 2, 4)) // c7-c5
        assertEquals(2, state.enPassantX)
        // bxc6 e.p. would expose the white king along the 5th rank, so it is not legal.
        assertFalse(simulator.calculateLegalActions(state)[0].contains(ChessAction(1, 4, 2, 5)))
        val noEnPassant = state.copy()
        noEnPassant.enPassantX = -1
        assertEquals(simulator.positionKey(noEnPassant), simulator.positionKey(state))
    }

    @Test
    fun mateThatCompletesRepetitionStillWins() {
        val simulator = ChessSimulator()
        val state = ChessState()
        // KR vs K: white king a6, white rook h7, black king a8. Quiet Rh7-h8# mates.
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        state.set(0, 5, ChessState.KING.toByte())
        state.set(7, 6, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        state.castlingRights = 0
        state.halfmoveClock = 10
        val matedKey = simulator.positionKey(simulator.applyMove(state, ChessAction(7, 6, 7, 7)))
        // Pretend the mated position has occurred twice before.
        state.positionHistory.add(matedKey)
        state.positionHistory.add(matedKey)
        val mated = simulator.stateTransition(state, listOf(ChessAction(7, 6, 7, 7), null))
        assertTrue(simulator.isThreefoldRepetition(mated))
        assertTrue(isOver(simulator, mated))
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(mated))
    }

    private fun emptyBoard(agentTurn: Byte): ChessState {
        val state = ChessState()
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        state.agentTurn = agentTurn
        state.castlingRights = 0
        return state
    }

    @Test
    fun startPositionIsNotInCheck() {
        val simulator = ChessSimulator()
        assertFalse(simulator.isInCheck(simulator.initialState))
    }

    @Test
    fun simpleCheckIsInCheck() {
        val simulator = ChessSimulator()
        // White king e1, black rook e8, black king a8; white to move.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(4, 0, ChessState.KING.toByte())
        state.set(4, 7, (-ChessState.ROOK).toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        assertTrue(simulator.isInCheck(state))
        // Only the side to move is asked about.
        state.agentTurn = ChessState.TURN_BLACK
        assertFalse(simulator.isInCheck(state))
    }

    @Test
    fun checkByPinnedPieceIsInCheck() {
        val simulator = ChessSimulator()
        // White king d4, white rook e1; black knight e6 pinned to black king e8; white to move.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(3, 3, ChessState.KING.toByte())
        state.set(4, 0, ChessState.ROOK.toByte())
        state.set(4, 5, (-ChessState.KNIGHT).toByte())
        state.set(4, 7, (-ChessState.KING).toByte())
        // The knight cannot move to take the king, but it still gives check.
        state.agentTurn = ChessState.TURN_BLACK
        assertTrue(simulator.calculateLegalActions(state)[1].none { it.fromX == 4 && it.fromY == 5 })
        state.agentTurn = ChessState.TURN_WHITE
        assertTrue(simulator.isInCheck(state))
    }

    @Test
    fun checkmateIsInCheck() {
        val simulator = ChessSimulator()
        // KR vs K: white king a6, white rook h7, black king a8. Rh7-h8#.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(0, 5, ChessState.KING.toByte())
        state.set(7, 6, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        val mated = play(simulator, state, ChessAction(7, 6, 7, 7))
        assertTrue(simulator.calculateLegalActions(mated)[1].isEmpty())
        assertTrue(simulator.isInCheck(mated))
    }

    @Test
    fun stalemateIsNotInCheck() {
        val simulator = ChessSimulator()
        // Black king a8, white queen b6, white king c1; black to move and has no moves.
        val state = emptyBoard(ChessState.TURN_BLACK)
        state.set(0, 7, (-ChessState.KING).toByte())
        state.set(1, 5, ChessState.QUEEN.toByte())
        state.set(2, 0, ChessState.KING.toByte())
        assertTrue(simulator.calculateLegalActions(state)[1].isEmpty())
        assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
        assertFalse(simulator.isInCheck(state))
    }

    /** [action] is illegal by every route: legal actions, [ChessSimulator.isLegalAction], and stateTransition. */
    private fun assertIllegal(simulator: ChessSimulator, state: ChessState, action: ChessAction) {
        for (player in 0 until 2) {
            assertFalse(simulator.calculateLegalActions(state)[player].contains(action))
            assertFalse(simulator.isLegalAction(state, player, action))
        }
        val actions = if (state.agentTurn == ChessState.TURN_WHITE) listOf(action, null) else listOf(null, action)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, actions)
        }
    }

    private fun assertLegal(simulator: ChessSimulator, state: ChessState, action: ChessAction) {
        val turn = state.agentTurn.toInt()
        assertTrue(simulator.calculateLegalActions(state)[turn].contains(action))
        assertTrue(simulator.isLegalAction(state, turn, action))
        assertFalse(simulator.isLegalAction(state, 1 - turn, action))
    }

    @Test
    fun movingOpponentsPieceIsIllegal() {
        val simulator = ChessSimulator()
        // White to move; e7-e5 is black's move.
        assertIllegal(simulator, simulator.initialState, ChessAction(4, 6, 4, 4))
    }

    @Test
    fun movingFromEmptySquareIsIllegal() {
        val simulator = ChessSimulator()
        assertIllegal(simulator, simulator.initialState, ChessAction(4, 2, 4, 3)) // e3-e4
    }

    @Test
    fun moveOffTheBoardIsIllegal() {
        val simulator = ChessSimulator()
        assertIllegal(simulator, simulator.initialState, ChessAction(-1, 0, 0, 2))
        assertIllegal(simulator, simulator.initialState, ChessAction(8, 1, 7, 2))
        assertIllegal(simulator, simulator.initialState, ChessAction(0, 1, 0, -1))
        assertIllegal(simulator, simulator.initialState, ChessAction(7, 1, 8, 2))
    }

    @Test
    fun movingPinnedPieceIsIllegal() {
        val simulator = ChessSimulator()
        // White king e1, white bishop e2 pinned by black rook e8, black king a8.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(4, 0, ChessState.KING.toByte())
        state.set(4, 1, ChessState.BISHOP.toByte())
        state.set(4, 7, (-ChessState.ROOK).toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        assertIllegal(simulator, state, ChessAction(4, 1, 3, 2)) // Be2-d3
        assertLegal(simulator, state, ChessAction(4, 0, 3, 0)) // Ke1-d1
    }

    @Test
    fun castlingThroughOrOutOfCheckIsIllegal() {
        val simulator = ChessSimulator()
        // White king e1, rooks a1 and h1, black king a8; white may castle either side.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(4, 0, ChessState.KING.toByte())
        state.set(0, 0, ChessState.ROOK.toByte())
        state.set(7, 0, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        state.castlingRights = 0b0011
        val kingSide = ChessAction(4, 0, 6, 0)
        val queenSide = ChessAction(4, 0, 2, 0)
        assertLegal(simulator, state, kingSide)
        assertLegal(simulator, state, queenSide)

        // Black rook f8 attacks f1: king side passes through check, queen side is fine.
        val throughCheck = state.copy()
        throughCheck.set(5, 7, (-ChessState.ROOK).toByte())
        assertIllegal(simulator, throughCheck, kingSide)
        assertLegal(simulator, throughCheck, queenSide)

        // Black rook e8 gives check: neither side may castle.
        val outOfCheck = state.copy()
        outOfCheck.set(4, 7, (-ChessState.ROOK).toByte())
        assertIllegal(simulator, outOfCheck, kingSide)
        assertIllegal(simulator, outOfCheck, queenSide)

        // Without the right, castling is illegal even when nothing attacks.
        val noRights = state.copy()
        noRights.castlingRights = 0b0010
        assertIllegal(simulator, noRights, kingSide)
        assertLegal(simulator, noRights, queenSide)
    }

    @Test
    fun enPassantIsIllegalWhenNotAvailable() {
        val simulator = ChessSimulator()
        // White pawn e5, black pawn d5, kings e1 and e8; white to move.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(4, 4, ChessState.PAWN.toByte())
        state.set(3, 4, (-ChessState.PAWN).toByte())
        state.set(4, 0, ChessState.KING.toByte())
        state.set(4, 7, (-ChessState.KING).toByte())
        val enPassant = ChessAction(4, 4, 3, 5) // e5xd6 e.p.
        state.enPassantX = -1
        assertIllegal(simulator, state, enPassant)
        state.enPassantX = 2 // the c-file, not the d-pawn
        assertIllegal(simulator, state, enPassant)
        state.enPassantX = 3
        assertLegal(simulator, state, enPassant)
    }

    @Test
    fun promotionMustChooseAPieceOnlyOnTheLastRank() {
        val simulator = ChessSimulator()
        // White pawns a7 and h2, kings e1 and e8; white to move.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(0, 6, ChessState.PAWN.toByte())
        state.set(7, 1, ChessState.PAWN.toByte())
        state.set(4, 0, ChessState.KING.toByte())
        state.set(4, 7, (-ChessState.KING).toByte())
        assertIllegal(simulator, state, ChessAction(0, 6, 0, 7)) // a8 with no piece chosen
        assertIllegal(simulator, state, ChessAction(0, 6, 0, 7, -ChessState.QUEEN)) // black's queen
        assertIllegal(simulator, state, ChessAction(0, 6, 0, 7, ChessState.KING))
        assertIllegal(simulator, state, ChessAction(7, 1, 7, 2, ChessState.QUEEN)) // h3 is not the last rank
        assertLegal(simulator, state, ChessAction(0, 6, 0, 7, ChessState.QUEEN))
        assertLegal(simulator, state, ChessAction(0, 6, 0, 7, ChessState.KNIGHT))
        assertLegal(simulator, state, ChessAction(7, 1, 7, 2))
    }

    @Test
    fun noMoveIsLegalAfterCheckmate() {
        val simulator = ChessSimulator()
        // KR vs K: white king a6, white rook h7, black king a8. Rh7-h8#.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(0, 5, ChessState.KING.toByte())
        state.set(7, 6, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        val mated = play(simulator, state, ChessAction(7, 6, 7, 7))
        assertIllegal(simulator, mated, ChessAction(0, 7, 1, 6)) // Ka8-b7
        assertIllegal(simulator, mated, ChessAction(0, 7, 1, 7)) // Ka8-b8
    }

    @Test
    fun noMoveIsLegalInStalemate() {
        val simulator = ChessSimulator()
        // Black king a8, white queen b6, white king c1; black to move and has no moves.
        val state = emptyBoard(ChessState.TURN_BLACK)
        state.set(0, 7, (-ChessState.KING).toByte())
        state.set(1, 5, ChessState.QUEEN.toByte())
        state.set(2, 0, ChessState.KING.toByte())
        assertIllegal(simulator, state, ChessAction(0, 7, 1, 7)) // Ka8-b8
        assertIllegal(simulator, state, ChessAction(0, 7, 0, 6)) // Ka8-a7
    }

    @Test
    fun noMoveIsLegalAfterFiftyMoveDraw() {
        val simulator = ChessSimulator()
        // KR vs K with the clock at 100: Rh7-h1 would otherwise be legal.
        val state = emptyBoard(ChessState.TURN_WHITE)
        state.set(0, 5, ChessState.KING.toByte())
        state.set(7, 6, ChessState.ROOK.toByte())
        state.set(0, 7, (-ChessState.KING).toByte())
        state.halfmoveClock = 99
        assertLegal(simulator, state, ChessAction(7, 6, 7, 0))
        state.halfmoveClock = 100
        assertIllegal(simulator, state, ChessAction(7, 6, 7, 0))
    }

    @Test
    fun noMoveIsLegalAfterThreefoldRepetition() {
        val simulator = ChessSimulator()
        val cycle = arrayOf(whiteKnightOut, blackKnightOut, whiteKnightBack, blackKnightBack)
        val state = play(simulator, simulator.initialState, *cycle, *cycle)
        assertTrue(simulator.isThreefoldRepetition(state))
        assertIllegal(simulator, state, whiteKnightOut)
        assertIllegal(simulator, state, ChessAction(4, 1, 4, 3)) // e2-e4
    }
}
