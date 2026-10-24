package com.castlefrog.agl.domains.chess

import com.castlefrog.agl.agents.RandomAgent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class ChessAlphaBetaAgentTest {

    private val simulator = ChessSimulator()

    private fun agent(depth: Int) = ChessAlphaBetaAgent(maxDepth = depth, timeLimitMillis = 0)

    private fun legalMovesOf(state: ChessState): Set<ChessAction> =
        simulator.calculateLegalActions(state)[state.agentTurn.toInt()]

    private fun play(state: ChessState, move: ChessAction): ChessState {
        val actions = if (state.agentTurn == ChessState.TURN_WHITE) listOf(move, null) else listOf(null, move)
        return simulator.stateTransition(state, actions)
    }

    private fun isOver(state: ChessState) = simulator.calculateLegalActions(state).all { it.isEmpty() }

    @Test
    fun findsMateInOne() {
        // Back-rank mate: Ra1-a8#.
        val state = fen("6k1/5ppp/8/8/8/8/8/R5K1 w")
        val result = agent(2).search(state, simulator)
        assertEquals(ChessAction(0, 0, 0, 7), result.move)
        assertEquals(ChessAlphaBetaAgent.MATE_SCORE - 1, result.score)
    }

    @Test
    fun findsMateInTwo() {
        // Rooks a1 and b2 against a bare king on h8: no mate in one, but Rb7 then Ra8# mates.
        val state = fen("7k/8/8/8/8/8/1R6/R5K1 w")
        val result = agent(3).search(state, simulator)
        assertEquals(ChessAlphaBetaAgent.MATE_SCORE - 3, result.score)
        val afterFirst = play(state, result.move!!)
        for (reply in legalMovesOf(afterFirst)) {
            val afterReply = play(afterFirst, reply)
            val mate = play(afterReply, agent(2).search(afterReply, simulator).move!!)
            assertTrue(isOver(mate))
            assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(mate))
        }
    }

    @Test
    fun winsHangingMaterial() {
        // The black rook on d5 is undefended and the e3 knight attacks it.
        val state = fen("4k3/8/8/3r4/8/4N3/8/4K3 w")
        assertEquals(ChessAction(4, 2, 3, 4), agent(2).search(state, simulator).move)
    }

    @Test
    fun avoidsHangingItsQueen() {
        // The e5 pawn attacks the queen on d4 and is defended by d6, so Qxe5 loses the queen.
        val state = fen("4k3/8/3p4/4p3/3Q4/8/8/4K3 w")
        val move = agent(2).search(state, simulator).move!!
        assertNotEquals(ChessAction(3, 3, 4, 4), move)
        val after = play(state, move)
        val queen = queenSquare(after)
        assertTrue(legalMovesOf(after).none { it.toX == queen.first && it.toY == queen.second })
    }

    @Test
    fun quiescenceSeesTheRecapture() {
        // At depth 1, Qxd5 wins a pawn unless the search looks on to exd5.
        val state = fen("4k3/8/4p3/3p4/8/8/8/3QK3 w")
        assertNotEquals(ChessAction(3, 0, 3, 4), agent(1).search(state, simulator).move)
    }

    @Test
    fun returnsALegalMoveInCheck() {
        val state = fen("4k3/8/8/8/1b6/8/8/4K3 w")
        assertTrue(simulator.isInCheck(state))
        val move = agent(3).search(state, simulator).move
        assertTrue(move in legalMovesOf(state))
    }

    @Test
    fun promotesAPawn() {
        // The black king attacks the pawn, so it must promote now.
        val state = fen("8/P7/1k6/8/8/8/8/4K3 w")
        val move = agent(3).search(state, simulator).move!!
        assertTrue(move in legalMovesOf(state))
        assertEquals(ChessState.QUEEN, move.promotion)
    }

    @Test
    fun returnsALegalMoveWithCastlingRights() {
        val state = fen("r3k2r/pppq1ppp/2n2n2/2bpp3/2BPP3/2N2N2/PPPQ1PPP/R3K2R w KQkq")
        val move = agent(3).search(state, simulator).move
        assertTrue(move in legalMovesOf(state))
        val black = play(state, move!!)
        assertTrue(agent(3).search(black, simulator).move in legalMovesOf(black))
    }

    @Test
    fun alwaysReturnsALegalMoveDuringRandomGames() {
        val random = Random(7)
        val randomAgent = RandomAgent(random)
        repeat(3) {
            var state = simulator.initialState
            var ply = 0
            while (!isOver(state) && ply < 80) {
                val turn = state.agentTurn.toInt()
                val move = if (ply % 4 == 0) {
                    agent(2).selectAction(turn, state, simulator)!!.also { assertTrue(it in legalMovesOf(state)) }
                } else {
                    randomAgent.selectAction(turn, state, simulator)!!
                }
                state = play(state, move)
                ply++
            }
        }
    }

    @Test
    fun returnsNullWhenItIsNotThePlayersTurnOrTheGameIsOver() {
        assertNull(agent(2).selectAction(1, simulator.initialState, simulator))
        val mated = fen("R5k1/5ppp/8/8/8/8/8/6K1 b")
        assertTrue(isOver(mated))
        assertNull(agent(2).selectAction(1, mated, simulator))
        assertNull(agent(2).search(mated, simulator).move)
    }

    @Test
    fun stopsAtTheDepthLimit() {
        val shallow = agent(1).search(simulator.initialState, simulator)
        val deep = agent(3).search(simulator.initialState, simulator)
        assertEquals(1, shallow.depth)
        assertEquals(3, deep.depth)
        assertTrue(deep.nodes > shallow.nodes)
    }

    @Test
    fun stopsAtTheTimeLimit() {
        val agent = ChessAlphaBetaAgent(maxDepth = 64, timeLimitMillis = 100)
        val result = agent.search(simulator.initialState, simulator)
        assertTrue(result.move in legalMovesOf(simulator.initialState))
        assertTrue(result.depth in 1 until 64)
        assertTrue("took ${result.elapsedMillis} ms", result.elapsedMillis < 5_000)
    }

    @Test
    fun stopsWhenCancelledBeforeStarting() {
        val agent = ChessAlphaBetaAgent(maxDepth = 64, timeLimitMillis = 0, isCancelled = { true })
        val result = agent.search(simulator.initialState, simulator)
        assertTrue(result.move in legalMovesOf(simulator.initialState))
        assertTrue(result.depth <= 1)
    }

    @Test
    fun stopsWhenCancelledFromAnotherThread() {
        val cancelled = AtomicBoolean(false)
        val agent = ChessAlphaBetaAgent(maxDepth = 64, timeLimitMillis = 0, isCancelled = { cancelled.get() })
        val canceller = Thread {
            Thread.sleep(50)
            cancelled.set(true)
        }
        canceller.start()
        val start = System.nanoTime()
        val result = agent.search(simulator.initialState, simulator)
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        canceller.join()
        assertTrue(result.move in legalMovesOf(simulator.initialState))
        assertTrue(result.depth < 64)
        assertTrue("took $elapsedMillis ms", elapsedMillis < 5_000)
    }

    @Test
    fun sameSeedGivesTheSameMoves() {
        val positions = listOf(
            simulator.initialState,
            fen("r3k2r/pppq1ppp/2n2n2/2bpp3/2BPP3/2N2N2/PPPQ1PPP/R3K2R w KQkq"),
            fen("4k3/8/3p4/4p3/3Q4/8/8/4K3 w")
        )
        for (strength in ChessStrength.entries.take(3)) {
            val first = positions.map { ChessAlphaBetaAgent(strength, Random(11)).search(it, simulator).move }
            val second = positions.map { ChessAlphaBetaAgent(strength, Random(11)).search(it, simulator).move }
            assertEquals(first, second)
        }
    }

    @Test
    fun weakLevelsVaryTheirMoves() {
        val moves = (0 until 20).map {
            ChessAlphaBetaAgent(ChessStrength.BEGINNER, Random(it)).search(simulator.initialState, simulator).move
        }
        assertTrue(moves.toSet().size > 1)
    }

    @Test
    fun weakLevelsStillPlayAMateInOne() {
        val state = fen("6k1/5ppp/8/8/8/8/8/R5K1 w")
        for (seed in 0 until 10) {
            val move = ChessAlphaBetaAgent(ChessStrength.BEGINNER, Random(seed)).search(state, simulator).move
            assertEquals(ChessAction(0, 0, 0, 7), move)
        }
    }

    @Test
    fun weakLevelsKeepWithinTheMargin() {
        // The hanging rook is worth far more than BEGINNER's margin, so every seed takes it.
        val state = fen("4k3/8/8/3r4/8/4N3/8/4K3 w")
        for (seed in 0 until 10) {
            val move = ChessAlphaBetaAgent(ChessStrength.BEGINNER, Random(seed)).search(state, simulator).move
            assertEquals(ChessAction(4, 2, 3, 4), move)
        }
    }

    @Test
    fun beatsRandomAgent() {
        val random = Random(2024)
        var wins = 0
        val games = 10
        for (game in 0 until games) {
            val searcherPlays = game % 2
            val players = listOf(agent(2), RandomAgent(random))
            var state = simulator.initialState
            var ply = 0
            while (!isOver(state) && ply < 300) {
                val turn = state.agentTurn.toInt()
                val mover = if (turn == searcherPlays) players[0] else players[1]
                state = play(state, mover.selectAction(turn, state, simulator)!!)
                ply++
            }
            if (simulator.calculateRewards(state)[searcherPlays] > 0) wins++
        }
        assertEquals(games, wins)
    }

    @Test
    fun rejectsOtherDomains() {
        val hex = com.castlefrog.agl.domains.hex.HexSimulator()
        val result = runCatching { agent(1).selectAction(0, hex.initialState, hex) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun queenSquare(state: ChessState): Pair<Int, Int> {
        for (x in 0 until 8) for (y in 0 until 8) {
            if (state.get(x, y) == ChessState.QUEEN.toByte()) return x to y
        }
        error("no white queen")
    }

    /** Builds a state from the board, side to move and castling fields of a FEN string. */
    private fun fen(text: String): ChessState {
        val fields = text.split(" ")
        val state = ChessState()
        for (x in 0 until 8) for (y in 0 until 8) state.set(x, y, ChessState.EMPTY)
        fields[0].split("/").forEachIndexed { row, rank ->
            var x = 0
            for (c in rank) {
                if (c.isDigit()) {
                    x += c.digitToInt()
                } else {
                    val type = "pnbrqk".indexOf(c.lowercaseChar()) + 1
                    state.set(x, 7 - row, (if (c.isUpperCase()) type else -type).toByte())
                    x++
                }
            }
        }
        state.agentTurn = if (fields[1] == "w") ChessState.TURN_WHITE else ChessState.TURN_BLACK
        val castling = fields.getOrElse(2) { "-" }
        state.castlingRights = listOf('K', 'Q', 'k', 'q').withIndex()
            .filter { it.value in castling }.sumOf { 1 shl it.index }
        state.enPassantX = -1
        return state
    }
}
