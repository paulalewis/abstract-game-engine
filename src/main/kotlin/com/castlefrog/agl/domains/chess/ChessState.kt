package com.castlefrog.agl.domains.chess

import com.castlefrog.agl.State

/**
 * Chess position on an 8×8 board.
 *
 * Pieces are signed bytes: positive = white, negative = black.
 * Absolute values: [PAWN], [KNIGHT], [BISHOP], [ROOK], [QUEEN], [KING].
 *
 * Castling rights: bits 0 white king-side, 1 white queen-side, 2 black king-side, 3 black queen-side.
 * [enPassantX] is the file of a possible en-passant capture target (-1 if none); the target
 * rank is implied by [agentTurn].
 *
 * [halfmoveClock] implements the fifty-move rule and [positionHistory] threefold
 * repetition (see property docs).
 */
class ChessState(
    val board: Array<ByteArray> = initialBoard(),
    var agentTurn: Byte = TURN_WHITE,
    var castlingRights: Int = 0b1111,
    var enPassantX: Int = -1,
    /**
     * Number of half-moves (plies) since the last pawn move or capture.
     *
     * Incremented after every move that is neither a pawn move nor a capture;
     * reset to 0 when a pawn moves or any piece is captured. When this reaches
     * 100 (50 full moves by each side), [ChessSimulator] treats the position as
     * a draw (neutral rewards, no legal actions).
     *
     * Same meaning as the halfmove clock in FEN / the fifty-move rule in the Laws of Chess.
     */
    var halfmoveClock: Int = 0,
    /**
     * Position keys of the earlier positions since the last pawn move or capture,
     * oldest first. The current position is not included.
     *
     * [ChessSimulator] appends the key of the position being left on every move, and
     * clears the list when [halfmoveClock] resets to 0, since a pawn move or capture
     * makes those positions unreachable. Its size therefore matches [halfmoveClock]
     * for states reached by play, and the fifty-move rule caps it at 100 entries.
     * When the current position's key already occurs twice, the position has occurred
     * three times and [ChessSimulator] treats it as a draw. Keys are made by
     * [ChessSimulator.positionKey].
     */
    val positionHistory: MutableList<String> = ArrayList()
) : State<ChessState> {

    companion object {
        const val SIZE = 8
        const val EMPTY: Byte = 0
        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6
        const val TURN_WHITE: Byte = 0
        const val TURN_BLACK: Byte = 1

        fun initialBoard(): Array<ByteArray> {
            val b = Array(SIZE) { ByteArray(SIZE) }
            val back = intArrayOf(ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK)
            for (x in 0 until SIZE) {
                b[x][0] = back[x].toByte()
                b[x][1] = PAWN.toByte()
                b[x][6] = (-PAWN).toByte()
                b[x][7] = (-back[x]).toByte()
            }
            return b
        }

        fun isWhite(piece: Byte): Boolean = piece > 0
        fun isBlack(piece: Byte): Boolean = piece < 0
        fun type(piece: Byte): Int = kotlin.math.abs(piece.toInt())
    }

    override fun copy(): ChessState {
        val copyBoard = Array(SIZE) { board[it].copyOf() }
        return ChessState(
            copyBoard,
            agentTurn,
            castlingRights,
            enPassantX,
            halfmoveClock,
            ArrayList(positionHistory)
        )
    }

    fun isOnBoard(x: Int, y: Int): Boolean = x in 0 until SIZE && y in 0 until SIZE

    fun get(x: Int, y: Int): Byte = board[x][y]

    fun set(x: Int, y: Int, value: Byte) {
        board[x][y] = value
    }

    fun pieceBelongsToSide(piece: Byte, turn: Int): Boolean {
        return if (turn == 0) isWhite(piece) else isBlack(piece)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChessState) return false
        if (agentTurn != other.agentTurn || castlingRights != other.castlingRights) return false
        if (enPassantX != other.enPassantX || halfmoveClock != other.halfmoveClock) return false
        if (positionHistory != other.positionHistory) return false
        for (i in 0 until SIZE) {
            if (!board[i].contentEquals(other.board[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = agentTurn.toInt()
        result = 31 * result + castlingRights
        result = 31 * result + enPassantX
        result = 31 * result + halfmoveClock
        result = 31 * result + positionHistory.hashCode()
        for (i in 0 until SIZE) {
            result = 31 * result + board[i].contentHashCode()
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (y in SIZE - 1 downTo 0) {
            for (x in 0 until SIZE) {
                sb.append(pieceChar(board[x][y]))
                if (x < SIZE - 1) sb.append(' ')
            }
            if (y > 0) sb.append('\n')
        }
        return sb.toString()
    }

    private fun pieceChar(p: Byte): Char {
        val c = when (type(p)) {
            PAWN -> 'p'
            KNIGHT -> 'n'
            BISHOP -> 'b'
            ROOK -> 'r'
            QUEEN -> 'q'
            KING -> 'k'
            else -> '.'
        }
        return if (isWhite(p)) c.uppercaseChar() else c
    }
}
