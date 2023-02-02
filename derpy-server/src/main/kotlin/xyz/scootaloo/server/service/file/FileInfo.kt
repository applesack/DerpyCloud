package xyz.scootaloo.server.service.file

/**
 * @author AppleSack
 * @since  2023/02/02
 */
class FileInfo(
    val size: Long,
    val name: String,
    val path: String,
    val modTime: Long,
    val isDir: Boolean
)
