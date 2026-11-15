package com.example.onerow

import org.junit.Assert.*
import org.junit.Test

class SpacePolicyTest {
    @Test fun periodAndCommaAlwaysTakeTheSpaceBack() {
        for (c in ".,!?") { assertTrue(SpacePolicy.cutsSpaceBefore(c, "d ", true)); assertTrue(SpacePolicy.cutsSpaceBefore(c, "d ", false)) }
    }
    @Test fun colonAndSemicolonOnlyTakeAnAutomaticSpaceBack() {
        for (c in ":;") { assertTrue(SpacePolicy.cutsSpaceBefore(c, "d ", true)); assertFalse(SpacePolicy.cutsSpaceBefore(c, "d ", false)) }
    }
    @Test fun nothingToCutWithoutAWordAndASpace() {
        assertFalse(SpacePolicy.cutsSpaceBefore('.', "  ", true))      // a space after a space
        assertFalse(SpacePolicy.cutsSpaceBefore('.', " ", true))       // only one character: start of the text
        assertFalse(SpacePolicy.cutsSpaceBefore('.', "dd", true))      // no space
    }
}
