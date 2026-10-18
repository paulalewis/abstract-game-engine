package com.castlefrog.agl.domains.hex

import com.castlefrog.agl.State

data class HexState(
    val boardSize: Int,
    val bitBoards: Array<ByteArray> = Array(2) {
        ByteArray((boardSize * boardSize + java.lang.Byte.SIZE - 1) / java.lang.Byte.SIZE)
    },
    var agentTurn: Byte = TURN_BLACK
) : State<HexState> {

    companion object {
        const val LOCATION_EMPTY = 0
        const val LOCATION_BLACK = 1
        const val LOCATION_WHITE = 2

        const val TURN_BLACK: Byte = 0
        const val TURN_WHITE: Byte = 1

        private const val BYTE_MASK = 0xff
    }

    override fun copy(): HexState {
        val copyBitBoards = Array(bitBoards.size) { bitBoards[it].copyOf() }
        return HexState(boardSize, copyBitBoards, agentTurn)
    }

    val locations: Array<ByteArray>
        get() {
            val locations = Array(boardSize) { ByteArray(boardSize) }
            var i = 0
            while (i < boardSize) {
                var j = 0
                while (j < boardSize) {
                    locations[i][j] = getLocation(i, j).toByte()
                    j += 1
                }
                i += 1
            }
            return locations
        }

    fun getLocation(x: Int, y: Int): Int {
        checkLocationArgs(x, y)
        val index = bitIndex(x, y)
        return when {
            isSet(bitBoards[0], index) -> LOCATION_BLACK
            isSet(bitBoards[1], index) -> LOCATION_WHITE
            else -> LOCATION_EMPTY
        }
    }

    fun isLocationEmpty(x: Int, y: Int): Boolean {
        checkLocationArgs(x, y)
        val index = bitIndex(x, y)
        return !isSet(bitBoards[0], index) && !isSet(bitBoards[1], index)
    }

    private fun isLocationOnBoard(x: Int, y: Int): Boolean {
        return x in 0 until boardSize && y in 0 until boardSize
    }

    val nPieces: Int
        get() = bitBoards[0].indices.sumOf { byteIndex ->
            val occupied = (bitBoards[0][byteIndex].toInt() or bitBoards[1][byteIndex].toInt()) and BYTE_MASK
            Integer.bitCount(occupied)
        }

    fun setLocation(x: Int, y: Int, value: Int) {
        checkLocationArgs(x, y)
        val index = bitIndex(x, y)
        when (value) {
            LOCATION_EMPTY -> {
                clearBit(bitBoards[0], index)
                clearBit(bitBoards[1], index)
            }
            LOCATION_BLACK -> {
                setBit(bitBoards[0], index)
                clearBit(bitBoards[1], index)
            }
            LOCATION_WHITE -> {
                clearBit(bitBoards[0], index)
                setBit(bitBoards[1], index)
            }
        }
    }

    private fun bitIndex(x: Int, y: Int): Int = y * boardSize + x

    private fun isSet(board: ByteArray, bitIndex: Int): Boolean {
        val bits = board[byteIndex(bitIndex)].toInt()
        return (bits and bitMask(bitIndex)) != 0
    }

    private fun setBit(board: ByteArray, bitIndex: Int) {
        val i = byteIndex(bitIndex)
        board[i] = (board[i].toInt() or bitMask(bitIndex)).toByte()
    }

    private fun clearBit(board: ByteArray, bitIndex: Int) {
        val i = byteIndex(bitIndex)
        board[i] = (board[i].toInt() and clearMask(bitIndex)).toByte()
    }

    private fun byteIndex(bitIndex: Int): Int = bitIndex / java.lang.Byte.SIZE

    private fun bitMask(bitIndex: Int): Int = 1 shl (bitIndex % java.lang.Byte.SIZE)

    /** 8-bit mask with the target bit cleared; matches historical `xor 0xff` clearing. */
    private fun clearMask(bitIndex: Int): Int = bitMask(bitIndex) xor BYTE_MASK

    private fun checkLocationArgs(x: Int, y: Int) {
        if (!isLocationOnBoard(x, y))
            throw IllegalArgumentException("(x=$x,y=$y) out of bounds")
    }

    override fun hashCode(): Int {
        var hashCode = 17 + boardSize
        hashCode = hashCode * 19 + bitBoards.contentDeepHashCode()
        hashCode = hashCode * 31 + agentTurn
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is HexState) {
            return false
        }
        if (bitBoards.size != other.bitBoards.size) {
            return false
        }
        for (i in bitBoards.indices) {
            if (bitBoards[i].size != other.bitBoards[i].size) {
                return false
            }
            bitBoards[i].indices
                .filter { other.bitBoards[i][it] != bitBoards[i][it] }
                .forEach { _ -> return false }
        }
        return other.boardSize == boardSize && other.agentTurn == agentTurn
    }

    override fun toString(): String {
        val output = StringBuilder()
        for (i in boardSize - 1 downTo 0) {
            for (j in i..boardSize - 2) {
                output.append(" ")
            }
            for (j in 0 until boardSize) {
                when (getLocation(j, i)) {
                    LOCATION_BLACK -> output.append("X")
                    LOCATION_WHITE -> output.append("O")
                    else -> output.append("-")
                }
                if (j != boardSize - 1) {
                    output.append(" ")
                }
            }
            if (i != 0) {
                output.append("\n")
            }
        }
        return output.toString()
    }
}
