package com.castlefrog.agl.domains.chess

import com.castlefrog.agl.Action
import com.castlefrog.agl.Agent
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import kotlin.math.exp
import kotlin.random.Random

/**
 * A chess agent that searches with negamax, alpha-beta pruning and iterative deepening, and
 * a quiescence search over captures and promotions at the leaves.
 *
 * Moves are ordered with the previous iteration's best move first at the root, then captures
 * by most valuable victim and least valuable attacker (MVV-LVA), then promotions, then two
 * killer moves per ply (quiet moves that last caused a cutoff at that ply). Positions are
 * scored by material and piece-square tables. Checkmate, stalemate, the fifty-move rule and
 * threefold repetition are scored from [ChessSimulator.calculateRewards], so the agent agrees
 * with the rules: a win is worth more the sooner it comes, and a draw is worth 0.
 *
 * The search stops at [maxDepth] plies, after [timeLimitMillis] (0 for no limit), or when
 * [isCancelled] returns true, whichever comes first. It is polled from the searching thread, so
 * another thread can stop a search by setting a flag it reads. The move comes from the last
 * depth searched in full; if not even depth 1 finished, it is the best move found so far, or
 * the first legal move.
 *
 * When [marginCentipawns] and [temperatureCentipawns] are above 0, the agent does not always
 * play its best move. It picks among the root moves scored within [marginCentipawns] of the
 * best, with probability proportional to `exp((score - best) / temperatureCentipawns)`. A
 * forced mate is always played. [ChessStrength] sets these for each level.
 *
 * The agent keeps no state between calls except [random], so one instance may be reused, but
 * not called from two threads at once unless [random] is thread-safe.
 *
 * @param maxDepth the deepest full-width search, in plies; at least 1
 * @param timeLimitMillis the time budget for one move in milliseconds, or 0 for none
 * @param marginCentipawns how far below the best score a move may be and still be picked
 * @param temperatureCentipawns how evenly moves within the margin are picked; 0 picks the best
 * @param random the source of randomness for picking among moves within the margin
 * @param isCancelled polled during the search; returning true stops it
 */
class ChessAlphaBetaAgent(
    val maxDepth: Int = ChessStrength.EXPERT.maxDepth,
    val timeLimitMillis: Long = ChessStrength.EXPERT.timeLimitMillis,
    val marginCentipawns: Int = 0,
    val temperatureCentipawns: Double = 0.0,
    private val random: Random = Random,
    private val isCancelled: () -> Boolean = { false }
) : Agent {

    init {
        require(maxDepth >= 1) { "maxDepth must be at least 1, was $maxDepth" }
        require(timeLimitMillis >= 0) { "timeLimitMillis must not be negative, was $timeLimitMillis" }
        require(marginCentipawns >= 0) { "marginCentipawns must not be negative, was $marginCentipawns" }
        require(temperatureCentipawns >= 0.0) {
            "temperatureCentipawns must not be negative, was $temperatureCentipawns"
        }
    }

    /** An agent with the depth, time and randomness of [strength]. */
    constructor(
        strength: ChessStrength,
        random: Random = Random,
        isCancelled: () -> Boolean = { false }
    ) : this(
        strength.maxDepth,
        strength.timeLimitMillis,
        strength.marginCentipawns,
        strength.temperatureCentipawns,
        random,
        isCancelled
    )

    /**
     * Picks a move for [playerId], or null when it is not that player's turn or the game is
     * over.
     *
     * @throws IllegalArgumentException if [state] is not a [ChessState] or [simulator] is not a
     *         [ChessSimulator]
     */
    override fun <S : State<S>, A : Action> selectAction(playerId: Int, state: S, simulator: Simulator<S, A>): A? {
        require(state is ChessState) { "${javaClass.simpleName} plays chess only, got ${state::class.simpleName}" }
        require(simulator is ChessSimulator) {
            "${javaClass.simpleName} plays chess only, got ${simulator::class.simpleName}"
        }
        if (state.agentTurn.toInt() != playerId) return null
        @Suppress("UNCHECKED_CAST")
        return search(state, simulator).move as A?
    }

    /**
     * Searches [state] for the side to move and returns the chosen move with what the search
     * found. The move is null only when the game is over.
     */
    fun search(state: ChessState, simulator: ChessSimulator = ChessSimulator()): ChessSearchResult =
        Search(simulator).run(state)

    private inner class Search(private val simulator: ChessSimulator) {
        private val startNanos = System.nanoTime()
        private val deadlineNanos = if (timeLimitMillis > 0) startNanos + timeLimitMillis * 1_000_000 else 0L
        private var nodes = 0L
        private var aborted = false

        /** Two quiet moves per ply that last caused a cutoff, tried after captures. */
        private val killers = Array(MAX_PLY + 1) { arrayOfNulls<ChessAction>(2) }

        fun run(state: ChessState): ChessSearchResult {
            val rootMoves = orderMoves(state, simulator.legalMoves(state))
            if (rootMoves.isEmpty()) return result(null, 0, 0)
            var completedDepth = 0
            var completedScores = IntArray(0)
            var order = rootMoves
            for (depth in 1..maxDepth) {
                if (depth > 1 && shouldNotStartAnotherDepth()) break
                val scores = IntArray(order.size) { -INFINITY }
                var best = -INFINITY
                for (i in order.indices) {
                    val child = simulator.applyMove(state, order[i])
                    val lowerBound = if (best == -INFINITY) -INFINITY else best - marginCentipawns
                    val score = -negamax(child, depth - 1, -INFINITY, -lowerBound, 1)
                    if (aborted) break
                    scores[i] = score
                    if (score > best) best = score
                }
                if (aborted) {
                    if (completedDepth == 0) return partialResult(order, scores)
                    break
                }
                completedDepth = depth
                // Best first for the next depth; the sort is stable, so ties keep their order.
                val sorted = order.indices.sortedByDescending { scores[it] }
                order = sorted.map { order[it] }
                completedScores = IntArray(sorted.size) { scores[sorted[it]] }
                if (completedScores[0] >= MATE_SCORE - depth) break // the quickest mate is found
            }
            val chosen = choose(completedScores)
            return result(order[chosen], completedScores[chosen], completedDepth)
        }

        private fun shouldNotStartAnotherDepth(): Boolean {
            if (isCancelled()) return true
            if (deadlineNanos == 0L) return false
            // A new depth costs several times the last one, so it would not finish in time.
            return System.nanoTime() - startNanos > (deadlineNanos - startNanos) / 2
        }

        private fun partialResult(order: List<ChessAction>, scores: IntArray): ChessSearchResult {
            var best = 0
            for (i in scores.indices) if (scores[i] > scores[best]) best = i
            return result(order[best], if (scores[best] == -INFINITY) 0 else scores[best], 0)
        }

        /** The index in [scores] (sorted best first) of the move to play. */
        private fun choose(scores: IntArray): Int {
            val best = scores[0]
            if (marginCentipawns == 0 || temperatureCentipawns == 0.0 || best >= MATE_SCORE - MAX_PLY) return 0
            // Moves above best - margin have exact scores; the rest failed low against that bound.
            var count = 0
            while (count < scores.size && scores[count] > best - marginCentipawns) count++
            val weights = DoubleArray(count) { exp((scores[it] - best) / temperatureCentipawns) }
            var pick = random.nextDouble() * weights.sum()
            for (i in 0 until count) {
                pick -= weights[i]
                if (pick < 0) return i
            }
            return count - 1
        }

        private fun result(move: ChessAction?, score: Int, depth: Int) =
            ChessSearchResult(move, score, depth, nodes, (System.nanoTime() - startNanos) / 1_000_000)

        private fun negamax(state: ChessState, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
            if (depth <= 0) return quiesce(state, alpha, beta, ply, 0)
            if (pollAbort()) return 0
            val moves = simulator.legalMoves(state)
            if (moves.isEmpty()) return terminalScore(state, ply)
            var a = alpha
            var best = -INFINITY
            for (move in orderMoves(state, moves, killers.getOrNull(ply))) {
                val score = -negamax(simulator.applyMove(state, move), depth - 1, -beta, -a, ply + 1)
                if (aborted) return 0
                if (score > best) best = score
                if (score > a) a = score
                if (a >= beta) {
                    if (!simulator.isCaptureOrPromotion(state, move)) addKiller(ply, move)
                    break
                }
            }
            return best
        }

        private fun quiesce(state: ChessState, alpha: Int, beta: Int, ply: Int, qDepth: Int): Int {
            if (pollAbort()) return 0
            val inCheck = simulator.isInCheck(state)
            val moves: List<ChessAction>
            var a = alpha
            var best: Int
            if (inCheck) {
                moves = simulator.legalMoves(state)
                if (moves.isEmpty()) return terminalScore(state, ply)
                // Out of check every move is searched, since standing pat could be mate.
                if (qDepth >= MAX_QUIESCENCE_CHECK_DEPTH || ply >= MAX_PLY) return evaluate(state)
                best = -INFINITY
            } else {
                if (simulator.isDrawByRule(state)) return terminalScore(state, ply)
                moves = simulator.legalCapturesAndPromotions(state)
                // A legal capture means a legal move, so only test for stalemate without one.
                if (moves.isEmpty() && !simulator.hasLegalMove(state)) return terminalScore(state, ply)
                best = evaluate(state)
                if (best >= beta || ply >= MAX_PLY) return best
                if (best > a) a = best
            }
            for (move in orderMoves(state, moves)) {
                val score = -quiesce(simulator.applyMove(state, move), -beta, -a, ply + 1, qDepth + 1)
                if (aborted) return 0
                if (score > best) best = score
                if (score > a) a = score
                if (a >= beta) break
            }
            return best
        }

        private fun addKiller(ply: Int, move: ChessAction) {
            val slots = killers.getOrNull(ply) ?: return
            if (slots[0] == move) return
            slots[1] = slots[0]
            slots[0] = move
        }

        /** The score of a finished game for the side to move, from the simulator's rewards. */
        private fun terminalScore(state: ChessState, ply: Int): Int {
            val reward = simulator.calculateRewards(state)[state.agentTurn.toInt()]
            return when {
                reward > 0 -> MATE_SCORE - ply
                reward < 0 -> -(MATE_SCORE - ply)
                else -> 0
            }
        }

        private fun pollAbort(): Boolean {
            nodes++
            if (!aborted && nodes and POLL_INTERVAL_MASK == 0L) {
                aborted = isCancelled() || (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos)
            }
            return aborted
        }
    }

    override fun toString(): String =
        "${javaClass.simpleName}(maxDepth=$maxDepth, timeLimitMillis=$timeLimitMillis, " +
            "marginCentipawns=$marginCentipawns, temperatureCentipawns=$temperatureCentipawns)"

    companion object {
        /** The score of mate now; a mate `n` plies away scores `MATE_SCORE - n`. */
        const val MATE_SCORE = 100_000
        private const val INFINITY = 1_000_000
        private const val MAX_PLY = 128
        private const val MAX_QUIESCENCE_CHECK_DEPTH = 8
        private const val POLL_INTERVAL_MASK = 0x3FFL

        private const val KILLER_KEY = 80

        /**
         * Captures first by MVV-LVA, then promotions, then [killers], then the rest in
         * generation order.
         */
        internal fun orderMoves(
            state: ChessState,
            moves: List<ChessAction>,
            killers: Array<ChessAction?>? = null
        ): List<ChessAction> {
            if (moves.size < 2) return moves
            val keys = IntArray(moves.size) {
                val move = moves[it]
                var key = moveOrderKey(state, move)
                if (key == 0 && killers != null) {
                    if (move == killers[0]) key = KILLER_KEY else if (move == killers[1]) key = KILLER_KEY - 1
                }
                key
            }
            return moves.indices.sortedByDescending { keys[it] }.map { moves[it] }
        }

        private fun moveOrderKey(state: ChessState, move: ChessAction): Int {
            val attacker = ChessState.type(state.get(move.fromX, move.fromY))
            var victim = ChessState.type(state.get(move.toX, move.toY))
            if (victim == 0 && attacker == ChessState.PAWN && move.fromX != move.toX) victim = ChessState.PAWN
            var key = 0
            if (victim != 0) key += 100 + 10 * PIECE_VALUES[victim] / 100 - attacker
            if (move.promotion != 0) key += PIECE_VALUES[kotlin.math.abs(move.promotion)] / 10
            return key
        }

        /** Material plus piece-square tables, in centipawns, for the side to move. */
        internal fun evaluate(state: ChessState): Int {
            var nonPawnMaterial = 0
            for (x in 0 until ChessState.SIZE) for (y in 0 until ChessState.SIZE) {
                val type = ChessState.type(state.get(x, y))
                if (type != ChessState.PAWN && type != ChessState.KING) nonPawnMaterial += PIECE_VALUES[type]
            }
            val endgame = nonPawnMaterial <= ENDGAME_NON_PAWN_MATERIAL
            var score = 0
            for (x in 0 until ChessState.SIZE) for (y in 0 until ChessState.SIZE) {
                val piece = state.get(x, y)
                if (piece == ChessState.EMPTY) continue
                val type = ChessState.type(piece)
                val white = ChessState.isWhite(piece)
                // Tables are written from white's side with rank 8 first.
                val index = if (white) (7 - y) * 8 + x else y * 8 + x
                val table = if (type == ChessState.KING && endgame) KING_ENDGAME_TABLE else PIECE_TABLES[type]
                val value = PIECE_VALUES[type] + table[index]
                score += if (white) value else -value
            }
            return if (state.agentTurn == ChessState.TURN_WHITE) score else -score
        }

        private const val ROOK_VALUE = 500
        private const val BISHOP_VALUE = 330
        private const val ENDGAME_NON_PAWN_MATERIAL = 2 * (ROOK_VALUE + BISHOP_VALUE)

        /** Indexed by piece type; the king's value is 0 because it is never captured. */
        private val PIECE_VALUES = intArrayOf(0, 100, 320, BISHOP_VALUE, ROOK_VALUE, 900, 0)

        // Piece-square tables from Tomasz Michniewski's Simplified Evaluation Function.
        private val PAWN_TABLE = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
        )
        private val KNIGHT_TABLE = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
        )
        private val BISHOP_TABLE = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
        )
        private val ROOK_TABLE = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0
        )
        private val QUEEN_TABLE = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20
        )
        private val KING_TABLE = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20
        )
        private val KING_ENDGAME_TABLE = intArrayOf(
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50
        )
        private val PIECE_TABLES = arrayOf(
            IntArray(64), PAWN_TABLE, KNIGHT_TABLE, BISHOP_TABLE, ROOK_TABLE, QUEEN_TABLE, KING_TABLE
        )
    }
}

/**
 * What one [ChessAlphaBetaAgent.search] found.
 *
 * @property move the move chosen, or null when the game is over
 * @property score the move's score in centipawns for the side to move; a mate `n` plies away
 *           scores `MATE_SCORE - n`, and being mated `-(MATE_SCORE - n)`
 * @property depth the deepest search that finished; 0 if it was stopped before depth 1 finished
 * @property nodes the positions visited, quiescence included
 * @property elapsedMillis the wall-clock time the search took
 */
data class ChessSearchResult(
    val move: ChessAction?,
    val score: Int,
    val depth: Int,
    val nodes: Long,
    val elapsedMillis: Long
)

/**
 * Preset strengths for [ChessAlphaBetaAgent], weakest first. Each sets a depth, a time limit,
 * and how far from its best move the agent may stray (see [ChessAlphaBetaAgent]).
 */
enum class ChessStrength(
    val maxDepth: Int,
    val timeLimitMillis: Long,
    val marginCentipawns: Int,
    val temperatureCentipawns: Double
) {
    BEGINNER(1, 500, 300, 150.0),
    EASY(2, 1000, 150, 60.0),
    MEDIUM(3, 2000, 50, 20.0),
    HARD(4, 3000, 0, 0.0),
    EXPERT(8, 5000, 0, 0.0)
}
