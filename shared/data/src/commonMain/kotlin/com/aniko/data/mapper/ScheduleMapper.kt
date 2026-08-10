package com.aniko.data.mapper

import com.aniko.data.dto.ReleaseDto
import com.aniko.data.dto.ScheduleResponseDto
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import com.aniko.network.ApiConfig

fun ScheduleResponseDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Schedule =
    Schedule(
        byDay =
            linkedMapOf(
                WeekDay.MONDAY to monday.toReleases(staticBaseUrl),
                WeekDay.TUESDAY to tuesday.toReleases(staticBaseUrl),
                WeekDay.WEDNESDAY to wednesday.toReleases(staticBaseUrl),
                WeekDay.THURSDAY to thursday.toReleases(staticBaseUrl),
                WeekDay.FRIDAY to friday.toReleases(staticBaseUrl),
                WeekDay.SATURDAY to saturday.toReleases(staticBaseUrl),
                WeekDay.SUNDAY to sunday.toReleases(staticBaseUrl),
            ),
    )

private fun List<ReleaseDto>.toReleases(staticBaseUrl: String): List<Release> = map { it.toDomain(staticBaseUrl) }
