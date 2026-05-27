package com.familycheckin.notifications

fun taskRewardMessage(childName: String, taskName: String, delta: Int, balance: Int): String {
    return "$childName 任务《$taskName》完成，积分 +$delta，当前积分 $balance"
}
