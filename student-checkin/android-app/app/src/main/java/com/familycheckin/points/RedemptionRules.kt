package com.familycheckin.points

fun canRedeem(balance: Int, requested: Int, minRedeemPoints: Int = 10): Boolean {
    return requested >= minRedeemPoints && requested <= balance
}

fun cashForPoints(requested: Int, cashPerTenPoints: Int): Double {
    return (requested / 10.0) * cashPerTenPoints
}
