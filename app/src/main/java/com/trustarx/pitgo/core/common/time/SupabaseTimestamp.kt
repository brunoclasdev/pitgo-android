package com.trustarx.pitgo.core.common.time

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Parses PostgreSQL/Supabase timestamptz values consistently on desugared Android runtimes. */
fun parseSupabaseInstant(value: String): Instant {
    val timestamp = value.trim()
    val normalizedUtc = when {
        timestamp.endsWith("+00:00") -> timestamp.dropLast(6) + "Z"
        timestamp.endsWith("+00") -> timestamp.dropLast(3) + "Z"
        else -> timestamp
    }
    return runCatching { Instant.parse(normalizedUtc) }
        .getOrElse {
            OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        }
}
