package com.webclass.plus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KarnaughMinimizerTest {
    @Test fun constantsAreCorrect() {
        assertEquals("F = 0", KarnaughMinimizer.solve(List(16) { 0 }, 4, KarnaughForm.SOP).expression)
        assertEquals("F = 1", KarnaughMinimizer.solve(List(16) { 1 }, 4, KarnaughForm.SOP).expression)
        assertEquals("F = 0", KarnaughMinimizer.solve(List(16) { 0 }, 4, KarnaughForm.POS).expression)
        assertEquals("F = 1", KarnaughMinimizer.solve(List(16) { 1 }, 4, KarnaughForm.POS).expression)
    }

    @Test fun wrapAroundAndDontCareExamplesAreCorrect() {
        val wrap = List(16) { if (it in setOf(0, 2, 8, 10)) 1 else 0 }
        assertEquals("F = B̅·D̅", KarnaughMinimizer.solve(wrap, 4, KarnaughForm.SOP).expression)

        val withDontCare = MutableList(8) { 0 }.apply {
            this[1] = 1; this[3] = 1; this[5] = -1; this[7] = -1
        }
        assertEquals("F = C", KarnaughMinimizer.solve(withDontCare, 3, KarnaughForm.SOP).expression)
    }

    @Test fun everyTwoAndThreeVariableFunctionIsEquivalent() {
        for (variables in 2..3) {
            val size = 1 shl variables
            for (truthTable in 0 until (1 shl size)) {
                val values = List(size) { if (truthTable and (1 shl it) != 0) 1 else 0 }
                verify(values, variables, KarnaughForm.SOP)
                verify(values, variables, KarnaughForm.POS)
            }
        }
    }

    @Test fun randomFourVariableFunctionsIncludingDontCaresAreValid() {
        val random = Random(20261107)
        repeat(2_000) {
            val values = List(16) { random.nextInt(3) - 1 }
            verify(values, 4, KarnaughForm.SOP)
            verify(values, 4, KarnaughForm.POS)
        }
    }

    private fun verify(values: List<Int>, variables: Int, form: KarnaughForm) {
        val solution = KarnaughMinimizer.solve(values, variables, form)
        values.forEachIndexed { minterm, expected ->
            when (expected) {
                1 -> assertTrue("$form vars=$variables m$minterm", solution.evaluate(minterm))
                0 -> assertFalse("$form vars=$variables m$minterm", solution.evaluate(minterm))
            }
        }
    }
}
