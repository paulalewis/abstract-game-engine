package com.castlefrog.agl.domains.go

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.agents.PlayoutPolicy
import kotlin.random.Random

/**
 * A fast [PlayoutPolicy] for [GoSimulator], following the stopping rule in doc/decisions.md.
 *
 * The mover plays a random empty point that is not one of its own single-point eyes
 * ([isOwnEye]) and is legal, and passes only when no such point is left. It does not
 * generate every legal action: it tries empty points in random order and checks each with
 * an exact legality test that looks only at the groups next to the point, plus one
 * positional superko scan, instead of building a trial board.
 *
 * With [preferTactics] on, the mover first plays a legal capture of an opponent group in
 * atari if there is one, then a legal move that extends one of its own groups in atari to
 * at least two liberties, both picked at random. Every move it plays is legal by the
 * simulator's rules, whatever the order.
 *
 * Use it with [GoAreaEvaluator] and a [com.castlefrog.agl.agents.MctsConfig.maxPlayoutLength]
 * of [moveCap], so playouts that reach the cap are scored by area rather than as a draw.
 * It only works with a [GoSimulator] and [GoState]; any other domain throws
 * [IllegalArgumentException].
 */
class GoPlayoutPolicy(val preferTactics: Boolean = true) : PlayoutPolicy {

    override fun <S : State<S>, A : Action> selectActions(
        state: S,
        simulator: Simulator<S, A>,
        random: Random
    ): List<A?>? {
        require(simulator is GoSimulator && state is GoState) { "GoPlayoutPolicy plays only Go" }
        val action = selectAction(state, simulator, random) ?: return null
        val turn = state.agentTurn.toInt()
        @Suppress("UNCHECKED_CAST")
        return List(2) { if (it == turn) action else null } as List<A?>
    }

    /** The mover's playout move in [state], or null if the game is over. */
    fun selectAction(state: GoState, simulator: GoSimulator, random: Random): GoAction? {
        if (state.consecutivePasses >= 2) {
            return null
        }
        val board = GoBoard(state, simulator)
        if (preferTactics) {
            board.tacticalMove(random)?.let { return it }
        }
        return board.randomMove(random) ?: GoAction.pass()
    }

    companion object {

        /** The ply cap that backs up the stopping rule: three plies per board point. */
        fun moveCap(boardSize: Int): Int = 3 * boardSize * boardSize

        /**
         * True if ([x], [y]) is a single-point eye of [stone]: an empty point whose on-board
         * orthogonal neighbours are all [stone], and whose diagonal neighbours hold at most
         * one opponent stone in the middle of the board, or none on the edge or in a corner.
         * The diagonal test leaves a false eye fillable, so a group that needs that point to
         * connect can still be saved.
         */
        fun isOwnEye(state: GoState, x: Int, y: Int, stone: Byte): Boolean {
            if (state.get(x, y) != GoState.LOCATION_EMPTY) return false
            val n = state.boardSize
            if (x > 0 && state.get(x - 1, y) != stone) return false
            if (x < n - 1 && state.get(x + 1, y) != stone) return false
            if (y > 0 && state.get(x, y - 1) != stone) return false
            if (y < n - 1 && state.get(x, y + 1) != stone) return false
            val opponent = if (stone == GoState.LOCATION_BLACK) GoState.LOCATION_WHITE else GoState.LOCATION_BLACK
            var offBoard = 0
            var opponentDiagonals = 0
            for (dx in DIAGONAL_STEPS) {
                for (dy in DIAGONAL_STEPS) {
                    val nx = x + dx
                    val ny = y + dy
                    if (!state.isOnBoard(nx, ny)) {
                        offBoard += 1
                    } else if (state.get(nx, ny) == opponent) {
                        opponentDiagonals += 1
                    }
                }
            }
            return if (offBoard > 0) opponentDiagonals == 0 else opponentDiagonals <= 1
        }

        private val DIAGONAL_STEPS = intArrayOf(-1, 1)
    }
}
