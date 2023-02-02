package xyz.scootaloo.server.service.file

import io.vertx.core.file.FileSystem
import io.vertx.kotlin.coroutines.await
import xyz.scootaloo.server.context.Contexts
import xyz.scootaloo.server.context.StorageSpace
import xyz.scootaloo.server.service.lock.Errors

/**
 * @author AppleSack
 * @since 2023/02/01
 */
object UFiles {

    fun slashClean(name: String): String {
        var out = name
        if (out == "" || out[0] == '/') {
            out = "/$out"
        }
        return UPaths.clean(out)
    }

    /**
     * 检查一个路径是否存在, 如果存在, 返回文件描述, 否则返回空
     */
    suspend fun isPathExists(storage: StorageSpace, path: String): Pair<Boolean, FileInfo> {
        val fs = Contexts.vertx.fileSystem()
        val realPath = UPaths.realPath(storage.prefix, path)
        if (!fs.exists(realPath).await()) {
            return false to FileInfo.NONE
        }
        return true to getFileInfoFromFileSystem(fs, realPath)
    }

    suspend fun walkFS(
        storage: StorageSpace, fs: FileSystem,
        info: FileInfo, depth: Int, path: String,
        walkFun: (String, FileInfo, Errors) -> Unit): Errors {
        TODO()
    }

    private suspend fun getFileInfoFromFileSystem(fs: FileSystem, realPath: String): FileInfo {
        val prop = fs.props(realPath).await()
        return FileInfo(prop.size(), "", "", prop.lastAccessTime(), prop.isDirectory)
    }

}
