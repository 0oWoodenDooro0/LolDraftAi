package com.loldraft.data.normalization

data class PatchVersion(
    val major: Int,
    val minor: Int,
    val subPatch: Int = 0,
    val hotfix: String? = null,
) : Comparable<PatchVersion> {
    val canonicalString: String
        get() {
            val base = "$major.$minor"
            val sub = if (subPatch > 0) ".$subPatch" else ""
            val hf = hotfix ?: ""
            return "$base$sub$hf"
        }

    override fun compareTo(other: PatchVersion): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.subPatch != other.subPatch) return this.subPatch.compareTo(other.subPatch)
        return (this.hotfix ?: "").compareTo(other.hotfix ?: "")
    }

    override fun toString(): String = canonicalString
}

object PatchNormalizer {
    private val PATCH_REGEX = Regex("""(?i)^\s*(?:patch\s*|v)?(\d+)\.(\d+)(?:\.(\d+))?([a-zA-Z])?\s*$""")

    fun parse(raw: String?): PatchVersion? {
        if (raw.isNullOrBlank()) return null
        val match = PATCH_REGEX.find(raw.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val subPatch =
            match.groupValues
                .getOrNull(3)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull() ?: 0
        val hotfix =
            match.groupValues
                .getOrNull(4)
                ?.takeIf { it.isNotBlank() }
                ?.lowercase()
        return PatchVersion(major, minor, subPatch, hotfix)
    }

    fun normalize(
        raw: String?,
        default: String = "unknown",
    ): String = parse(raw)?.canonicalString ?: default

    fun isValid(raw: String?): Boolean = parse(raw) != null
}
