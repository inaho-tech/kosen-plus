package com.webclass.plus.util

enum class KarnaughForm { SOP, POS }

data class KarnaughImplicant(val value: Int, val mask: Int) {
    fun covers(minterm: Int): Boolean = (minterm and mask.inv()) == (value and mask.inv())
    fun literalCount(variableCount: Int): Int = variableCount - Integer.bitCount(mask)
}

data class KarnaughSolution(
    val variableCount: Int,
    val form: KarnaughForm,
    val expression: String,
    val groups: List<KarnaughImplicant>,
    val constant: Boolean? = null
) {
    fun evaluate(minterm: Int): Boolean = constant ?: when (form) {
        KarnaughForm.SOP -> groups.any { it.covers(minterm) }
        KarnaughForm.POS -> groups.none { it.covers(minterm) }
    }
}

/** Exact two-level minimizer used by the 2–4 variable Karnaugh-map UI. */
object KarnaughMinimizer {
    fun solve(values: List<Int>, variableCount: Int, form: KarnaughForm): KarnaughSolution {
        require(variableCount in 2..4)
        require(values.size == (1 shl variableCount))
        require(values.all { it in -1..1 })

        val targets = values.indices.filterTo(linkedSetOf()) {
            values[it] == if (form == KarnaughForm.SOP) 1 else 0
        }
        val dontCares = values.indices.filterTo(linkedSetOf()) { values[it] == -1 }

        if (targets.isEmpty()) {
            val output = form == KarnaughForm.POS
            return KarnaughSolution(variableCount, form, if (output) "F = 1" else "F = 0", emptyList(), output)
        }

        val primes = generatePrimeImplicants(targets + dontCares)
            .filter { prime -> targets.any(prime::covers) }
            .sortedWith(compareBy<KarnaughImplicant>({ it.literalCount(variableCount) }, { it.mask }, { it.value }))

        var best: List<KarnaughImplicant>? = null
        var bestLiteralCount = Int.MAX_VALUE
        val subsetCount = 1 shl primes.size
        for (subset in 1 until subsetCount) {
            val termCount = Integer.bitCount(subset)
            if (best != null && termCount > best!!.size) continue
            val selected = primes.indices.filter { subset and (1 shl it) != 0 }.map(primes::get)
            if (!targets.all { target -> selected.any { it.covers(target) } }) continue
            val literals = selected.sumOf { it.literalCount(variableCount) }
            if (best == null || termCount < best!!.size || (termCount == best!!.size && literals < bestLiteralCount)) {
                best = selected
                bestLiteralCount = literals
            }
        }

        val groups = best.orEmpty()
        return KarnaughSolution(
            variableCount = variableCount,
            form = form,
            expression = formatExpression(groups, variableCount, form),
            groups = groups
        )
    }

    private fun generatePrimeImplicants(seed: Set<Int>): List<KarnaughImplicant> {
        var current = seed.map { KarnaughImplicant(it, 0) }.toSet()
        val primes = linkedSetOf<KarnaughImplicant>()

        while (current.isNotEmpty()) {
            val used = mutableSetOf<KarnaughImplicant>()
            val next = linkedSetOf<KarnaughImplicant>()
            val list = current.toList()
            for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    val first = list[i]
                    val second = list[j]
                    if (first.mask != second.mask) continue
                    val difference = (first.value xor second.value) and first.mask.inv()
                    if (Integer.bitCount(difference) == 1) {
                        used += first
                        used += second
                        val newMask = first.mask or difference
                        next += KarnaughImplicant(first.value and newMask.inv(), newMask)
                    }
                }
            }
            primes += current.filterNot { it in used }
            current = next
        }
        return primes.toList()
    }

    private fun formatExpression(groups: List<KarnaughImplicant>, variableCount: Int, form: KarnaughForm): String {
        if (groups.size == 1 && groups.first().literalCount(variableCount) == 0) {
            return if (form == KarnaughForm.SOP) "F = 1" else "F = 0"
        }
        val names = listOf("A", "B", "C", "D")
        val terms = groups.map { group ->
            val literals = (0 until variableCount).mapNotNull { variableIndex ->
                val bit = 1 shl (variableCount - variableIndex - 1)
                if (group.mask and bit != 0) return@mapNotNull null
                val isOne = group.value and bit != 0
                when (form) {
                    KarnaughForm.SOP -> if (isOne) names[variableIndex] else "${names[variableIndex]}̅"
                    KarnaughForm.POS -> if (isOne) "${names[variableIndex]}̅" else names[variableIndex]
                }
            }
            when (form) {
                KarnaughForm.SOP -> literals.joinToString("·").ifEmpty { "1" }
                KarnaughForm.POS -> "(${literals.joinToString(" + ").ifEmpty { "0" }})"
            }
        }
        return "F = " + when (form) {
            KarnaughForm.SOP -> terms.joinToString(" + ")
            KarnaughForm.POS -> terms.joinToString(" · ")
        }
    }
}
