package com.example.moneymanager.wellbeing

/**
 * Coarse package → category mapping.
 * Prefer small defaults + heuristics; unknown packages map to [OTHER].
 * Callers may pass user overrides (package → category label).
 */
object UsageCategoryMapper {

    const val SOCIAL = "Social"
    const val WORK = "Work"
    const val ENTERTAINMENT = "Entertainment"
    const val OTHER = "Other"

    val ALL_CATEGORIES = listOf(SOCIAL, WORK, ENTERTAINMENT, OTHER)

    /** Small, intentional defaults — not a Play Store dump. */
    private val DEFAULTS: Map<String, String> = mapOf(
        "com.instagram.android" to SOCIAL,
        "com.whatsapp" to SOCIAL,
        "org.telegram.messenger" to SOCIAL,
        "com.facebook.katana" to SOCIAL,
        "com.facebook.orca" to SOCIAL,
        "com.twitter.android" to SOCIAL,
        "com.snapchat.android" to SOCIAL,
        "com.discord" to SOCIAL,
        "com.linkedin.android" to WORK,
        "com.microsoft.teams" to WORK,
        "com.slack" to WORK,
        "com.google.android.gm" to WORK,
        "com.microsoft.office.outlook" to WORK,
        "com.google.android.apps.docs" to WORK,
        "com.google.android.apps.docs.editors.docs" to WORK,
        "com.netflix.mediaclient" to ENTERTAINMENT,
        "com.google.android.youtube" to ENTERTAINMENT,
        "com.spotify.music" to ENTERTAINMENT,
        "com.amazon.avod.thirdpartyclient" to ENTERTAINMENT,
        "com.disney.disneyplus" to ENTERTAINMENT,
        "com.hotstar.airtel" to ENTERTAINMENT,
        "in.startv.hotstar" to ENTERTAINMENT
    )

    private val SOCIAL_TOKENS = listOf(
        "instagram", "whatsapp", "telegram", "facebook", "messenger",
        "twitter", "snapchat", "tiktok", "discord", "reddit", "signal"
    )
    private val WORK_TOKENS = listOf(
        "office", "outlook", "teams", "slack", "zoom", "docs", "sheets",
        "notion", "jira", "confluence", "calendar", "mail", "gmail", "linkedin"
    )
    private val ENTERTAINMENT_TOKENS = listOf(
        "youtube", "netflix", "spotify", "primevideo", "disney", "hotstar",
        "twitch", "game", "play.games", "music", "video", "cinema", "sony.liv",
        "com.jio.media", "mxplayer"
    )

    fun normalizeCategory(raw: String?): String {
        val c = raw?.trim().orEmpty()
        if (c.isEmpty()) return OTHER
        return ALL_CATEGORIES.firstOrNull { it.equals(c, ignoreCase = true) } ?: OTHER
    }

    /**
     * Resolve category for [packageName].
     * Order: user override → exact default → heuristic tokens → Other.
     */
    fun categoryFor(
        packageName: String,
        overrides: Map<String, String> = emptyMap()
    ): String {
        val pkg = packageName.trim().lowercase()
        if (pkg.isEmpty()) return OTHER

        overrides[packageName.trim()]?.let { return normalizeCategory(it) }
        overrides[pkg]?.let { return normalizeCategory(it) }

        DEFAULTS[pkg]?.let { return it }
        DEFAULTS.entries.firstOrNull { it.key.equals(pkg, ignoreCase = true) }?.value?.let {
            return it
        }

        return heuristicCategory(pkg)
    }

    fun heuristicCategory(packageNameLower: String): String {
        val pkg = packageNameLower.lowercase()
        if (SOCIAL_TOKENS.any { pkg.contains(it) }) return SOCIAL
        if (WORK_TOKENS.any { pkg.contains(it) }) return WORK
        if (ENTERTAINMENT_TOKENS.any { pkg.contains(it) }) return ENTERTAINMENT
        return OTHER
    }

    /** Encode overrides for DataStore: `pkg=Category|pkg2=Category`. */
    fun encodeOverrides(map: Map<String, String>): String =
        map.entries
            .filter { it.key.isNotBlank() }
            .joinToString("|") { "${it.key.trim()}=${normalizeCategory(it.value)}" }

    fun decodeOverrides(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('|')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val pkg = part.substring(0, idx).trim()
                val cat = normalizeCategory(part.substring(idx + 1))
                if (pkg.isEmpty()) null else pkg to cat
            }
            .toMap()
    }
}
