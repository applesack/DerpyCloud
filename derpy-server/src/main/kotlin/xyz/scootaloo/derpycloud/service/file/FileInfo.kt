package xyz.scootaloo.derpycloud.service.file

/**
 * @author AppleSack
 * @since  2023/02/02
 */
class FileInfo(
    val size: Long,         // 文件长度
    val name: String,       // 文件名, 不以/符号开头
    val path: String,       // 文件路径, 以/开头的相对路径, 且路径格式为unix
    val modTime: Long,      // 文件最后一次的修改时间
    val creationTime: Long, // 文件被创建的时间
    val isDir: Boolean      // 该文件是否是目录
) {
    companion object {
        /**
         * 占位符, 标记一个方法返回了无效的文件
         */
        val NONE = FileInfo(0, "", "", 0, 0, false)
    }
}
