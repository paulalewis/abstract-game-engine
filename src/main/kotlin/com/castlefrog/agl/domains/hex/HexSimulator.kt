package com.castlefrog.agl.domains.hex

import com.castlefrog.agl.Simulator
import com.castlefrog.agl.domains.AdversarialRewards
import com.castlefrog.agl.domains.nextPlayerTurnSequential
import com.castlefrog.agl.requireLegalAction

class HexSimulator(
    private val boardSize: Int = 11,
    private val pieRule: Boolean = true
) : Simulator<HexState, HexAction> {

    private val actions = HexAction.generateActions(boardSize)

    init {
        if (boardSize !in MIN_BOARD_SIZE until MAX_BOARD_SIZE) {
            throw IllegalArgumentException("Invalid board size: $boardSize")
        }
    }

    override val initialState: HexState
        get() = HexState(boardSize = boardSize)

    override fun calculateRewards(state: HexState): IntArray {
        val locations = state.locations
        val visited = Array(state.boardSize) { BooleanArray(state.boardSize) }
        for (i in 0 until state.boardSize) {
            if (locations[0][i].toInt() == HexState.LOCATION_BLACK && !visited[0][i]) {
                if (dfsSides(0, i, locations, visited, actions) and 3 == 3) {
                    return AdversarialRewards.firstPlayerWins()
                }
            }
            if (locations[i][0].toInt() == HexState.LOCATION_WHITE && !visited[i][0]) {
                if (dfsSides(i, 0, locations, visited, actions) and 12 == 12) {
                    return AdversarialRewards.secondPlayerWins()
                }
            }
        }
        return AdversarialRewards.neutral()
    }

    override fun calculateLegalActions(state: HexState): List<Set<HexAction>> {
        val legalActions = ArrayList<MutableSet<HexAction>>()
        legalActions.add(mutableSetOf())
        legalActions.add(mutableSetOf())
        val rewards = calculateRewards(state)
        if (rewards.contentEquals(AdversarialRewards.neutral())) {
            (0 until state.boardSize).forEach { i ->
                (0 until state.boardSize)
                    .filter { state.isLocationEmpty(i, it) || (pieRule && isSecondMove(state)) }
                    .forEach { legalActions[state.agentTurn.toInt()].add(actions[i][it]) }
            }
        }
        return legalActions
    }

    /**
     * Checks the cell of [action] alone instead of building the set of every legal
     * cell as in [calculateLegalActions]. The game-over check still runs, last.
     */
    override fun isLegalAction(state: HexState, agentTurn: Int, action: HexAction): Boolean {
        if (agentTurn != state.agentTurn.toInt()) {
            return false
        }
        // Actions are built from Int indices with toByte(), so read them back unsigned.
        val x = action.x.toInt() and BYTE_MASK
        val y = action.y.toInt() and BYTE_MASK
        if (x >= state.boardSize || y >= state.boardSize) {
            return false
        }
        if (!state.isLocationEmpty(x, y) && !(pieRule && isSecondMove(state))) {
            return false
        }
        return calculateRewards(state).contentEquals(AdversarialRewards.neutral())
    }

    override fun stateTransition(state: HexState, actions: List<HexAction?>): HexState {
        val agentTurn = state.agentTurn.toInt()
        val action = requireLegalAction(actions, agentTurn, state) { isLegalAction(state, agentTurn, it) }
        val x = action.x.toInt()
        val y = action.y.toInt()
        val nextState = state.copy()
        if (nextState.isLocationEmpty(x, y)) {
            nextState.setLocation(x, y, nextState.agentTurn + 1)
            nextState.agentTurn = nextPlayerTurnSequential(nextState.agentTurn.toInt(), NUMBER_OF_PLAYERS).toByte()
        } else {
            nextState.setLocation(x, y, 0)
            nextState.setLocation(y, x, nextState.agentTurn + 1)
            nextState.agentTurn = nextPlayerTurnSequential(nextState.agentTurn.toInt(), NUMBER_OF_PLAYERS).toByte()
        }
        return nextState
    }

    override fun numberOfPlayers(): Int = NUMBER_OF_PLAYERS

    companion object {
        private const val NUMBER_OF_PLAYERS = 2
        private const val MIN_BOARD_SIZE = 3
        private const val MAX_BOARD_SIZE = 255
        private const val BYTE_MASK = 0xFF

        private fun dfsSides(
            x0: Int,
            y0: Int,
            locations: Array<ByteArray>,
            visited: Array<BooleanArray>,
            actions: Array<Array<HexAction>>
        ): Int {
            var value = 0
            val stack = ArrayDeque<HexAction>()
            stack.addFirst(actions[x0][y0])
            visited[x0][y0] = true
            while (!stack.isEmpty()) {
                val v = stack.removeFirst()
                val x = v.x.toInt()
                val y = v.y.toInt()
                value = value or getLocationMask(x, y, actions.size)
                var i = -1
                while (i <= 1) {
                    var j = -1
                    while (j <= 1) {
                        val xi = x + i
                        val yi = y + j
                        if (i + j != 0 && xi >= 0 && yi >= 0 &&
                            xi < actions.size && yi < actions.size
                        ) {
                            if (!visited[xi][yi] && locations[xi][yi] == locations[x][y]) {
                                stack.addFirst(actions[xi][yi])
                                visited[xi][yi] = true
                            }
                        }
                        j += 1
                    }
                    i += 1
                }
            }
            return value
        }

        private fun getLocationMask(x: Int, y: Int, boardSize: Int): Int {
            var side = when (x) {
                0 -> { 1 }
                boardSize - 1 -> { 2 }
                else -> { 0 }
            }
            if (y == 0) {
                side = side or 4
            } else if (y == boardSize - 1) {
                side = side or 8
            }
            return side
        }

        private fun isSecondMove(state: HexState): Boolean {
            return state.nPieces == 1 && state.agentTurn == HexState.TURN_WHITE
        }
    }
}
