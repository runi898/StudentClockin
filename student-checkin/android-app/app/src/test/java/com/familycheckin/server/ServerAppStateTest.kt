package com.familycheckin.server

import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import com.familycheckin.parent.ParentDashboardFocus
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerAppStateTest {
    @Test
    fun parentSignupLoadsWorkspaceAndCreatedChildAccount() = runBlocking {
        val gateway = FakeServerGateway().apply {
            signUpResult = ServerSignUpResult(
                memberContext = ServerMemberContext(
                    profileId = "profile-parent",
                    familyId = "family-1",
                    memberId = "member-parent",
                    role = "parent",
                    displayName = "濡堝",
                    email = "parent@example.com",
                    cashCnyPer10Points = 2.0,
                    minRedeemPoints = 10,
                    familyName = "李家任务表",
                    timezone = "Asia/Shanghai",
                    mediaRetentionDays = 30
                ),
                createdChild = ServerChildAccount(
                    memberId = "member-child-1",
                    childName = "灏忓畤",
                    email = "xiaoyu@example.com",
                    createdAt = "2026-05-29T01:00:00Z"
                )
            )
            familyChildren = listOf(
                ServerFamilyChild(
                    id = "member-child-1",
                    childDisplayName = "灏忓畤",
                    role = "child"
                )
            )
            childAccounts = listOf(
                ServerChildAccount(
                    memberId = "member-child-1",
                    childName = "灏忓畤",
                    email = "xiaoyu@example.com",
                    createdAt = "2026-05-29T01:00:00Z"
                )
            )
        }

        val state = ServerAppState(gateway)

        val role = state.signUpParent(
            familyName = "李家任务表",
            parentName = "濡堝",
            parentEmail = "parent@example.com",
            parentPassword = "Parent123!",
            firstChildName = "灏忓畤",
            firstChildEmail = "xiaoyu@example.com",
            firstChildPassword = "Child123!"
        )

        assertEquals(ServerRole.PARENT, role)
        assertEquals("李家任务表", state.familyName)
        assertEquals("濡堝", state.childName)
        assertEquals("parent@example.com", state.parentEmail)
        assertEquals(30, state.mediaRetentionDays)
        assertEquals(1, state.childAccounts.size)
        assertEquals("灏忓畤", state.childAccounts.first().childName)
        assertEquals("xiaoyu@example.com", state.childAccounts.first().email)
        assertEquals("李家任务表", gateway.lastSignUpRequest?.familyName)
        assertEquals("灏忓畤", gateway.lastSignUpRequest?.firstChildName)
    }

    @Test
    fun childLoginLoadsTasksLedgerAndFamilySettings() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "灏忓畤",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 2.0,
                minRedeemPoints = 10,
                familyName = "娴嬭瘯瀹跺涵",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            childTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-1",
                    taskNameSnapshot = "鏁板鍙ｇ畻",
                    modeSnapshot = "stopwatch",
                    deliveryRequirementSnapshot = "photo",
                    pointValueSnapshot = 2,
                    targetDurationSecondsSnapshot = 900,
                    scheduledTimeLocal = "18:00",
                    status = "completed",
                    startedAt = "2026-05-27T10:00:00Z",
                    completedAt = "2026-05-27T10:12:30Z",
                    actualDurationSeconds = 750
                )
            )
            ledgerEntries = listOf(
                ServerLedgerEntry(
                    id = "ledger-1",
                    createdAt = "2026-05-27T09:00:00Z",
                    changeType = "manual_adjust",
                    pointsDelta = 20,
                    balanceAfter = 20,
                    note = "鍒濆绉垎"
                )
            )
            dailyReports = listOf(
                ServerDailyChildReport(
                    childMemberId = "member-child",
                    localDate = "2026-05-27",
                    taskTotalCount = 1,
                    taskCompletedCount = 1
                ),
                ServerDailyChildReport(
                    childMemberId = "member-child",
                    localDate = "2026-05-26",
                    taskTotalCount = 2,
                    taskCompletedCount = 1
                )
            )
            submissions = listOf(
                ServerTaskSubmission(
                    id = "submission-1",
                    occurrenceId = "occ-1",
                    submissionType = "photo",
                    storagePath = "occ-1/demo.jpg",
                    uploadedAt = "2026-05-27T10:13:00Z"
                )
            )
        }

        val state = ServerAppState(gateway)

        val role = state.login("child1@familycheckin.local", "Child123!")

        assertEquals(ServerRole.CHILD, role)
        assertEquals("灏忓畤", state.childName)
        assertEquals("child1@familycheckin.local", state.parentEmail)
        assertEquals("娴嬭瘯瀹跺涵", state.familyName)
        assertEquals(30, state.mediaRetentionDays)
        assertEquals(2, state.cashPerTenPoints)
        assertEquals(10, state.minRedeemPoints)
        assertEquals(20, state.currentPoints)
        assertEquals(1, state.tasks.size)
        assertEquals("鏁板鍙ｇ畻", state.tasks.first().title)
        assertEquals(TaskMode.STOPWATCH, state.tasks.first().mode)
        assertEquals(DeliveryRequirement.PHOTO, state.tasks.first().deliveryRequirement)
        assertEquals(TaskStatus.COMPLETED, state.tasks.first().status)
        assertEquals("鍒濆绉垎", state.ledger.first().title)
        assertEquals("1 / 1", "${state.childSummary.todayCompleted} / ${state.childSummary.todayTotal}")
        assertEquals(1, state.submissionsForTask("occ-1").size)
    }

    @Test
    fun parentLoginLoadsRequestsStatsAndChildAccounts() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "瀹堕暱",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 2.0,
                minRedeemPoints = 10,
                familyName = "娴嬭瘯瀹跺涵",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(
                    id = "member-child",
                    childDisplayName = "灏忓畤",
                    role = "child"
                )
            )
            parentTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-2",
                    taskNameSnapshot = "鑻辫璺熻",
                    modeSnapshot = "countdown",
                    deliveryRequirementSnapshot = "audio",
                    pointValueSnapshot = 3,
                    targetDurationSecondsSnapshot = 1200,
                    scheduledTimeLocal = "19:00",
                    status = "pending",
                    childName = "灏忓畤"
                )
            )
            redemptions = listOf(
                ServerRedemptionRequest(
                    id = "redeem-1",
                    childName = "灏忓畤",
                    pointsRequested = 10,
                    cashAmountCny = 2.0,
                    requestedAt = "2026-05-27T11:00:00Z",
                    status = "pending"
                )
            )
            redemptionStats = ServerRedemptionStats(
                last7DaysCount = 2,
                last7DaysCashCny = 4.0,
                currentMonthCount = 5,
                currentMonthCashCny = 10.0
            )
            dailyReports = listOf(
                ServerDailyChildReport(
                    childMemberId = "member-child",
                    localDate = "2026-05-27",
                    taskTotalCount = 1,
                    taskCompletedCount = 0
                ),
                ServerDailyChildReport(
                    childMemberId = "member-child",
                    localDate = "2026-05-26",
                    taskTotalCount = 2,
                    taskCompletedCount = 1
                )
            )
            submissions = listOf(
                ServerTaskSubmission(
                    id = "submission-1",
                    occurrenceId = "occ-2",
                    submissionType = "audio",
                    storagePath = "occ-2/demo.m4a",
                    uploadedAt = "2026-05-27T11:13:00Z"
                )
            )
            childAccounts = listOf(
                ServerChildAccount(
                    memberId = "member-child",
                    childName = "灏忓畤",
                    email = "child1@familycheckin.local",
                    createdAt = "2026-05-27T10:30:00Z"
                )
            )
        }

        val state = ServerAppState(gateway)

        val role = state.login("parent@familycheckin.local", "Parent123!")

        assertEquals(ServerRole.PARENT, role)
        assertEquals(1, state.tasks.size)
        assertEquals(1, state.redemptionRequests.size)
        assertEquals(2, state.parentStats.last7DaysCount)
        assertEquals(10.0, state.parentStats.currentMonthCashCny)
        assertEquals(1, state.parentChildren.size)
        assertEquals("灏忓畤", state.parentChildren.first().childName)
        assertEquals("parent@familycheckin.local", state.parentEmail)
        assertEquals(30, state.mediaRetentionDays)
        assertEquals(1, state.childAccounts.size)
        assertEquals("0 / 1", state.parentOverview.todaySummaryText)
        assertEquals(1, state.parentOverview.pendingCount)
        assertEquals(1, state.parentTaskRows.size)
        assertEquals("鑻辫璺熻", state.parentTaskRows.first().title)
        assertEquals(1, state.submissionsForTask("occ-2").size)
    }

    @Test
    fun taskInsightLoadsYesterdayAndWindowStats() = runBlocking {
        val beijingToday = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val yesterdayTimestamp = "${beijingToday.minusDays(1)}T00:00:00+08:00"
        val gateway = FakeServerGateway().apply {
            taskHistoryDetails["occ-1"] = ServerTaskHistoryDetail(
                taskTitle = "鏁板鍙ｇ畻",
                occurrences = listOf(
                    ServerTaskHistoryOccurrence(
                        localDate = yesterdayTimestamp,
                        status = "completed",
                        completedAt = "2026-06-01T10:00:00Z"
                    ),
                    ServerTaskHistoryOccurrence(
                        localDate = beijingToday.minusDays(2).toString(),
                        status = "pending"
                    ),
                    ServerTaskHistoryOccurrence(
                        localDate = beijingToday.minusDays(3).toString(),
                        status = "completed",
                        completedAt = "2026-05-30T10:00:00Z"
                    ),
                    ServerTaskHistoryOccurrence(
                        localDate = beijingToday.minusDays(24).toString(),
                        status = "completed",
                        completedAt = "2026-05-10T10:00:00Z"
                    ),
                    ServerTaskHistoryOccurrence(
                        localDate = beijingToday.minusDays(113).toString(),
                        status = "pending"
                    )
                )
            )
        }

        val state = ServerAppState(gateway)
        state.toggleChildTaskInsight("occ-1")

        assertEquals("occ-1", state.selectedChildTaskId)
        assertEquals("鏁板鍙ｇ畻", state.selectedChildTaskInsight?.taskTitle)
        assertEquals("1 / 1", "${state.selectedChildTaskInsight?.yesterdayStat?.completedCount} / ${state.selectedChildTaskInsight?.yesterdayStat?.totalCount}")
        assertEquals("67%", state.selectedChildTaskInsight?.last7DaysStat?.completionRateText)
        assertEquals("75%", state.selectedChildTaskInsight?.last30DaysStat?.completionRateText)
    }

    @Test
    fun parentFilteringUsesChildMemberIdInsteadOfChildName() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "家长",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "测试家庭",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(id = "child-dd", childDisplayName = "弟弟", role = "child"),
                ServerFamilyChild(id = "child-jj", childDisplayName = "姐姐", role = "child")
            )
            parentTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-dd",
                    taskTemplateId = "template-dd",
                    childMemberId = "child-dd",
                    taskNameSnapshot = "弟弟专属任务",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 5,
                    status = "completed",
                    childName = "姐姐"
                ),
                ServerTaskOccurrence(
                    id = "occ-jj",
                    taskTemplateId = "template-jj",
                    childMemberId = "child-jj",
                    taskNameSnapshot = "姐姐专属任务",
                    modeSnapshot = "countdown",
                    deliveryRequirementSnapshot = "photo",
                    pointValueSnapshot = 3,
                    status = "pending",
                    childName = "弟弟"
                )
            )
            dailyReports = listOf(
                ServerDailyChildReport(
                    childMemberId = "child-dd",
                    localDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                    taskTotalCount = 1,
                    taskCompletedCount = 1
                ),
                ServerDailyChildReport(
                    childMemberId = "child-jj",
                    localDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                    taskTotalCount = 1,
                    taskCompletedCount = 0
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("parent@familycheckin.local", "Parent123!")

        val littleBrother = state.parentChildren.first { it.memberId == "child-dd" }
        val bigSister = state.parentChildren.first { it.memberId == "child-jj" }

        assertEquals(1, littleBrother.todayTotal)
        assertEquals(1, littleBrother.todayCompleted)
        assertEquals(1, bigSister.todayTotal)
        assertEquals(0, bigSister.todayCompleted)

        state.selectParentFocus(ParentDashboardFocus.TODAY)
        state.selectChild("child-dd")
        assertEquals(listOf("弟弟专属任务"), state.parentTaskRows.map { it.title })

        state.selectChild("child-jj")
        assertEquals(listOf("姐姐专属任务"), state.parentTaskRows.map { it.title })
    }

    @Test
    fun deleteTaskRefreshesParentRowsWhenGatewayRemovesTemplate() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "家长",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "测试家庭",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(
                    id = "member-child",
                    childDisplayName = "孩子",
                    role = "child"
                )
            )
            parentTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-1",
                    taskTemplateId = "template-1",
                    taskNameSnapshot = "閺佹澘顒熼崣锝堫吀",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "pending",
                    childName = "孩子"
                ),
                ServerTaskOccurrence(
                    id = "occ-2",
                    taskTemplateId = "template-2",
                    taskNameSnapshot = "閼昏精顕㈢捄鐔活嚢",
                    modeSnapshot = "countdown",
                    deliveryRequirementSnapshot = "photo",
                    pointValueSnapshot = 3,
                    status = "pending",
                    childName = "孩子"
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("parent@familycheckin.local", "Parent123!")

        state.deleteTask("occ-1")

        assertEquals(listOf("occ-1"), gateway.deletedTaskOccurrenceIds)
        assertEquals(1, state.parentTaskRows.size)
        assertEquals("template-2", state.parentTaskRows.first().taskTemplateId)
    }

    @Test
    fun deleteTaskOnlyRemovesSelectedOccurrenceWhenLegacyRowsShareTemplate() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "瀹堕暱",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "娴嬭瘯瀹跺涵",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(id = "child-dd", childDisplayName = "寮熷紵", role = "child"),
                ServerFamilyChild(id = "child-jj", childDisplayName = "濮愬", role = "child")
            )
            parentTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-dd",
                    taskTemplateId = "shared-template",
                    childMemberId = "child-dd",
                    taskNameSnapshot = "闃呰",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "pending",
                    childName = "寮熷紵"
                ),
                ServerTaskOccurrence(
                    id = "occ-jj",
                    taskTemplateId = "shared-template",
                    childMemberId = "child-jj",
                    taskNameSnapshot = "闃呰",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "completed",
                    childName = "濮愬"
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("parent@familycheckin.local", "Parent123!")

        state.deleteTask("occ-dd")

        assertEquals(listOf("occ-dd"), gateway.deletedTaskOccurrenceIds)
        assertEquals(listOf("occ-jj"), gateway.parentTasks.map { it.id })
        assertEquals("1 / 1", state.parentOverview.todaySummaryText)
        assertTrue(state.parentChildren.any { it.todayCompleted == 1 && it.todayTotal == 1 })
    }

    @Test
    fun addTaskSendsAssignedChildMemberIdToGateway() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "家长",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "测试家庭",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(id = "child-dd", childDisplayName = "弟弟", role = "child"),
                ServerFamilyChild(id = "child-jj", childDisplayName = "姐姐", role = "child")
            )
        }

        val state = ServerAppState(gateway)
        state.login("parent@familycheckin.local", "Parent123!")

        state.addTask(
            childMemberId = "child-dd",
            title = "阅读打卡",
            mode = TaskMode.CHECK_ONLY,
            deliveryRequirement = DeliveryRequirement.NONE,
            points = 2,
            targetMinutes = null,
            scheduledTimeLabel = "20:00"
        )

        assertEquals("child-dd", gateway.lastCreatedTaskChildMemberId)
        assertEquals("阅读打卡", gateway.lastCreatedTaskName)
    }


    @Test
    fun createChildAccountRefreshesParentAccountList() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-parent",
                familyId = "family-1",
                memberId = "member-parent",
                role = "parent",
                displayName = "瀹堕暱",
                email = "parent@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "鎴戠殑瀹跺涵",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            familyChildren = listOf(
                ServerFamilyChild(
                    id = "member-child-1",
                    childDisplayName = "灏忓畤",
                    role = "child"
                )
            )
            childAccounts = listOf(
                ServerChildAccount(
                    memberId = "member-child-1",
                    childName = "灏忓畤",
                    email = "xiaoyu@example.com",
                    createdAt = "2026-05-29T01:00:00Z"
                )
            )
            createdChildAccount = ServerChildAccount(
                memberId = "member-child-2",
                childName = "灏忛洦",
                email = "xiaoyu2@example.com",
                createdAt = "2026-05-29T02:00:00Z"
            )
        }

        val state = ServerAppState(gateway)
        state.login("parent@familycheckin.local", "Parent123!")

        gateway.familyChildren = gateway.familyChildren + ServerFamilyChild(
            id = "member-child-2",
            childDisplayName = "灏忛洦",
            role = "child"
        )
        gateway.childAccounts = gateway.childAccounts + ServerChildAccount(
            memberId = "member-child-2",
            childName = "灏忛洦",
            email = "xiaoyu2@example.com",
            createdAt = "2026-05-29T02:00:00Z"
        )

        state.createChildAccount(
            childName = "灏忛洦",
            childEmail = "xiaoyu2@example.com",
            childPassword = "Child456!"
        )

        assertEquals("灏忛洦", gateway.lastCreatedChildRequest?.childName)
        assertEquals("xiaoyu2@example.com", gateway.lastCreatedChildRequest?.childEmail)
        assertEquals(2, state.childAccounts.size)
        assertEquals("灏忛洦", state.childAccounts.last().childName)
    }

    @Test
    fun completeTaskSendsTaskCompletionNotification() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "灏忓畤",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10
            )
            childTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-1",
                    taskNameSnapshot = "鏁板鍙ｇ畻",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "pending"
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("child1@familycheckin.local", "Child123!")

        state.completeTask("occ-1")

        assertTrue(gateway.lastNotificationMessage?.contains("完成任务") == true)
        assertTrue(gateway.lastNotificationMessage?.contains("积分 +2") == true)
    }

    @Test
    fun submitTaskDeliverySendsSubmissionNotification() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "灏忓畤",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10
            )
            childTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-1",
                    taskNameSnapshot = "鑻辫璺熻",
                    modeSnapshot = "countdown",
                    deliveryRequirementSnapshot = "photo",
                    pointValueSnapshot = 3,
                    status = "pending"
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("child1@familycheckin.local", "Child123!")

        state.submitTaskDelivery(
            taskId = "occ-1",
            submissionType = "photo",
            fileName = "demo.jpg",
            contentType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3)
        )

        assertTrue(gateway.lastNotificationMessage?.contains("已提交") == true)
        assertTrue(gateway.lastNotificationMessage?.contains("积分 +3") == true)
    }

    @Test
    fun switchAccountClearsLoadedSessionState() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "孩子",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 2.0,
                minRedeemPoints = 10,
                familyName = "测试家庭",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            ledgerEntries = listOf(
                ServerLedgerEntry(
                    id = "ledger-1",
                    createdAt = "2026-05-27T09:00:00Z",
                    changeType = "manual_adjust",
                    pointsDelta = 20,
                    balanceAfter = 20,
                    note = "閸掓繂顫愮粔顖氬瀻"
                )
            )
        }

        val state = ServerAppState(gateway)
        state.login("child1@familycheckin.local", "Child123!")

        state.switchAccount()

        assertNull(state.currentRole)
        assertEquals("", state.childName)
        assertEquals(0, state.ledger.size)
        assertEquals(0, state.currentPoints)
        assertTrue(gateway.sessionCleared)
    }

    @Test
    fun offlineCompleteTaskQueuesActionAndUpdatesLocalState() = runBlocking {
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "小明",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "测试家庭",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            childTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-offline-1",
                    taskNameSnapshot = "离线数学任务",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "pending"
                )
            )
            ledgerEntries = emptyList()
            failCompleteTaskWith = IOException("offline")
        }
        val offlineStore = InMemoryOfflineStateStore()
        val state = ServerAppState(
            gateway = gateway,
            offlineStore = offlineStore,
            pendingMediaStore = InMemoryPendingMediaStore()
        )

        state.login("child1@familycheckin.local", "Child123!")
        state.completeTask("occ-offline-1")

        assertEquals(TaskStatus.COMPLETED, state.tasks.first().status)
        assertEquals(2, state.currentPoints)
        assertEquals(1, offlineStore.pendingActions().size)
        assertTrue(state.flashMessage.contains("离线"))
    }

    @Test
    fun completeTaskFastDrainsActionsQueuedWhileSyncAlreadyRunning() = runBlocking {
        val firstCallEntered = CountDownLatch(1)
        val releaseFirstCall = CountDownLatch(1)
        val gateway = FakeServerGateway().apply {
            memberContext = ServerMemberContext(
                profileId = "profile-child",
                familyId = "family-1",
                memberId = "member-child",
                role = "child",
                displayName = "灏忔槑",
                email = "child1@familycheckin.local",
                cashCnyPer10Points = 1.0,
                minRedeemPoints = 10,
                familyName = "娴嬭瘯瀹跺涵",
                timezone = "Asia/Shanghai",
                mediaRetentionDays = 30
            )
            childTasks = listOf(
                ServerTaskOccurrence(
                    id = "occ-fast-1",
                    taskNameSnapshot = "璇枃",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 2,
                    status = "pending"
                ),
                ServerTaskOccurrence(
                    id = "occ-fast-2",
                    taskNameSnapshot = "鏁板",
                    modeSnapshot = "check_only",
                    deliveryRequirementSnapshot = "none",
                    pointValueSnapshot = 3,
                    status = "pending"
                )
            )
            onCompleteTask = { occurrenceId ->
                if (occurrenceId == "occ-fast-1") {
                    firstCallEntered.countDown()
                    releaseFirstCall.await(2, TimeUnit.SECONDS)
                }
            }
        }
        val offlineStore = InMemoryOfflineStateStore()
        val state = ServerAppState(
            gateway = gateway,
            offlineStore = offlineStore,
            pendingMediaStore = InMemoryPendingMediaStore()
        )

        state.login("child1@familycheckin.local", "Child123!")

        val firstAction = async { state.completeTaskFast("occ-fast-1") }
        firstAction.await()
        assertTrue(firstCallEntered.await(1, TimeUnit.SECONDS))

        state.completeTaskFast("occ-fast-2")
        assertEquals(TaskStatus.COMPLETED, state.tasks.first { it.id == "occ-fast-1" }.status)
        assertEquals(TaskStatus.COMPLETED, state.tasks.first { it.id == "occ-fast-2" }.status)
        assertEquals(5, state.currentPoints)

        releaseFirstCall.countDown()
        delay(250)

        assertEquals(0, offlineStore.pendingActions().size)
        assertEquals(2, gateway.completeTaskCalls)
    }

    @Test
    fun initializeFromCacheRestoresChildSnapshotWithoutNetwork() = runBlocking {
        val offlineStore = InMemoryOfflineStateStore().apply {
            saveSnapshot(
                OfflineAppSnapshot(
                    memberContext = ServerMemberContext(
                        profileId = "profile-child",
                        familyId = "family-1",
                        memberId = "member-child",
                        role = "child",
                        displayName = "小明",
                        email = "child1@familycheckin.local",
                        cashCnyPer10Points = 2.0,
                        minRedeemPoints = 10,
                        familyName = "测试家庭",
                        timezone = "Asia/Shanghai",
                        mediaRetentionDays = 30
                    ),
                    childSourceTasks = listOf(
                        ServerTaskOccurrence(
                            id = "occ-cached-1",
                            taskNameSnapshot = "缓存任务",
                            modeSnapshot = "countdown",
                            deliveryRequirementSnapshot = "photo",
                            pointValueSnapshot = 3,
                            targetDurationSecondsSnapshot = 600,
                            scheduledTimeLocal = "20:00",
                            status = "running",
                            startedAt = "2026-06-05T10:00:00Z"
                        )
                    ),
                    parentSourceTasks = emptyList(),
                    ledgerEntries = listOf(
                        ServerLedgerEntry(
                            id = "ledger-cached-1",
                            createdAt = "2026-06-05T10:00:00Z",
                            changeType = "task_reward",
                            pointsDelta = 3,
                            balanceAfter = 12,
                            note = "缓存任务"
                        )
                    ),
                    redemptions = emptyList(),
                    redemptionStats = ServerRedemptionStats(0, 0.0, 0, 0.0),
                    familyChildren = emptyList(),
                    childAccounts = emptyList(),
                    notificationChannels = emptyList(),
                    dailyReports = listOf(
                        ServerDailyChildReport(
                            childMemberId = "member-child",
                            localDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                            taskTotalCount = 1,
                            taskCompletedCount = 0
                        )
                    ),
                    submissions = emptyList()
                )
            )
        }

        val state = ServerAppState(
            gateway = FakeServerGateway(),
            offlineStore = offlineStore,
            pendingMediaStore = InMemoryPendingMediaStore()
        )

        val restored = state.initializeFromCache()

        assertTrue(restored)
        assertEquals(ServerRole.CHILD, state.currentRole)
        assertEquals("小明", state.childName)
        assertEquals(1, state.tasks.size)
        assertEquals("缓存任务", state.tasks.first().title)
        assertEquals(12, state.currentPoints)
    }
}

private class FakeServerGateway : ServerGateway {
    var memberContext = ServerMemberContext(
        profileId = "",
        familyId = "",
        memberId = "",
        role = "child",
        displayName = "",
        email = "",
        cashCnyPer10Points = 1.0,
        minRedeemPoints = 10
    )
    var signUpResult = ServerSignUpResult(memberContext = memberContext, createdChild = null)
    var childTasks: List<ServerTaskOccurrence> = emptyList()
    var parentTasks: List<ServerTaskOccurrence> = emptyList()
    var ledgerEntries: List<ServerLedgerEntry> = emptyList()
    var redemptions: List<ServerRedemptionRequest> = emptyList()
    var redemptionStats: ServerRedemptionStats = ServerRedemptionStats(0, 0.0, 0, 0.0)
    var familyChildren: List<ServerFamilyChild> = emptyList()
    var childAccounts: List<ServerChildAccount> = emptyList()
    var notificationChannels: List<ServerNotificationChannel> = emptyList()
    var dailyReports: List<ServerDailyChildReport> = emptyList()
    var submissions: List<ServerTaskSubmission> = emptyList()
    var taskHistoryDetails: MutableMap<String, ServerTaskHistoryDetail> = mutableMapOf()
    var sessionCleared = false
    val deletedTaskTemplateIds = mutableListOf<String>()
    val deletedTaskOccurrenceIds = mutableListOf<String>()
    var createdChildAccount = ServerChildAccount(
        memberId = "child-created",
        childName = "新孩子",
        email = "child-created@example.com",
        createdAt = "2026-05-29T01:00:00Z"
    )
    var lastSignUpRequest: ParentSignUpRequest? = null
    var lastCreatedChildRequest: CreateChildAccountRequest? = null
    var lastCreatedTaskChildMemberId: String? = null
    var lastCreatedTaskName: String? = null
    var lastNotificationMessage: String? = null
    var lastSubmissionType: String? = null
    var failCompleteTaskWith: IOException? = null
    var completeTaskCalls = 0
    var onCompleteTask: (suspend (String) -> Unit)? = null

    override suspend fun signIn(email: String, password: String): ServerMemberContext = memberContext

    override suspend fun signUpParent(request: ParentSignUpRequest): ServerSignUpResult {
        lastSignUpRequest = request
        memberContext = signUpResult.memberContext
        return signUpResult
    }

    override suspend fun requestPasswordReset(email: String) = Unit

    override suspend fun restoreSession(): ServerMemberContext? = memberContext

    override fun clearSession() {
        sessionCleared = true
    }

    override suspend fun childTodaySnapshot(): List<ServerTaskOccurrence> = childTasks

    override suspend fun parentTodaySnapshot(): List<ServerTaskOccurrence> = parentTasks

    override suspend fun pointLedger(): List<ServerLedgerEntry> = ledgerEntries

    override suspend fun parentRedemptionList(): List<ServerRedemptionRequest> = redemptions

    override suspend fun parentRedemptionStats(): ServerRedemptionStats = redemptionStats

    override suspend fun familyChildren(): List<ServerFamilyChild> = familyChildren

    override suspend fun childAccounts(): List<ServerChildAccount> = childAccounts
    override suspend fun notificationChannels(): List<ServerNotificationChannel> = notificationChannels

    override suspend fun dailyChildReports(limitDays: Int): List<ServerDailyChildReport> = dailyReports

    override suspend fun childTaskHistory(occurrenceId: String, lookbackDays: Int): ServerTaskHistoryDetail {
        return taskHistoryDetails[occurrenceId] ?: ServerTaskHistoryDetail(taskTitle = "", occurrences = emptyList())
    }

    override suspend fun taskSubmissions(): List<ServerTaskSubmission> = submissions

    override suspend fun createTaskSubmission(
        occurrenceId: String,
        submissionType: String,
        storagePath: String
    ) {
        lastSubmissionType = submissionType
    }

    override suspend fun uploadTaskFile(
        storagePath: String,
        bytes: ByteArray,
        contentType: String
    ) = Unit

    override suspend fun startTask(occurrenceId: String) = Unit

    override suspend fun completeTask(occurrenceId: String) {
        completeTaskCalls += 1
        onCompleteTask?.invoke(occurrenceId)
        failCompleteTaskWith?.let { throw it }
    }

    override suspend fun finishTask(occurrenceId: String, actualDurationSeconds: Int) = Unit

    override suspend fun submitRedemption(pointsRequested: Int) = Unit

    override suspend fun reviewRedemption(requestId: String, approve: Boolean) = Unit

    override suspend fun sendNotification(familyId: String, messageText: String) {
        lastNotificationMessage = messageText
    }

    override suspend fun createChildAccount(request: CreateChildAccountRequest): ServerChildAccount {
        lastCreatedChildRequest = request
        return createdChildAccount
    }

    override suspend fun resetChildPassword(memberId: String, newPassword: String) = Unit

    override suspend fun deleteChildAccount(memberId: String) = Unit

    override suspend fun saveNotificationChannel(
        channelId: String?,
        channelType: String,
        webhookUrl: String,
        isEnabled: Boolean
    ) = Unit

    override suspend fun updateFamilySettings(
        familyName: String,
        cashCnyPer10Points: Double,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) = Unit

    override suspend fun updateChildProfile(memberId: String, childName: String) = Unit

    override suspend fun createQuickTask(
        childMemberId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    ) {
        lastCreatedTaskChildMemberId = childMemberId
        lastCreatedTaskName = name
    }

    override suspend fun updateTaskTemplate(
        taskTemplateId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    ) = Unit

    override suspend fun deleteTaskTemplate(taskTemplateId: String) {
        deletedTaskTemplateIds += taskTemplateId
        parentTasks = parentTasks.filterNot { it.taskTemplateId == taskTemplateId }
        childTasks = childTasks.filterNot { it.taskTemplateId == taskTemplateId }
    }

    override suspend fun deleteTaskOccurrence(occurrenceId: String) {
        deletedTaskOccurrenceIds += occurrenceId
        parentTasks = parentTasks.filterNot { it.id == occurrenceId && it.status != "completed" }
        childTasks = childTasks.filterNot { it.id == occurrenceId && it.status != "completed" }
    }

    override suspend fun resetTodayOccurrences() = Unit
}

private class InMemoryOfflineStateStore : OfflineStateStore {
    private val snapshots = linkedMapOf<String, OfflineAppSnapshot>()
    private val actionsByMember = linkedMapOf<String, List<PendingSyncAction>>()
    private var activeMemberId: String? = null

    override fun loadSnapshot(memberId: String?): OfflineAppSnapshot? {
        val resolvedMemberId = memberId ?: activeMemberId ?: return null
        return snapshots[resolvedMemberId]
    }

    override fun saveSnapshot(snapshot: OfflineAppSnapshot) {
        val memberId = snapshot.memberContext.memberId
        snapshots[memberId] = snapshot
        activeMemberId = memberId
    }

    override fun clearSnapshot(memberId: String?) {
        val resolvedMemberId = memberId ?: activeMemberId
        if (resolvedMemberId != null) {
            snapshots.remove(resolvedMemberId)
            if (activeMemberId == resolvedMemberId) {
                activeMemberId = null
            }
        } else {
            snapshots.clear()
            activeMemberId = null
        }
    }

    override fun loadPendingActions(memberId: String?): List<PendingSyncAction> {
        val resolvedMemberId = memberId ?: activeMemberId ?: return emptyList()
        return actionsByMember[resolvedMemberId].orEmpty()
    }

    override fun savePendingActions(memberId: String, actions: List<PendingSyncAction>) {
        actionsByMember[memberId] = actions
        activeMemberId = memberId
    }

    override fun clearPendingActions(memberId: String?) {
        val resolvedMemberId = memberId ?: activeMemberId
        if (resolvedMemberId != null) {
            actionsByMember.remove(resolvedMemberId)
        } else {
            actionsByMember.clear()
        }
    }

    fun pendingActions(memberId: String? = activeMemberId): List<PendingSyncAction> {
        val resolvedMemberId = memberId ?: return emptyList()
        return actionsByMember[resolvedMemberId].orEmpty()
    }
}

private class InMemoryPendingMediaStore : PendingMediaStore {
    private val values = linkedMapOf<String, ByteArray>()

    override fun save(fileName: String, bytes: ByteArray): String {
        val key = "test-$fileName-${values.size + 1}"
        values[key] = bytes
        return key
    }

    override fun read(key: String): ByteArray? = values[key]

    override fun delete(key: String) {
        values.remove(key)
    }

    override fun clear() {
        values.clear()
    }
}
