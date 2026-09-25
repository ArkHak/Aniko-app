package com.aniko.data.update

/**
 * Версия приложения по SemVer (`MAJOR.MINOR.PATCH[-предрелиз]`).
 *
 * Порядок как в SemVer: сначала числа, затем релиз (`preRelease == null`) старше любого
 * предрелиза, а предрелизы сравниваются по точечным идентификаторам (числовые — как числа и меньше
 * буквенных). Метаданные сборки (`+...`) при разборе отбрасываются.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val core = compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
        if (core != 0) return core
        return comparePreRelease(preRelease, other.preRelease)
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        /** `v0.1.0`, `0.1.0`, `1.2.3-beta.1` → версия; всё остальное (в том числе `1.2`) → `null`. */
        fun parse(raw: String): AppVersion? = PATTERN.matchEntire(raw.trim())?.let { fromGroups(it.groupValues) }

        private const val GROUP_MAJOR = 1
        private const val GROUP_MINOR = 2
        private const val GROUP_PATCH = 3
        private const val GROUP_PRE_RELEASE = 4

        private fun fromGroups(groups: List<String>): AppVersion? {
            val major = groups[GROUP_MAJOR].toIntOrNull()
            val minor = groups[GROUP_MINOR].toIntOrNull()
            val patch = groups[GROUP_PATCH].toIntOrNull()
            return if (major == null || minor == null || patch == null) {
                null
            } else {
                AppVersion(major, minor, patch, groups[GROUP_PRE_RELEASE].ifEmpty { null })
            }
        }

        private fun comparePreRelease(
            a: String?,
            b: String?,
        ): Int =
            when {
                a == b -> 0
                a == null -> 1 // релиз (без суффикса) старше предрелиза
                b == null -> -1
                else -> compareIdentifiers(a.split('.'), b.split('.'))
            }

        private fun compareIdentifiers(
            left: List<String>,
            right: List<String>,
        ): Int =
            left
                .zip(right)
                .map { (x, y) -> compareIdentifier(x, y) }
                .firstOrNull { it != 0 } ?: left.size.compareTo(right.size)

        private fun compareIdentifier(
            a: String,
            b: String,
        ): Int {
            val na = a.toLongOrNull()
            val nb = b.toLongOrNull()
            return when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1 // числовой идентификатор меньше буквенного
                nb != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
