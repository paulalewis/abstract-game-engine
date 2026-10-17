package com.castlefrog.agl

import com.castlefrog.agl.domains.chess.ChessSimulator
import com.castlefrog.agl.domains.chess.ChessState
import com.castlefrog.agl.domains.connect4.Connect4Simulator
import com.castlefrog.agl.domains.connect4.Connect4State
import com.castlefrog.agl.domains.draughts.DraughtsSimulator
import com.castlefrog.agl.domains.draughts.DraughtsState
import com.castlefrog.agl.domains.go.GoSimulator
import com.castlefrog.agl.domains.go.GoState
import com.castlefrog.agl.domains.havannah.HavannahSimulator
import com.castlefrog.agl.domains.havannah.HavannahState
import com.castlefrog.agl.domains.hex.HexSimulator
import com.castlefrog.agl.domains.hex.HexState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Parameterized harness: every domain must satisfy [SimulatorContract], including
 * [Simulator.isLegalAction] agreeing with [Simulator.calculateLegalActions].
 */
@RunWith(Parameterized::class)
class CrossDomainSimulatorContractTest(
    @Suppress("unused") private val domainName: String,
    private val runContracts: () -> Unit
) {

    @Test
    fun stateTransitionAndCopyContracts() {
        runContracts()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun domains(): Collection<Array<Any>> = listOf(
            arrayOf(
                "Connect4",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = Connect4Simulator(),
                        mutateNested = { state: Connect4State -> state.bitBoards[0] = state.bitBoards[0] xor 1L }
                    )
                }
            ),
            arrayOf(
                "Hex",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = HexSimulator(boardSize = 5),
                        mutateNested = { state: HexState ->
                            state.setLocation(0, 0, HexState.LOCATION_BLACK)
                        }
                    )
                }
            ),
            arrayOf(
                "Havannah",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = HavannahSimulator(base = 5),
                        mutateNested = { state: HavannahState ->
                            state.locations[0][0] = HavannahState.LOCATION_BLACK
                        }
                    )
                }
            ),
            arrayOf(
                "Chess",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = ChessSimulator(),
                        mutateNested = { state: ChessState ->
                            state.set(0, 1, ChessState.EMPTY)
                        }
                    )
                }
            ),
            arrayOf(
                "Draughts",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = DraughtsSimulator(),
                        mutateNested = { state: DraughtsState ->
                            state.set(0, 2, DraughtsState.EMPTY)
                        }
                    )
                }
            ),
            arrayOf(
                "Go",
                {
                    SimulatorContract.assertDomainContracts(
                        simulator = GoSimulator(boardSize = 5),
                        mutateNested = { state: GoState ->
                            state.set(0, 0, GoState.LOCATION_BLACK)
                        }
                    )
                }
            )
        )
    }
}
