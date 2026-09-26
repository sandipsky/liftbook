package com.example.liftbook.core.unit

/*
 * Conversions for display only. Values are stored in kilograms and metres and are never
 * persisted converted (FR-6.1), so a pound value that round-trips through storage comes back
 * exactly as typed — nothing here rounds.
 */

/** Exact, by the 1959 international definition of the pound. */
const val KILOGRAMS_PER_POUND = 0.45359237

const val METERS_PER_KILOMETER = 1_000.0

/** Exact: 1 international mile = 1,609.344 m. */
const val METERS_PER_MILE = 1_609.344

fun kgToLb(kg: Double): Double = kg / KILOGRAMS_PER_POUND

fun lbToKg(lb: Double): Double = lb * KILOGRAMS_PER_POUND

fun metersToKilometers(meters: Double): Double = meters / METERS_PER_KILOMETER

fun metersToMiles(meters: Double): Double = meters / METERS_PER_MILE

fun kilometersToMeters(kilometers: Double): Double = kilometers * METERS_PER_KILOMETER

fun milesToMeters(miles: Double): Double = miles * METERS_PER_MILE
