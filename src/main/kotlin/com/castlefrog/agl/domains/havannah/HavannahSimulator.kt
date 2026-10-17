package com.castlefrog.agl.domains.havannah

import com.castlefrog.agl.Simulator
import com.castlefrog.agl.domains.AdversarialRewards
import com.castlefrog.agl.domains.nextPlayerTurnSequential
import com.castlefrog.agl.requireLegalAction

class HavannahSimulator(
    val base: Int = 10,
    val pieRule: Boolean = true
) : Simulator<HavannahState, HavannahAction> {

    private val size = 2 * base - 1
    private val actions: Array<Array<HavannahAction>> = HavannahAction.generateActions(size)

    private val corners: Array<IntArray>
        get() = arrayOf(
            intArrayOf(0, 0), intArrayOf(0, base - 1), intArrayOf(base - 1, 0),
            intArrayOf(base - 1, size - 1), intArrayOf(size - 1, base - 1), intArrayOf(size - 1, size - 1)
        )

    private val sides: Array<Array<IntArray>>
        get() {
            val sides = Array(6) { Array(base - 2) { IntArray(2) } }
            for (i in 0..base - 3) {
                sides[0][i][0] = 0
                sides[0][i][1] = i + 1
                sides[1][i][0] = i + 1
                sides[1][i][1] = 0
                sides[2][i][0] = i + 1
                sides[2][i][1] = base + i
                sides[3][i][0] = base + i
                sides[3][i][1] = size - 1
                sides[4][i][0] = size - 1
                sides[4][i][1] = base + i
                sides[5][i][0] = base + i
                sides[5][i][1] = i + 1
            }
            return sides
        }

    init {
        if (base < MIN_BASE) {
            throw IllegalArgumentException("Invalid board size: $base")
        }
    }

    override val initialState: HavannahState
        get() = HavannahState(base, Array(2 * base - 1) { ByteArray(2 * base - 1) }, HavannahState.TURN_BLACK)

    override fun calculateRewards(state: HavannahState): IntArray {
        val visited = Array(size) { BooleanArray(size) }
        for (y in 0 until size) {
            for (x in 0 until size) {
                // Checks: non empty location - hasn't been visited
                if (!state.isLocationEmpty(x, y) && !visited[x][y]) {
                    var result = dfsCornersSides(x, y, state, visited, corners, sides, actions)
                    // count corners
                    var nCorners = 0
                    for (k in 0..5) {
                        if (result % 2 == 1) {
                            nCorners += 1
                        }
                        result = result shr 1
                    }
                    // count sides
                    var nSides = 0
                    for (k in 0..5) {
                        if (result % 2 == 1) {
                            nSides += 1
                        }
                        result = result shr 1
                    }
                    if (nCorners >= 2 || nSides >= 3) {
                        return if (state.locations[x][y] == HavannahState.LOCATION_BLACK) {
                            AdversarialRewards.firstPlayerWins()
                        } else {
                            AdversarialRewards.secondPlayerWins()
                        }
                    }
                }
            }
        }

        val otherState = state.copy()
        for (y in otherState.locations.indices) {
            val xMin = if (y >= state.base) y - state.base + 1 else 0
            val xMax = if (y >= state.base) size else state.base + y
            for (x in xMin until xMax) {
                if (otherState.isLocationEmpty(
                        x,
                        y
                    ) || otherState.locations[x][y] == (otherState.agentTurn + 1).toByte()
                ) {
                    otherState.locations[x][y] = HavannahState.LOCATION_BLACK
                } else {
                    otherState.locations[x][y] = HavannahState.LOCATION_EMPTY
                }
            }
        }

        val visited2 = Array(size) { BooleanArray(size) }
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!otherState.isLocationEmpty(x, y) && !visited2[x][y]) {
                    if (dfsCornersSides(x, y, otherState, visited2, corners, sides, actions) == 0) {
                        return if (otherState.agentTurn == HavannahState.TURN_BLACK) {
                            AdversarialRewards.secondPlayerWins()
                        } else {
                            AdversarialRewards.firstPlayerWins()
                        }
                    }
                }
            }
        }

        // The check above misses a ring whose enclosed cells all hold the mover's stones. Every
        // such cell has six neighbours holding the mover's stones, and those six form a ring.
        val moverStone = (nextPlayerTurnSequential(state.agentTurn.toInt(), NUMBER_OF_PLAYERS) + 1).toByte()
        for (y in 0 until size) {
            val xMin = if (y >= state.base) y - state.base + 1 else 0
            val xMax = if (y >= state.base) size else state.base + y
            for (x in xMin until xMax) {
                if (isSurroundedBy(x, y, moverStone, state)) {
                    return if (moverStone == HavannahState.LOCATION_BLACK) {
                        AdversarialRewards.firstPlayerWins()
                    } else {
                        AdversarialRewards.secondPlayerWins()
                    }
                }
            }
        }
        return AdversarialRewards.neutral()
    }

    override fun calculateLegalActions(state: HavannahState): List<Set<HavannahAction>> {
        val legalActions = arrayListOf<MutableSet<HavannahAction>>(mutableSetOf(), mutableSetOf())
        val rewards = calculateRewards(state)
        if (rewards.contentEquals(AdversarialRewards.neutral())) {
            var count = 0
            var tempAction: HavannahAction? = null
            for (y in 0 until size) {
                val xMin = if (y >= state.base) y - state.base + 1 else 0
                val xMax = if (y >= state.base) size else state.base + y

                for (x in xMin until xMax) {
                    if (state.isLocationEmpty(x, y)) {
                        legalActions[state.agentTurn.toInt()].add(actions[x][y])
                    } else if (pieRule && count == 0 && state.agentTurn == HavannahState.TURN_WHITE) {
                        count = 1
                        tempAction = actions[x][y]
                    } else if (pieRule && count == 1 && state.agentTurn == HavannahState.TURN_WHITE) {
                        count = 2
                        tempAction = null
                    }
                }
            }
            if (tempAction != null) {
                legalActions[state.agentTurn.toInt()].add(tempAction)
            }
        }
        return legalActions
    }

    override fun stateTransition(state: HavannahState, actions: List<HavannahAction?>): HavannahState {
        val agentTurn = state.agentTurn.toInt()
        val legalActions = calculateLegalActions(state)
        val action = requireLegalAction(actions, agentTurn, legalActions[agentTurn], state)
        val nextState = state.copy()
        nextState.locations[action.x.toInt()][action.y.toInt()] = (nextState.agentTurn + 1).toByte()
        nextState.agentTurn = nextPlayerTurnSequential(nextState.agentTurn.toInt(), NUMBER_OF_PLAYERS).toByte()
        return nextState
    }

    override fun numberOfPlayers(): Int = NUMBER_OF_PLAYERS

    companion object {
        private const val MIN_BASE = 2
        private const val NUMBER_OF_PLAYERS = 2

        private fun dfsCornersSides(
            x0: Int,
            y0: Int,
            state: HavannahState,
            visited: Array<BooleanArray>,
            corners: Array<IntArray>,
            sides: Array<Array<IntArray>>,
            actions: Array<Array<HavannahAction>>
        ): Int {
            val size = 2 * state.base - 1
            var value = 0
            val stack = ArrayDeque<HavannahAction>()
            stack.addFirst(actions[x0][y0])
            visited[x0][y0] = true
            while (!stack.isEmpty()) {
                val v = stack.removeFirst()
                val x = v.x.toInt()
                val y = v.y.toInt()
                value = value or (getCornerMask(x, y, corners) or getSideMask(x, y, sides))
                for (i in -1..1) {
                    for (j in -1..1) {
                        val xi = x + i
                        val yi = y + j
                        if (i + j != 0 && xi >= 0 && yi >= 0 &&
                            xi < size && yi < size &&
                            (yi < state.base && xi < state.base + yi || yi >= state.base && xi > yi - state.base)
                        ) {
                            if (!visited[xi][yi] && state.locations[xi][yi] == state.locations[x][y]) {
                                stack.addFirst(actions[xi][yi])
                                visited[xi][yi] = true
                            }
                        }
                    }
                }
            }
            return value
        }

        /** True if all six neighbours of (x, y) are on the board and hold [stone]. */
        private fun isSurroundedBy(x: Int, y: Int, stone: Byte, state: HavannahState): Boolean {
            val size = 2 * state.base - 1
            for (i in -1..1) {
                for (j in -1..1) {
                    if (i + j != 0) {
                        val xi = x + i
                        val yi = y + j
                        val onBoard = xi >= 0 && yi >= 0 && xi < size && yi < size &&
                            (yi < state.base && xi < state.base + yi || yi >= state.base && xi > yi - state.base)
                        if (!onBoard || state.locations[xi][yi] != stone) {
                            return false
                        }
                    }
                }
            }
            return true
        }

        private fun getCornerMask(x: Int, y: Int, corners: Array<IntArray>): Int {
            return (corners.indices)
                .firstOrNull { corners[it][0] == x && corners[it][1] == y }
                ?.let { 1 shl it }
                ?: 0
        }

        private fun getSideMask(x: Int, y: Int, sides: Array<Array<IntArray>>): Int {
            for (i in sides.indices) {
                for (j in sides[i].indices) {
                    if (sides[i][j][0] == x && sides[i][j][1] == y) {
                        return 1 shl i + 6
                    }
                }
            }
            return 0
        }
    }
}
