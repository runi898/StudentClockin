package com.familycheckin.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationMessageTest {
    @Test
    fun formatsTaskRewardMessage() {
        val actual = taskRewardMessage("小宇", "阅读 20 分钟", 1, 21)
        assertEquals("小宇 任务《阅读 20 分钟》完成，积分 +1，当前积分 21", actual)
    }
}
