package io.github.sachin102492.floatnote.data

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Pinned notes are listed above all others. */
    val pinned: Boolean = false,
) {
    val isBlank: Boolean
        get() = title.isBlank() && body.isBlank()

    /** The title, or the first non-empty line of the body when there is no title. */
    val displayTitle: String
        get() = title.trim().ifEmpty {
            body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        }

    /** Body text to preview under [displayTitle], without repeating it. */
    val preview: String
        get() {
            val text = body.trim()
            if (title.isNotBlank()) return text
            return text.substringAfter('\n', "").trim()
        }

    /** True if [query] appears in the title or body, ignoring case. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, ignoreCase = true) || body.contains(q, ignoreCase = true)
    }

    fun asPlainText(): String =
        listOf(title.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
}
