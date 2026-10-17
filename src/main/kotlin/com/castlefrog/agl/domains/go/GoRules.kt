package com.castlefrog.agl.domains.go

/**
 * Rule set played by [GoSimulator]. Both use positional superko, area scoring
 * with komi for white, and end after two consecutive passes. They differ only
 * in self-capture.
 */
enum class GoRules {
    /** Self-capture is legal when the resulting whole-board coloring is new. */
    TROMP_TAYLOR,

    /** Self-capture is illegal: the placed stone's group must keep a liberty after captures. */
    CHINESE
}
