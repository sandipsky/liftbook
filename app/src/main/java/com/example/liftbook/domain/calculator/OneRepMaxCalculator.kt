package com.example.liftbook.domain.calculator

/**
 * Estimated one-rep max by the Epley formula: weight × (1 + reps / 30). The divisor is a
 * Double on purpose — `reps / 30` in integer arithmetic is 0 for anything under 30 reps, the
 * classic silent bug.
 */
fun oneRepMax(weightKg: Double, reps: Int): Double = weightKg * (1 + reps / 30.0)
