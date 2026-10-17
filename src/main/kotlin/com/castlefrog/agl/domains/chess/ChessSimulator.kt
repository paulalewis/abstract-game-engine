package com.castlefrog.agl.domains.chess

import com.castlefrog.agl.Simulator
import com.castlefrog.agl.domains.AdversarialRewards
import com.castlefrog.agl.domains.nextPlayerTurnSequential
import com.castlefrog.agl.requireLegalAction
import kotlin.math.abs

/**
 * Standard chess with castling, en passant, promotion, check, checkmate, and stalemate.
 * Draws by stalemate, the 50-move rule, or threefold repetition yield neutral rewards.
 *
 * The 50-move rule and threefold repetition are automatic: the game ends with no legal
 * actions as soon as [ChessState.halfmoveClock] reaches 100 or the current position has
 * occurred for the third time (see [ChessState.positionHistory]). Under the Laws of Chess
 * both are draws a player must claim, but a simulator has no way to claim. Checkmate and
 * stalemate are checked first, so a mating move wins even if it also completes a repetition.
 *
 * Player 0 is white, player 1 is black.
 */
class ChessSimulator : Simulator<ChessState, ChessAction> {

    override val initialState: ChessState
        get() = ChessState()

    override fun numberOfPlayers(): Int = NUMBER_OF_PLAYERS

    override fun calculateRewards(state: ChessState): IntArray {
        val legal = generateLegalActions(state)
        if (legal.isEmpty()) {
            // No legal moves: checkmate if in check, otherwise stalemate.
            // Mate ends the game immediately; the 50-move rule does not un-mate.
            return if (isInCheck(state, state.agentTurn.toInt())) {
                // Side to move loses → opponent wins.
                if (state.agentTurn.toInt() == 0) {
                    AdversarialRewards.secondPlayerWins()
                } else {
                    AdversarialRewards.firstPlayerWins()
                }
            } else {
                AdversarialRewards.neutral()
            }
        }
        // Draws by the 50-move rule or threefold repetition, like any other non-mate, are neutral.
        return AdversarialRewards.neutral()
    }

    override fun calculateLegalActions(state: ChessState): List<Set<ChessAction>> {
        val legal = listOf(mutableSetOf<ChessAction>(), mutableSetOf())
        val moves = generateLegalActions(state)
        // Checkmate/stalemate first: mate ends the game immediately.
        if (moves.isEmpty()) {
            return legal
        }
        if (state.halfmoveClock >= 100 || isThreefoldRepetition(state)) {
            return legal
        }
        legal[state.agentTurn.toInt()].addAll(moves)
        return legal
    }

    override fun stateTransition(state: ChessState, actions: List<ChessAction?>): ChessState {
        val agentTurn = state.agentTurn.toInt()
        val legalActions = calculateLegalActions(state)
        val action = requireLegalAction(actions, agentTurn, legalActions[agentTurn], state)
        return applyMove(state, action)
    }

    /**
     * True when the current position of [state] has occurred for at least the third time
     * since the last pawn move or capture.
     */
    internal fun isThreefoldRepetition(state: ChessState): Boolean {
        if (state.positionHistory.size < 2) return false
        val key = positionKey(state)
        return state.positionHistory.count { it == key } >= 2
    }

    /**
     * Key identifying a position for repetition (Laws of Chess, article 9.2): the piece on
     * each square, the side to move, castling rights, and the en passant file. The en passant
     * file counts only when a legal en passant capture exists, so a two-square pawn move that
     * no pawn can capture does not make the position differ. The halfmove clock is not part
     * of the key.
     */
    internal fun positionKey(state: ChessState): String {
        val sb = StringBuilder(ChessState.SIZE * ChessState.SIZE + 8)
        for (x in 0 until ChessState.SIZE) {
            for (y in 0 until ChessState.SIZE) {
                sb.append('0' + (state.get(x, y) + ChessState.KING))
            }
        }
        sb.append('/').append(state.agentTurn.toInt())
        sb.append('/').append(state.castlingRights)
        sb.append('/').append(if (hasLegalEnPassant(state)) state.enPassantX else -1)
        return sb.toString()
    }

    private fun hasLegalEnPassant(state: ChessState): Boolean {
        val epX = state.enPassantX
        if (epX < 0) return false
        val turn = state.agentTurn.toInt()
        val pawn = if (turn == 0) ChessState.PAWN.toByte() else (-ChessState.PAWN).toByte()
        val fromY = if (turn == 0) 4 else 3
        val toY = if (turn == 0) 5 else 2
        for (dx in intArrayOf(-1, 1)) {
            val fromX = epX + dx
            if (!state.isOnBoard(fromX, fromY) || state.get(fromX, fromY) != pawn) continue
            if (state.get(epX, toY) != ChessState.EMPTY) continue
            if (!isInCheck(movePieces(state, ChessAction(fromX, fromY, epX, toY)), turn)) return true
        }
        return false
    }

    private fun generateLegalActions(state: ChessState): Set<ChessAction> {
        val turn = state.agentTurn.toInt()
        val pseudo = generatePseudoLegal(state, turn)
        val legal = mutableSetOf<ChessAction>()
        for (move in pseudo) {
            val next = movePieces(state, move)
            if (!isInCheck(next, turn)) {
                legal.add(move)
            }
        }
        return legal
    }

    private fun generatePseudoLegal(state: ChessState, turn: Int): List<ChessAction> {
        val moves = ArrayList<ChessAction>()
        for (x in 0 until ChessState.SIZE) {
            for (y in 0 until ChessState.SIZE) {
                val p = state.get(x, y)
                if (!state.pieceBelongsToSide(p, turn)) continue
                when (ChessState.type(p)) {
                    ChessState.PAWN -> addPawnMoves(state, x, y, turn, moves)
                    ChessState.KNIGHT -> addKnightMoves(state, x, y, turn, moves)
                    ChessState.BISHOP -> addSliding(state, x, y, turn, BISHOP_DIRS, moves)
                    ChessState.ROOK -> addSliding(state, x, y, turn, ROOK_DIRS, moves)
                    ChessState.QUEEN -> addSliding(state, x, y, turn, QUEEN_DIRS, moves)
                    ChessState.KING -> addKingMoves(state, x, y, turn, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(
        state: ChessState,
        x: Int,
        y: Int,
        turn: Int,
        moves: MutableList<ChessAction>
    ) {
        val dir = if (turn == 0) 1 else -1
        val startRank = if (turn == 0) 1 else 6
        val promoRank = if (turn == 0) 7 else 0
        val ny = y + dir
        if (state.isOnBoard(x, ny) && state.get(x, ny) == ChessState.EMPTY) {
            addPawnArrival(x, y, x, ny, promoRank, turn, moves)
            if (y == startRank) {
                val n2 = y + 2 * dir
                if (state.get(x, n2) == ChessState.EMPTY) {
                    moves.add(ChessAction(x, y, x, n2))
                }
            }
        }
        for (dx in intArrayOf(-1, 1)) {
            val nx = x + dx
            if (!state.isOnBoard(nx, ny)) continue
            val target = state.get(nx, ny)
            if (target != ChessState.EMPTY && state.pieceBelongsToSide(target, 1 - turn)) {
                addPawnArrival(x, y, nx, ny, promoRank, turn, moves)
            }
            // En passant
            if (target == ChessState.EMPTY && state.enPassantX == nx) {
                val epRank = if (turn == 0) 5 else 2
                if (ny == epRank) {
                    moves.add(ChessAction(x, y, nx, ny))
                }
            }
        }
    }

    private fun addPawnArrival(
        fx: Int,
        fy: Int,
        tx: Int,
        ty: Int,
        promoRank: Int,
        turn: Int,
        moves: MutableList<ChessAction>
    ) {
        if (ty == promoRank) {
            val sign = if (turn == 0) 1 else -1
            for (t in intArrayOf(ChessState.QUEEN, ChessState.ROOK, ChessState.BISHOP, ChessState.KNIGHT)) {
                moves.add(ChessAction(fx, fy, tx, ty, sign * t))
            }
        } else {
            moves.add(ChessAction(fx, fy, tx, ty))
        }
    }

    private fun addKnightMoves(
        state: ChessState,
        x: Int,
        y: Int,
        turn: Int,
        moves: MutableList<ChessAction>
    ) {
        for ((dx, dy) in KNIGHT_DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (!state.isOnBoard(nx, ny)) continue
            val t = state.get(nx, ny)
            if (t == ChessState.EMPTY || state.pieceBelongsToSide(t, 1 - turn)) {
                moves.add(ChessAction(x, y, nx, ny))
            }
        }
    }

    private fun addSliding(
        state: ChessState,
        x: Int,
        y: Int,
        turn: Int,
        dirs: List<Pair<Int, Int>>,
        moves: MutableList<ChessAction>
    ) {
        for ((dx, dy) in dirs) {
            var nx = x + dx
            var ny = y + dy
            while (state.isOnBoard(nx, ny)) {
                val t = state.get(nx, ny)
                if (t == ChessState.EMPTY) {
                    moves.add(ChessAction(x, y, nx, ny))
                } else {
                    if (state.pieceBelongsToSide(t, 1 - turn)) {
                        moves.add(ChessAction(x, y, nx, ny))
                    }
                    break
                }
                nx += dx
                ny += dy
            }
        }
    }

    private fun addKingMoves(
        state: ChessState,
        x: Int,
        y: Int,
        turn: Int,
        moves: MutableList<ChessAction>
    ) {
        for ((dx, dy) in QUEEN_DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (!state.isOnBoard(nx, ny)) continue
            val t = state.get(nx, ny)
            if (t == ChessState.EMPTY || state.pieceBelongsToSide(t, 1 - turn)) {
                moves.add(ChessAction(x, y, nx, ny))
            }
        }
        // Castling
        if (isInCheck(state, turn)) return
        val rank = if (turn == 0) 0 else 7
        if (x != 4 || y != rank) return
        val kingSideBit = if (turn == 0) 0b0001 else 0b0100
        val queenSideBit = if (turn == 0) 0b0010 else 0b1000
        if (state.castlingRights and kingSideBit != 0) {
            if (state.get(5, rank) == ChessState.EMPTY && state.get(6, rank) == ChessState.EMPTY) {
                if (!isSquareAttacked(state, 5, rank, 1 - turn) && !isSquareAttacked(state, 6, rank, 1 - turn)) {
                    moves.add(ChessAction(4, rank, 6, rank))
                }
            }
        }
        if (state.castlingRights and queenSideBit != 0) {
            if (state.get(1, rank) == ChessState.EMPTY &&
                state.get(2, rank) == ChessState.EMPTY &&
                state.get(3, rank) == ChessState.EMPTY
            ) {
                if (!isSquareAttacked(state, 3, rank, 1 - turn) && !isSquareAttacked(state, 2, rank, 1 - turn)) {
                    moves.add(ChessAction(4, rank, 2, rank))
                }
            }
        }
    }

    /**
     * Plays [action] on a copy of [state], including the repetition bookkeeping in
     * [ChessState.positionHistory].
     */
    internal fun applyMove(state: ChessState, action: ChessAction): ChessState {
        val next = movePieces(state, action)
        if (next.halfmoveClock != 0) {
            next.positionHistory.add(positionKey(state))
        }
        return next
    }

    /**
     * Plays [action] on a copy of [state] without the repetition bookkeeping, which is all
     * that legality checks need. The pawn move or capture case still clears
     * [ChessState.positionHistory], since those positions can no longer recur.
     */
    private fun movePieces(state: ChessState, action: ChessAction): ChessState {
        val next = state.copy()
        val turn = next.agentTurn.toInt()
        val piece = next.get(action.fromX, action.fromY)
        var captured = next.get(action.toX, action.toY)
        next.set(action.fromX, action.fromY, ChessState.EMPTY)

        // En passant capture
        if (ChessState.type(piece) == ChessState.PAWN &&
            action.fromX != action.toX &&
            captured == ChessState.EMPTY
        ) {
            val capY = action.fromY
            captured = next.get(action.toX, capY)
            next.set(action.toX, capY, ChessState.EMPTY)
        }

        // Castling rook move
        if (ChessState.type(piece) == ChessState.KING && abs(action.toX - action.fromX) == 2) {
            val rank = action.fromY
            if (action.toX == 6) {
                next.set(7, rank, ChessState.EMPTY)
                next.set(5, rank, if (turn == 0) ChessState.ROOK.toByte() else (-ChessState.ROOK).toByte())
            } else if (action.toX == 2) {
                next.set(0, rank, ChessState.EMPTY)
                next.set(3, rank, if (turn == 0) ChessState.ROOK.toByte() else (-ChessState.ROOK).toByte())
            }
        }

        val placed = if (action.promotion != 0) action.promotion.toByte() else piece
        next.set(action.toX, action.toY, placed)

        // Update castling rights
        var rights = next.castlingRights
        if (ChessState.type(piece) == ChessState.KING) {
            rights = if (turn == 0) rights and 0b1100 else rights and 0b0011
        }
        if (ChessState.type(piece) == ChessState.ROOK) {
            if (action.fromX == 0 && action.fromY == 0) rights = rights and 0b1101
            if (action.fromX == 7 && action.fromY == 0) rights = rights and 0b1110
            if (action.fromX == 0 && action.fromY == 7) rights = rights and 0b0111
            if (action.fromX == 7 && action.fromY == 7) rights = rights and 0b1011
        }
        if (captured != ChessState.EMPTY && ChessState.type(captured) == ChessState.ROOK) {
            if (action.toX == 0 && action.toY == 0) rights = rights and 0b1101
            if (action.toX == 7 && action.toY == 0) rights = rights and 0b1110
            if (action.toX == 0 && action.toY == 7) rights = rights and 0b0111
            if (action.toX == 7 && action.toY == 7) rights = rights and 0b1011
        }
        next.castlingRights = rights

        // En passant target
        next.enPassantX = if (ChessState.type(piece) == ChessState.PAWN && abs(action.toY - action.fromY) == 2) {
            action.fromX
        } else {
            -1
        }

        if (ChessState.type(piece) == ChessState.PAWN || captured != ChessState.EMPTY) {
            next.halfmoveClock = 0
            next.positionHistory.clear()
        } else {
            next.halfmoveClock += 1
        }

        next.agentTurn = nextPlayerTurnSequential(turn, NUMBER_OF_PLAYERS).toByte()
        return next
    }

    internal fun isInCheck(state: ChessState, turn: Int): Boolean {
        val king = findKing(state, turn) ?: return false
        return isSquareAttacked(state, king.first, king.second, 1 - turn)
    }

    private fun findKing(state: ChessState, turn: Int): Pair<Int, Int>? {
        val want = if (turn == 0) ChessState.KING.toByte() else (-ChessState.KING).toByte()
        for (x in 0 until ChessState.SIZE) {
            for (y in 0 until ChessState.SIZE) {
                if (state.get(x, y) == want) return x to y
            }
        }
        return null
    }

    private fun isSquareAttacked(state: ChessState, x: Int, y: Int, byTurn: Int): Boolean {
        // Pawns
        val pawnDir = if (byTurn == 0) 1 else -1
        for (dx in intArrayOf(-1, 1)) {
            val px = x + dx
            val py = y - pawnDir // square from which a pawn of byTurn would attack (x,y)
            if (state.isOnBoard(px, py)) {
                val p = state.get(px, py)
                if (state.pieceBelongsToSide(p, byTurn) && ChessState.type(p) == ChessState.PAWN) {
                    return true
                }
            }
        }
        // Knights
        for ((dx, dy) in KNIGHT_DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (!state.isOnBoard(nx, ny)) continue
            val p = state.get(nx, ny)
            if (state.pieceBelongsToSide(p, byTurn) && ChessState.type(p) == ChessState.KNIGHT) return true
        }
        // King
        for ((dx, dy) in QUEEN_DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (!state.isOnBoard(nx, ny)) continue
            val p = state.get(nx, ny)
            if (state.pieceBelongsToSide(p, byTurn) && ChessState.type(p) == ChessState.KING) return true
        }
        // Sliding
        if (slidingAttack(state, x, y, byTurn, BISHOP_DIRS, setOf(ChessState.BISHOP, ChessState.QUEEN))) return true
        if (slidingAttack(state, x, y, byTurn, ROOK_DIRS, setOf(ChessState.ROOK, ChessState.QUEEN))) return true
        return false
    }

    private fun slidingAttack(
        state: ChessState,
        x: Int,
        y: Int,
        byTurn: Int,
        dirs: List<Pair<Int, Int>>,
        types: Set<Int>
    ): Boolean {
        for ((dx, dy) in dirs) {
            var nx = x + dx
            var ny = y + dy
            while (state.isOnBoard(nx, ny)) {
                val p = state.get(nx, ny)
                if (p != ChessState.EMPTY) {
                    if (state.pieceBelongsToSide(p, byTurn) && ChessState.type(p) in types) return true
                    break
                }
                nx += dx
                ny += dy
            }
        }
        return false
    }

    companion object {
        private const val NUMBER_OF_PLAYERS = 2
        private val KNIGHT_DIRS = listOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
        )
        private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val QUEEN_DIRS = BISHOP_DIRS + ROOK_DIRS
    }
}
