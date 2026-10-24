package com.castlefrog.agl.benchmark

import com.castlefrog.agl.domains.go.GoAction
import com.castlefrog.agl.domains.go.GoState
import java.util.Random

/**
 * The stopping rule for random Go playouts, recorded in doc/decisions.md.
 *
 * The mover picks uniformly among its legal placements that do not fill one of
 * its own single-point eyes. It passes only when no such placement is left.
 * Without this rule random players fill their own eyes, their groups die, the
 * board refills, and the game does not end.
 *
 * A single-point eye of [stone] is an empty point whose on-board orthogonal
 * neighbours are all [stone], and whose diagonal neighbours hold at most one
 * opponent stone in the middle of the board, or none on the edge or in a corner.
 * The diagonal test keeps a false eye fillable, so a group that needs that
 * point to connect is not left to die.
 */
object GoPlayoutPolicy {

    /** The ply cap that backs up the stopping rule: three plies per board point. */
    fun moveCap(boardSize: Int): Int = 3 * boardSize * boardSize

    fun select(state: GoState, legal: Set<GoAction>, random: Random): GoAction {
        val stone = if (state.agentTurn == GoState.TURN_BLACK) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
        val candidates = ArrayList<GoAction>(legal.size)
        for (action in legal) {
            if (!action.isPass && !isOwnEye(state, action.x, action.y, stone)) {
                candidates.add(action)
            }
        }
        return if (candidates.isEmpty()) GoAction.pass() else candidates[random.nextInt(candidates.size)]
    }

    fun isOwnEye(state: GoState, x: Int, y: Int, stone: Byte): Boolean {
        if (state.get(x, y) != GoState.LOCATION_EMPTY) return false
        for ((dx, dy) in ORTHOGONAL) {
            val nx = x + dx
            val ny = y + dy
            if (state.isOnBoard(nx, ny) && state.get(nx, ny) != stone) return false
        }
        val opponent = if (stone == GoState.LOCATION_BLACK) GoState.LOCATION_WHITE else GoState.LOCATION_BLACK
        var offBoard = 0
        var opponentDiagonals = 0
        for ((dx, dy) in DIAGONAL) {
            val nx = x + dx
            val ny = y + dy
            if (!state.isOnBoard(nx, ny)) {
                offBoard += 1
            } else if (state.get(nx, ny) == opponent) {
                opponentDiagonals += 1
            }
        }
        return if (offBoard > 0) opponentDiagonals == 0 else opponentDiagonals <= 1
    }

    private val ORTHOGONAL = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
    private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
}
