package com.familycheckin.tasks

import java.time.Instant
import java.time.ZoneId

private val SHANGHAI = ZoneId.of("Asia/Shanghai")

fun occurrenceLocalDate(timestamp: String): String {
    return Instant.parse(timestamp).atZone(SHANGHAI).toLocalDate().toString()
}
