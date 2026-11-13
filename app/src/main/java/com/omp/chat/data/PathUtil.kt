package com.omp.chat.data

/**
 * Working-folder paths come from the connected PC, which may run Windows or
 * Linux, so they are validated by shape instead of by the phone's own OS.
 *
 * Rules:
 *  - A bare drive ("C:") is drive-relative and never a folder.
 *  - A real Windows folder is `X:\...`, `X:/...` or a UNC path (`\\server\share`).
 *  - A Linux folder is absolute (`/...`).
 *  - Windows paths compare case-insensitively; Linux paths stay case-sensitive.
 */
object CwdPath {

    private val driveOnly = Regex("^[A-Za-z]:$")
    private val windowsPath = Regex("^[A-Za-z]:[\\\\/].*")

    fun isWindowsStyle(value: String): Boolean {
        val v = value.trim()
        return windowsPath.matches(v) || v.startsWith("\\\\")
    }

    /** True only for an absolute folder path; `C:` and relative names fail. */
    fun isUsable(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty() || driveOnly.matches(v)) return false
        return isWindowsStyle(v) || v.startsWith("/")
    }

    /**
     * Comparison key. Never call this to *prove* a folder exists; it only
     * answers "do these two strings name the same folder?".
     */
    fun key(value: String): String {
        var v = value.trim().replace('/', '\\').trimEnd('\\')
        if (isWindowsStyle(v)) v = v.lowercase()
        return v
    }

    fun same(a: String, b: String): Boolean {
        if (!isUsable(a) || !isUsable(b)) return false
        return key(a) == key(b)
    }

    /**
     * Last path segment, or the path itself for a root such as `C:\`.
     * Returns "" for anything that is not a usable absolute folder.
     */
    fun displayName(value: String): String {
        val v = value.trim()
        if (!isUsable(v)) return ""
        val trimmed = v.trimEnd('\\', '/')
        val name = trimmed.substringAfterLast('\\').substringAfterLast('/')
        return name.ifBlank { v }
    }

    /** Short label for the sidebar; never returns `C:` or an empty string. */
    fun projectLabel(value: String, fallback: String): String =
        displayName(value).ifBlank { fallback }

    /** True when two folder strings address the same place, ignoring casing on Windows. */
    fun equivalent(a: String, b: String): Boolean = same(a, b)
}
