package com.swordfish.lemuroid.lib.storage.smb

/**
 * Network share (SMB) with the games library. [path] is the full path from the share name, e.g.
 * "/almacen/juegos/romsRetro" (share "almacen", folder "juegos/romsRetro").
 */
data class SmbConfig(
    val displayName: String,
    val server: String,
    val path: String,
    val useAuthentication: Boolean,
    val user: String,
    val password: String,
) {
    /** Name of the share: the first part of the path. */
    val shareName: String
        get() = pathParts().firstOrNull().orEmpty()

    /** Folder inside the share, with "\" separators as SMB expects ("" for the root of the share). */
    val folder: String
        get() = pathParts().drop(1).joinToString(SMB_SEPARATOR)

    /** Name shown in the settings: the display name, or the server and path. */
    val label: String
        get() = displayName.ifBlank { "$server$path" }

    fun isValid(): Boolean = server.isNotBlank() && shareName.isNotBlank()

    // Without the password, in case it ever ends up in a log.
    override fun toString(): String =
        "SmbConfig(displayName=$displayName, server=$server, path=$path, useAuthentication=$useAuthentication, " +
            "user=$user, password=***)"

    private fun pathParts(): List<String> = path.split('/', '\\').filter { it.isNotBlank() }

    companion object {
        const val SMB_SEPARATOR = "\\"
    }
}
