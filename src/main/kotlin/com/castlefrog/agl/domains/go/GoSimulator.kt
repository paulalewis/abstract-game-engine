package com.castlefrog.agl.domains.go

import com.castlefrog.agl.Simulator
import com.castlefrog.agl.domains.AdversarialRewards
import com.castlefrog.agl.domains.nextPlayerTurnSequential
import com.castlefrog.agl.requireLegalAction

/**
 * Tromp–Taylor Go (logical rules), with a configurable square board.
 *
 * Default board is 19×19. A turn is a pass, or a play that does not repeat an
 * earlier whole-board coloring (positional superko). A play colors an empty
 * point, then clears opponent stones with no liberties, then own stones with
 * no liberties — suicide is legal when the resulting coloring is new. Two
 * consecutive passes end the game. Scoring is area (stones + empty points that
 * reach only that color). [komi] is added to white (7 by default).
 */
class GoSimulator(
    val boardSize: Int = GoState.DEFAULT_BOARD_SIZE,
    val komi: Double = DEFAULT_KOMI
) : Simulator<GoState, GoAction> {

    init {
        require(boardSize in MIN_BOARD_SIZE..MAX_BOARD_SIZE) {
            "Invalid board size: $boardSize"
        }
    }

    override val initialState: GoState
        get() {
            val state = GoState(boardSize = boardSize)
            state.recordCurrentColoring()
            return state
        }

    override fun numberOfPlayers(): Int = NUMBER_OF_PLAYERS

    override fun calculateRewards(state: GoState): IntArray {
        if (state.consecutivePasses < 2) {
            return AdversarialRewards.neutral()
        }
        val score = score(state)
        return when {
            score.black > score.white -> AdversarialRewards.firstPlayerWins()
            score.white > score.black -> AdversarialRewards.secondPlayerWins()
            else -> AdversarialRewards.neutral()
        }
    }

    /**
     * Area score of [state]: each side's stones and territory, with [komi] for white.
     * Works for any state, finished or not. [calculateRewards] compares these totals.
     */
    fun score(state: GoState): GoScore {
        var blackStones = 0
        var whiteStones = 0
        var blackTerritory = 0
        var whiteTerritory = 0
        val visited = Array(state.boardSize) { BooleanArray(state.boardSize) }
        for (x in 0 until state.boardSize) {
            for (y in 0 until state.boardSize) {
                when (state.get(x, y)) {
                    GoState.LOCATION_BLACK -> blackStones += 1
                    GoState.LOCATION_WHITE -> whiteStones += 1
                    GoState.LOCATION_EMPTY -> {
                        if (!visited[x][y]) {
                            val region = ArrayList<Pair<Int, Int>>()
                            val borders = HashSet<Byte>()
                            floodEmpty(state, x, y, visited, region, borders)
                            if (borders.size == 1) {
                                when (borders.first()) {
                                    GoState.LOCATION_BLACK -> blackTerritory += region.size
                                    GoState.LOCATION_WHITE -> whiteTerritory += region.size
                                }
                            }
                        }
                    }
                }
            }
        }
        return GoScore(blackStones, blackTerritory, whiteStones, whiteTerritory, komi)
    }

    override fun calculateLegalActions(state: GoState): List<Set<GoAction>> {
        val legal = listOf(mutableSetOf<GoAction>(), mutableSetOf())
        if (state.consecutivePasses >= 2) {
            return legal
        }
        val turn = state.agentTurn.toInt()
        legal[turn].add(GoAction.pass())
        val stone = if (turn == 0) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
        for (x in 0 until state.boardSize) {
            for (y in 0 until state.boardSize) {
                if (state.get(x, y) != GoState.LOCATION_EMPTY) continue
                if (isLegalPlacement(state, x, y, stone)) {
                    legal[turn].add(GoAction.place(x, y))
                }
            }
        }
        return legal
    }

    /**
     * Checks [action] alone: one trial board and one superko scan for a placement,
     * instead of one per empty point as in [calculateLegalActions].
     */
    override fun isLegalAction(state: GoState, agentTurn: Int, action: GoAction): Boolean {
        if (state.consecutivePasses >= 2 || agentTurn != state.agentTurn.toInt()) {
            return false
        }
        if (action.isPass) {
            return action == GoAction.pass()
        }
        if (!state.isOnBoard(action.x, action.y) || state.get(action.x, action.y) != GoState.LOCATION_EMPTY) {
            return false
        }
        val stone = if (agentTurn == 0) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
        return isLegalPlacement(state, action.x, action.y, stone)
    }

    override fun stateTransition(state: GoState, actions: List<GoAction?>): GoState {
        val agentTurn = state.agentTurn.toInt()
        val action = requireLegalAction(actions, agentTurn, state) { isLegalAction(state, agentTurn, it) }
        val next = state.copy()
        if (action.isPass) {
            next.consecutivePasses += 1
            next.agentTurn = nextPlayerTurnSequential(agentTurn, NUMBER_OF_PLAYERS).toByte()
            return next
        }
        next.consecutivePasses = 0
        next.recordCurrentColoring()
        val stone = if (agentTurn == 0) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
        val captured = applyPlacement(next, action.x, action.y, stone)
        if (agentTurn == 0) {
            next.capturedByBlack += captured
        } else {
            next.capturedByWhite += captured
        }
        next.positionHistory.add(next.boardSnapshot())
        next.agentTurn = nextPlayerTurnSequential(agentTurn, NUMBER_OF_PLAYERS).toByte()
        return next
    }

    /**
     * Place [stone] at ([x], [y]), clear opponent stones that do not reach empty,
     * then clear own stones that do not reach empty. Returns opponent stones removed.
     */
    private fun applyPlacement(state: GoState, x: Int, y: Int, stone: Byte): Int {
        state.set(x, y, stone)
        val opponent = if (stone == GoState.LOCATION_BLACK) GoState.LOCATION_WHITE else GoState.LOCATION_BLACK
        val captured = clearColor(state, opponent)
        clearColor(state, stone)
        return captured
    }

    private fun clearColor(state: GoState, color: Byte): Int {
        val toRemove = ArrayList<Pair<Int, Int>>()
        val visited = Array(state.boardSize) { BooleanArray(state.boardSize) }
        for (x in 0 until state.boardSize) {
            for (y in 0 until state.boardSize) {
                if (state.get(x, y) != color || visited[x][y]) continue
                val group = collectGroup(state, x, y)
                for ((gx, gy) in group) {
                    visited[gx][gy] = true
                }
                if (liberties(state, group).isEmpty()) {
                    toRemove.addAll(group)
                }
            }
        }
        for ((gx, gy) in toRemove) {
            state.set(gx, gy, GoState.LOCATION_EMPTY)
        }
        return toRemove.size
    }

    private fun isLegalPlacement(state: GoState, x: Int, y: Int, stone: Byte): Boolean {
        val trial = GoState(
            boardSize = state.boardSize,
            board = Array(state.boardSize) { state.board[it].copyOf() }
        )
        applyPlacement(trial, x, y, stone)
        return !state.coloringHasOccurred(trial.boardSnapshot())
    }

    private fun floodEmpty(
        state: GoState,
        x: Int,
        y: Int,
        visited: Array<BooleanArray>,
        region: MutableList<Pair<Int, Int>>,
        borders: MutableSet<Byte>
    ) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(x to y)
        visited[x][y] = true
        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.removeFirst()
            region.add(cx to cy)
            for ((dx, dy) in ORTHO) {
                val nx = cx + dx
                val ny = cy + dy
                if (!state.isOnBoard(nx, ny)) continue
                val v = state.get(nx, ny)
                if (v == GoState.LOCATION_EMPTY) {
                    if (!visited[nx][ny]) {
                        visited[nx][ny] = true
                        stack.add(nx to ny)
                    }
                } else {
                    borders.add(v)
                }
            }
        }
    }

    companion object {
        private const val NUMBER_OF_PLAYERS = 2
        private const val MIN_BOARD_SIZE = 2
        private const val MAX_BOARD_SIZE = 19
        private const val DEFAULT_KOMI = 7.0
        private val ORTHO = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)

        internal fun collectGroup(state: GoState, x: Int, y: Int): List<Pair<Int, Int>> {
            val color = state.get(x, y)
            if (color == GoState.LOCATION_EMPTY) return emptyList()
            val group = ArrayList<Pair<Int, Int>>()
            val visited = Array(state.boardSize) { BooleanArray(state.boardSize) }
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(x to y)
            visited[x][y] = true
            while (stack.isNotEmpty()) {
                val (cx, cy) = stack.removeFirst()
                group.add(cx to cy)
                for ((dx, dy) in ORTHO) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (!state.isOnBoard(nx, ny) || visited[nx][ny]) continue
                    if (state.get(nx, ny) == color) {
                        visited[nx][ny] = true
                        stack.add(nx to ny)
                    }
                }
            }
            return group
        }

        internal fun liberties(state: GoState, group: List<Pair<Int, Int>>): Set<Pair<Int, Int>> {
            val libs = HashSet<Pair<Int, Int>>()
            for ((x, y) in group) {
                for ((dx, dy) in ORTHO) {
                    val nx = x + dx
                    val ny = y + dy
                    if (state.isOnBoard(nx, ny) && state.get(nx, ny) == GoState.LOCATION_EMPTY) {
                        libs.add(nx to ny)
                    }
                }
            }
            return libs
        }
    }
}
