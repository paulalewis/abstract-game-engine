package com.castlefrog.agl.domains.go

import kotlin.random.Random

/**
 * One position's groups and liberties, for [GoPlayoutPolicy] to pick and check moves fast.
 *
 * Built once per playout step with one pass over the board. [isLegal] then checks a
 * placement exactly as [GoSimulator] does, but from the groups next to the point instead
 * of a trial board and two full-board capture scans. It relies on every group on the
 * board having a liberty, which holds in any position the simulator produces; if some
 * group has none, it asks [GoSimulator.isLegalAction] instead.
 *
 * Points are indexed `x * boardSize + y`, the order of [GoState.boardSnapshot].
 */
internal class GoBoard(private val state: GoState, private val simulator: GoSimulator) {

    private val n = state.boardSize
    private val cells: ByteArray = state.boardSnapshot()
    private val turn = state.agentTurn.toInt()
    private val stone: Byte = if (turn == 0) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
    private val opponent: Byte = if (turn == 0) GoState.LOCATION_WHITE else GoState.LOCATION_BLACK

    /** The group of each stone, or -1 for an empty point. */
    private val groupOf = IntArray(cells.size) { -1 }
    private val libertyCount = IntArray(cells.size)
    private val lastLiberty = IntArray(cells.size)
    private val groupColor = ByteArray(cells.size)
    private var groupCount = 0
    private var hasGroupWithoutLiberty = false

    private val empties = IntArray(cells.size)
    private var emptyCount = 0

    init {
        val stack = IntArray(cells.size)
        val libertyStamp = IntArray(cells.size)
        val neighbours = IntArray(4)
        for (p in cells.indices) {
            val color = cells[p]
            if (color == GoState.LOCATION_EMPTY) {
                empties[emptyCount++] = p
                continue
            }
            if (groupOf[p] != -1) continue
            val g = groupCount++
            val stamp = g + 1
            groupColor[g] = color
            groupOf[p] = g
            var top = 0
            stack[top++] = p
            var liberties = 0
            var last = -1
            while (top > 0) {
                val q = stack[--top]
                for (i in 0 until neighbours(q, neighbours)) {
                    val r = neighbours[i]
                    val c = cells[r]
                    if (c == GoState.LOCATION_EMPTY) {
                        if (libertyStamp[r] != stamp) {
                            libertyStamp[r] = stamp
                            liberties += 1
                            last = r
                        }
                    } else if (c == color && groupOf[r] == -1) {
                        groupOf[r] = g
                        stack[top++] = r
                    }
                }
            }
            libertyCount[g] = liberties
            lastLiberty[g] = last
            if (liberties == 0) hasGroupWithoutLiberty = true
        }
    }

    /**
     * A capture of an opponent group in atari, or else a move that gives one of the
     * mover's groups in atari at least two liberties; legal and not an own eye. Null if
     * there is none.
     */
    fun tacticalMove(random: Random): GoAction? {
        val captures = IntArray(groupCount)
        var captureCount = 0
        val escapes = IntArray(groupCount)
        var escapeCount = 0
        val neighbours = IntArray(4)
        for (g in 0 until groupCount) {
            if (libertyCount[g] != 1) continue
            val q = lastLiberty[g]
            if (groupColor[g] == opponent) {
                captures[captureCount++] = q
            } else {
                var emptyNeighbours = 0
                for (i in 0 until neighbours(q, neighbours)) {
                    if (cells[neighbours[i]] == GoState.LOCATION_EMPTY) emptyNeighbours += 1
                }
                if (emptyNeighbours >= 2) escapes[escapeCount++] = q
            }
        }
        return pickLegal(captures, captureCount, random) ?: pickLegal(escapes, escapeCount, random)
    }

    /** A random empty point that is legal and not an own eye, or null if there is none. */
    fun randomMove(random: Random): GoAction? = pickLegal(empties, emptyCount, random)

    /** Tries the first [count] points of [points] in random order; reorders the array. */
    private fun pickLegal(points: IntArray, count: Int, random: Random): GoAction? {
        var remaining = count
        while (remaining > 0) {
            val j = random.nextInt(remaining)
            val p = points[j]
            points[j] = points[remaining - 1]
            points[remaining - 1] = p
            remaining -= 1
            val x = p / n
            val y = p % n
            if (!GoPlayoutPolicy.isOwnEye(state, x, y, stone) && isLegal(p)) {
                return GoAction.place(x, y)
            }
        }
        return null
    }

    /** Whether the mover may place a stone on [p], by the simulator's rules. */
    fun isLegal(p: Int): Boolean {
        if (cells[p] != GoState.LOCATION_EMPTY) return false
        if (hasGroupWithoutLiberty) {
            return simulator.isLegalAction(state, turn, GoAction.place(p / n, p % n))
        }
        val neighbours = IntArray(4)
        val count = neighbours(p, neighbours)
        var captures = false
        var hasLiberty = false
        for (i in 0 until count) {
            val r = neighbours[i]
            when (cells[r]) {
                GoState.LOCATION_EMPTY -> hasLiberty = true
                opponent -> if (libertyCount[groupOf[r]] == 1) captures = true
                else -> if (libertyCount[groupOf[r]] >= 2) hasLiberty = true
            }
        }
        // Captured stones next to p become its liberties.
        val survives = hasLiberty || captures
        if (!survives && simulator.rules == GoRules.CHINESE) return false
        val result = cells.copyOf()
        if (survives) {
            result[p] = stone
            if (captures) {
                for (i in 0 until count) {
                    val r = neighbours[i]
                    if (cells[r] == opponent && libertyCount[groupOf[r]] == 1) removeGroup(result, groupOf[r])
                }
            }
        } else {
            // Tromp–Taylor self-capture: the stone and its groups are removed, p stays empty.
            for (i in 0 until count) {
                val r = neighbours[i]
                if (cells[r] == stone) removeGroup(result, groupOf[r])
            }
        }
        return !hasOccurred(result, p)
    }

    private fun removeGroup(result: ByteArray, g: Int) {
        for (q in cells.indices) {
            if (groupOf[q] == g) result[q] = GoState.LOCATION_EMPTY
        }
    }

    /** [GoState.coloringHasOccurred], checking point [p] first to skip most earlier colorings cheaply. */
    private fun hasOccurred(result: ByteArray, p: Int): Boolean {
        val history = state.positionHistory
        for (previous in history) {
            if (previous.size == result.size && previous[p] == result[p] && previous.contentEquals(result)) return true
        }
        return cells.contentEquals(result)
    }

    /** Writes the on-board orthogonal neighbours of [p] into [out] and returns how many. */
    private fun neighbours(p: Int, out: IntArray): Int {
        val x = p / n
        val y = p % n
        var count = 0
        if (x > 0) out[count++] = p - n
        if (x < n - 1) out[count++] = p + n
        if (y > 0) out[count++] = p - 1
        if (y < n - 1) out[count++] = p + 1
        return count
    }
}
