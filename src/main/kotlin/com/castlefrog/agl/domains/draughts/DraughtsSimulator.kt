package com.castlefrog.agl.domains.draughts

import com.castlefrog.agl.Simulator
import com.castlefrog.agl.domains.AdversarialRewards
import com.castlefrog.agl.domains.nextPlayerTurnSequential
import com.castlefrog.agl.requireLegalAction

/**
 * International draughts: 10×10 board, men move forward and capture in all
 * four diagonal directions, kings fly any distance, captures are mandatory,
 * the longest capture sequence must be taken, and jumped pieces stay on the
 * board until the sequence ends. Multi-jumps continue on the same turn via
 * [DraughtsState.mustContinueFromX]. A man promotes only when its move ends
 * on the last rank.
 *
 * A player with no legal move loses. Otherwise the game is an automatic draw
 * (neutral rewards, no legal actions) when any of these FMJD article 6 rules holds:
 *
 * - Threefold repetition (6.2): the same pieces on the same squares, with the same
 *   player to move, occur for the third time. Only positions since the last man
 *   move or capture count; see [DraughtsState.positionHistory].
 * - Twenty-five king moves (6.3): each player has made 25 moves in a row that were
 *   king moves without a capture (50 plies); see [DraughtsState.kingMoveCount].
 * - Endgame limits (6.4, 6.5): three kings, two kings and a man, or a king and two
 *   men against one king draws after 16 moves each; two kings, a king and a man, or
 *   one king against one king draws after 5 moves each. Moves are counted from when
 *   that material is first reached, and the count restarts when the material
 *   changes from the first group to the second; see [DraughtsState.endgameGroup].
 *
 * The no-move loss is checked first, so a move that leaves the opponent without a
 * move wins even if it also completes a draw. Moves are counted when a turn ends: a
 * multi-jump is one move, and a position in the middle of one never counts.
 */
class DraughtsSimulator : Simulator<DraughtsState, DraughtsAction> {

    override val initialState: DraughtsState
        get() = DraughtsState()

    override fun numberOfPlayers(): Int = NUMBER_OF_PLAYERS

    override fun calculateRewards(state: DraughtsState): IntArray {
        val turn = state.agentTurn.toInt()
        if (collectMoves(state).isNotEmpty()) {
            // Still playing, or an automatic draw.
            return AdversarialRewards.neutral()
        }
        // Side to move has no moves — loses (opponent wins), even if a draw rule also holds.
        return if (turn == 0) AdversarialRewards.secondPlayerWins() else AdversarialRewards.firstPlayerWins()
    }

    override fun calculateLegalActions(state: DraughtsState): List<Set<DraughtsAction>> {
        val legal = listOf(mutableSetOf<DraughtsAction>(), mutableSetOf())
        if (isDraw(state)) {
            return legal
        }
        legal[state.agentTurn.toInt()].addAll(collectMoves(state))
        return legal
    }

    override fun stateTransition(state: DraughtsState, actions: List<DraughtsAction?>): DraughtsState {
        val agentTurn = state.agentTurn.toInt()
        val legalActions = calculateLegalActions(state)
        val action = requireLegalAction(actions, agentTurn, legalActions[agentTurn], state)
        val next = state.copy()
        val piece = next.get(action.fromX, action.fromY)
        next.set(action.fromX, action.fromY, DraughtsState.EMPTY)
        next.set(action.toX, action.toY, piece)

        val captured = findCapturedPiece(next, action.fromX, action.fromY, action.toX, action.toY)
        if (captured != null) {
            next.pendingCaptures.add(DraughtsState.captureKey(captured.first, captured.second))
            next.mustContinueFromX = action.toX
            next.mustContinueFromY = action.toY
            if (collectCaptures(next).isNotEmpty()) {
                return next
            }
            clearPendingCaptures(next)
        }

        promoteIfOnLastRank(next, action.toX, action.toY)
        next.mustContinueFromX = -1
        next.mustContinueFromY = -1
        next.agentTurn = nextPlayerTurnSequential(agentTurn, NUMBER_OF_PLAYERS).toByte()
        updateDrawCounters(state, next, isIrreversible = captured != null || !state.isKing(piece))
        return next
    }

    private fun collectMoves(state: DraughtsState): Set<DraughtsAction> {
        val captures = collectCaptures(state)
        return if (captures.isNotEmpty()) captures else collectQuietMoves(state)
    }

    /** Whether a draw rule ends the game. Never true in the middle of a multi-jump. */
    private fun isDraw(state: DraughtsState): Boolean {
        if (state.mustContinueFromX >= 0) return false
        val key = state.positionKey()
        if (state.positionHistory.count { it == key } >= REPETITIONS_FOR_DRAW - 1) return true
        if (state.kingMoveCount >= KING_MOVE_PLIES_FOR_DRAW) return true
        return when (state.endgameGroup) {
            DraughtsState.ENDGAME_SIXTEEN -> state.endgameMoveCount >= ENDGAME_SIXTEEN_PLIES_FOR_DRAW
            DraughtsState.ENDGAME_FIVE -> state.endgameMoveCount >= ENDGAME_FIVE_PLIES_FOR_DRAW
            else -> false
        }
    }

    /** Called once per turn, when [previous] was the position at the start of the turn just ended. */
    private fun updateDrawCounters(previous: DraughtsState, next: DraughtsState, isIrreversible: Boolean) {
        if (isIrreversible) {
            next.positionHistory.clear()
            next.kingMoveCount = 0
        } else {
            next.positionHistory.add(previous.positionKey())
            next.kingMoveCount = previous.kingMoveCount + 1
        }
        val group = endgameGroup(next)
        if (group != previous.endgameGroup) {
            next.endgameGroup = group
            next.endgameMoveCount = 0
        } else if (group != DraughtsState.ENDGAME_NONE) {
            next.endgameMoveCount = previous.endgameMoveCount + 1
        }
    }

    private fun endgameGroup(state: DraughtsState): Byte {
        var whiteMen = 0
        var whiteKings = 0
        var blackMen = 0
        var blackKings = 0
        for (x in 0 until DraughtsState.SIZE) {
            for (y in 0 until DraughtsState.SIZE) {
                when (state.get(x, y)) {
                    DraughtsState.WHITE_MAN -> whiteMen++
                    DraughtsState.WHITE_KING -> whiteKings++
                    DraughtsState.BLACK_MAN -> blackMen++
                    DraughtsState.BLACK_KING -> blackKings++
                }
            }
        }
        return when {
            blackKings == 1 && blackMen == 0 -> endgameGroupAgainstLoneKing(whiteKings, whiteMen)
            whiteKings == 1 && whiteMen == 0 -> endgameGroupAgainstLoneKing(blackKings, blackMen)
            else -> DraughtsState.ENDGAME_NONE
        }
    }

    private fun endgameGroupAgainstLoneKing(kings: Int, men: Int): Byte {
        if (kings < 1) return DraughtsState.ENDGAME_NONE
        return when (kings + men) {
            1, 2 -> DraughtsState.ENDGAME_FIVE
            3 -> DraughtsState.ENDGAME_SIXTEEN
            else -> DraughtsState.ENDGAME_NONE
        }
    }

    private fun collectCaptures(state: DraughtsState): Set<DraughtsAction> {
        var bestLen = 0
        val best = mutableSetOf<DraughtsAction>()
        forEachOwnPiece(state) { x, y, piece ->
            for (jump in generateJumps(state, x, y, piece)) {
                val captured = state.pendingCaptures.toMutableSet()
                captured.add(DraughtsState.captureKey(jump.capX, jump.capY))
                val len = 1 + maxCaptureLength(state, jump.toX, jump.toY, piece, captured, x, y)
                if (len > bestLen) {
                    bestLen = len
                    best.clear()
                    best.add(jump.action)
                } else if (len == bestLen) {
                    best.add(jump.action)
                }
            }
        }
        return best
    }

    private fun collectQuietMoves(state: DraughtsState): Set<DraughtsAction> {
        val result = mutableSetOf<DraughtsAction>()
        forEachOwnPiece(state) { x, y, piece ->
            if (state.isKing(piece)) {
                for ((dx, dy) in DIAGONALS) {
                    var tx = x + dx
                    var ty = y + dy
                    while (state.isOnBoard(tx, ty) && state.get(tx, ty) == DraughtsState.EMPTY) {
                        result.add(DraughtsAction(x, y, tx, ty))
                        tx += dx
                        ty += dy
                    }
                }
            } else {
                val dy = if (state.isWhitePiece(piece)) 1 else -1
                for (dx in intArrayOf(-1, 1)) {
                    val tx = x + dx
                    val ty = y + dy
                    if (state.isOnBoard(tx, ty) && state.get(tx, ty) == DraughtsState.EMPTY) {
                        result.add(DraughtsAction(x, y, tx, ty))
                    }
                }
            }
        }
        return result
    }

    private fun maxCaptureLength(
        state: DraughtsState,
        x: Int,
        y: Int,
        piece: Byte,
        captured: Set<Int>,
        originX: Int,
        originY: Int
    ): Int {
        val jumps = generateJumps(state, x, y, piece, captured, originX, originY)
        if (jumps.isEmpty()) return 0
        var best = 0
        for (jump in jumps) {
            val nextCaptured = captured.toMutableSet()
            nextCaptured.add(DraughtsState.captureKey(jump.capX, jump.capY))
            val len = 1 + maxCaptureLength(state, jump.toX, jump.toY, piece, nextCaptured, originX, originY)
            if (len > best) best = len
        }
        return best
    }

    private fun generateJumps(
        state: DraughtsState,
        x: Int,
        y: Int,
        piece: Byte,
        captured: Set<Int> = state.pendingCaptures,
        originX: Int = -1,
        originY: Int = -1
    ): List<Jump> {
        return if (state.isKing(piece)) {
            generateKingJumps(state, x, y, captured, originX, originY)
        } else {
            generateManJumps(state, x, y, captured, originX, originY)
        }
    }

    private fun generateManJumps(
        state: DraughtsState,
        x: Int,
        y: Int,
        captured: Set<Int>,
        originX: Int,
        originY: Int
    ): List<Jump> {
        val jumps = mutableListOf<Jump>()
        val turn = state.agentTurn.toInt()
        for ((dx, dy) in DIAGONALS) {
            val mx = x + dx
            val my = y + dy
            val jx = x + 2 * dx
            val jy = y + 2 * dy
            if (!state.isOnBoard(jx, jy)) continue
            if (!isOpponent(state, mx, my, turn)) continue
            if (captured.contains(DraughtsState.captureKey(mx, my))) continue
            if (!isClearForLanding(state, jx, jy, originX, originY)) continue
            jumps.add(Jump(x, y, jx, jy, mx, my))
        }
        return jumps
    }

    private fun generateKingJumps(
        state: DraughtsState,
        x: Int,
        y: Int,
        captured: Set<Int>,
        originX: Int,
        originY: Int
    ): List<Jump> {
        val jumps = mutableListOf<Jump>()
        val turn = state.agentTurn.toInt()
        for ((dx, dy) in DIAGONALS) {
            var cx = x + dx
            var cy = y + dy
            while (state.isOnBoard(cx, cy) && isClearForLanding(state, cx, cy, originX, originY)) {
                cx += dx
                cy += dy
            }
            if (!state.isOnBoard(cx, cy)) continue
            if (!isOpponent(state, cx, cy, turn)) continue
            if (captured.contains(DraughtsState.captureKey(cx, cy))) continue
            val capX = cx
            val capY = cy
            var lx = cx + dx
            var ly = cy + dy
            while (state.isOnBoard(lx, ly) && isClearForLanding(state, lx, ly, originX, originY)) {
                jumps.add(Jump(x, y, lx, ly, capX, capY))
                lx += dx
                ly += dy
            }
        }
        return jumps
    }

    /** Origin stays occupied on the board during search; treat it as empty so a cycle may land there. */
    private fun isClearForLanding(
        state: DraughtsState,
        x: Int,
        y: Int,
        originX: Int,
        originY: Int
    ): Boolean {
        if (x == originX && y == originY) return true
        return state.get(x, y) == DraughtsState.EMPTY
    }

    private fun forEachOwnPiece(state: DraughtsState, body: (x: Int, y: Int, piece: Byte) -> Unit) {
        val turn = state.agentTurn.toInt()
        val xs: IntRange
        val ys: IntRange
        if (state.mustContinueFromX >= 0) {
            xs = state.mustContinueFromX..state.mustContinueFromX
            ys = state.mustContinueFromY..state.mustContinueFromY
        } else {
            xs = 0 until DraughtsState.SIZE
            ys = 0 until DraughtsState.SIZE
        }
        for (x in xs) {
            for (y in ys) {
                val p = state.get(x, y)
                if (turn == 0 && !state.isWhitePiece(p)) continue
                if (turn == 1 && !state.isBlackPiece(p)) continue
                body(x, y, p)
            }
        }
    }

    private fun isOpponent(state: DraughtsState, x: Int, y: Int, turn: Int): Boolean {
        if (!state.isOnBoard(x, y)) return false
        val p = state.get(x, y)
        return if (turn == 0) state.isBlackPiece(p) else state.isWhitePiece(p)
    }

    private fun findCapturedPiece(
        state: DraughtsState,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Pair<Int, Int>? {
        val dx = sign(toX - fromX)
        val dy = sign(toY - fromY)
        var x = fromX + dx
        var y = fromY + dy
        while (x != toX || y != toY) {
            if (state.get(x, y) != DraughtsState.EMPTY) {
                return x to y
            }
            x += dx
            y += dy
        }
        return null
    }

    private fun clearPendingCaptures(state: DraughtsState) {
        for (key in state.pendingCaptures) {
            val x = key / DraughtsState.SIZE
            val y = key % DraughtsState.SIZE
            state.set(x, y, DraughtsState.EMPTY)
        }
        state.pendingCaptures.clear()
    }

    private fun promoteIfOnLastRank(state: DraughtsState, x: Int, y: Int) {
        val piece = state.get(x, y)
        if (piece == DraughtsState.WHITE_MAN && y == DraughtsState.SIZE - 1) {
            state.set(x, y, DraughtsState.WHITE_KING)
        } else if (piece == DraughtsState.BLACK_MAN && y == 0) {
            state.set(x, y, DraughtsState.BLACK_KING)
        }
    }

    private fun sign(v: Int): Int = when {
        v > 0 -> 1
        v < 0 -> -1
        else -> 0
    }

    private data class Jump(
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        val capX: Int,
        val capY: Int
    ) {
        val action: DraughtsAction get() = DraughtsAction(fromX, fromY, toX, toY)
    }

    companion object {
        private const val NUMBER_OF_PLAYERS = 2
        private const val REPETITIONS_FOR_DRAW = 3
        private const val KING_MOVE_PLIES_FOR_DRAW = 50
        private const val ENDGAME_SIXTEEN_PLIES_FOR_DRAW = 32
        private const val ENDGAME_FIVE_PLIES_FOR_DRAW = 10
        private val DIAGONALS = listOf(-1 to 1, 1 to 1, -1 to -1, 1 to -1)
    }
}
