package com.familycheckin.points

fun canRedeem(balance: Int, requested: Int): Boolean {
    return requested >= 10 && requested < balance
}

fun cashForPoints(requested: Int, cashPerTenPoints: Int): Double {
    return (requested / 10.0) * cashPerTenPoints
}
