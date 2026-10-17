package com.castlefrog.agl.domains.draughts

import com.castlefrog.agl.State

/**
 * International draughts on a 10×10 board.
 *
 * Pieces occupy dark squares only (`(x + y) % 2 == 0`); the near-left corner
 * for each side is dark. Values: empty 0; man/king for white (side to move
 * first, bottom ranks) and black (top ranks).
 *
 * When [mustContinueFromX] is non-negative, that piece must continue a multi-jump.
 * Squares in [pendingCaptures] still hold the jumped pieces until the sequence ends.
 *
 * The draw rules use [positionHistory], [kingMoveCount], [endgameGroup], and
 * [endgameMoveCount]. They change only when a turn ends, never in the middle of a
 * multi-jump.
 */
class DraughtsState(
    val board: Array<ByteArray> = initialBoard(),
    var agentTurn: Byte = TURN_WHITE,
    var mustContinueFromX: Int = -1,
    var mustContinueFromY: Int = -1,
    val pendingCaptures: MutableSet<Int> = mutableSetOf(),
    /**
     * [positionKey] of each earlier position, at the start of a turn, since the last
     * man move or capture. The current position is not included.
     */
    val positionHistory: MutableList<String> = mutableListOf(),
    /** Plies in a row that were king moves without a capture. */
    var kingMoveCount: Int = 0,
    /** Endgame material group of the board: [ENDGAME_NONE], [ENDGAME_SIXTEEN], or [ENDGAME_FIVE]. */
    var endgameGroup: Byte = ENDGAME_NONE,
    /** Plies played since the board first reached [endgameGroup]. */
    var endgameMoveCount: Int = 0
) : State<DraughtsState> {

    companion object {
        const val SIZE = 10
        const val EMPTY: Byte = 0
        const val WHITE_MAN: Byte = 1
        const val WHITE_KING: Byte = 2
        const val BLACK_MAN: Byte = 3
        const val BLACK_KING: Byte = 4
        const val TURN_WHITE: Byte = 0
        const val TURN_BLACK: Byte = 1

        /** No endgame limit applies. */
        const val ENDGAME_NONE: Byte = 0

        /** Three kings, two kings and a man, or a king and two men, against one king. */
        const val ENDGAME_SIXTEEN: Byte = 1

        /** Two kings, a king and a man, or one king, against one king. */
        const val ENDGAME_FIVE: Byte = 2

        fun initialBoard(): Array<ByteArray> {
            val board = Array(SIZE) { ByteArray(SIZE) }
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    if ((x + y) % 2 != 0) continue // light squares unused
                    when (y) {
                        in 0..3 -> board[x][y] = WHITE_MAN
                        in 6..9 -> board[x][y] = BLACK_MAN
                    }
                }
            }
            return board
        }

        fun isDarkSquare(x: Int, y: Int): Boolean = (x + y) % 2 == 0

        fun captureKey(x: Int, y: Int): Int = x * SIZE + y
    }

    override fun copy(): DraughtsState {
        val copyBoard = Array(SIZE) { board[it].copyOf() }
        return DraughtsState(
            copyBoard,
            agentTurn,
            mustContinueFromX,
            mustContinueFromY,
            pendingCaptures.toMutableSet(),
            positionHistory.toMutableList(),
            kingMoveCount,
            endgameGroup,
            endgameMoveCount
        )
    }

    /** The pieces on every square and the player to move, as a string; equal keys are the same position. */
    fun positionKey(): String {
        val sb = StringBuilder(1 + SIZE * SIZE)
        sb.append(agentTurn.toInt())
        for (column in board) {
            for (square in column) {
                sb.append(square.toInt())
            }
        }
        return sb.toString()
    }

    fun isOnBoard(x: Int, y: Int): Boolean = x in 0 until SIZE && y in 0 until SIZE

    fun get(x: Int, y: Int): Byte = board[x][y]

    fun set(x: Int, y: Int, value: Byte) {
        board[x][y] = value
    }

    fun isWhitePiece(p: Byte): Boolean = p == WHITE_MAN || p == WHITE_KING

    fun isBlackPiece(p: Byte): Boolean = p == BLACK_MAN || p == BLACK_KING

    fun isKing(p: Byte): Boolean = p == WHITE_KING || p == BLACK_KING

    fun isPendingCapture(x: Int, y: Int): Boolean = pendingCaptures.contains(captureKey(x, y))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DraughtsState) return false
        if (agentTurn != other.agentTurn) return false
        if (mustContinueFromX != other.mustContinueFromX || mustContinueFromY != other.mustContinueFromY) return false
        if (pendingCaptures != other.pendingCaptures) return false
        if (positionHistory != other.positionHistory) return false
        if (kingMoveCount != other.kingMoveCount) return false
        if (endgameGroup != other.endgameGroup || endgameMoveCount != other.endgameMoveCount) return false
        for (i in 0 until SIZE) {
            if (!board[i].contentEquals(other.board[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = agentTurn.toInt()
        result = 31 * result + mustContinueFromX
        result = 31 * result + mustContinueFromY
        result = 31 * result + pendingCaptures.hashCode()
        result = 31 * result + positionHistory.hashCode()
        result = 31 * result + kingMoveCount
        result = 31 * result + endgameGroup
        result = 31 * result + endgameMoveCount
        for (i in 0 until SIZE) {
            result = 31 * result + board[i].contentHashCode()
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (y in SIZE - 1 downTo 0) {
            for (x in 0 until SIZE) {
                sb.append(
                    when (board[x][y]) {
                        WHITE_MAN -> 'w'
                        WHITE_KING -> 'W'
                        BLACK_MAN -> 'b'
                        BLACK_KING -> 'B'
                        else -> if (isDarkSquare(x, y)) '.' else ' '
                    }
                )
                if (x < SIZE - 1) sb.append(' ')
            }
            if (y > 0) sb.append('\n')
        }
        return sb.toString()
    }
}
