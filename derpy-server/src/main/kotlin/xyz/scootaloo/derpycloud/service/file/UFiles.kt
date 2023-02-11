package xyz.scootaloo.derpycloud.service.file

import io.vertx.core.CompositeFuture
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import xyz.scootaloo.derpycloud.context.Contexts
import xyz.scootaloo.derpycloud.context.StorageSpace
import xyz.scootaloo.derpycloud.service.webdav.Errors
import java.nio.file.Paths

/**
 * @author AppleSack
 * @since 2023/02/01
 */
object UFiles {

    /**
     * 检查一个路径是否存在, 如果存在, 返回文件描述, 否则返回空
     */
    suspend fun isPathExists(storage: StorageSpace, path: String): Pair<Boolean, FileInfo> {
        val fs = Contexts.vertx.fileSystem()
        val realPath = UPaths.realPath(storage, path)
        if (!fs.exists(realPath).await()) {
            return false to FileInfo.NONE
        }
        return true to getFileInfoFromFileSystem(storage, fs, realPath)
    }

    suspend fun isParentPathExists(storage: StorageSpace, path: String): Boolean {
        val sepIdx = path.lastIndexOf('/')
        val parent = if (sepIdx < 0) {
            "/"
        } else {
            path.substring(0, sepIdx)
        }
        return isPathExists(storage, UPaths.clean(parent)).first
    }

    fun findETag(fi: FileInfo): String {
        return String.format("\"%x%x\"", fi.modTime, fi.size)
    }

    suspend fun open(storage: StorageSpace, path: String, options: OpenOptions): AsyncFile {
        val fs = Contexts.vertx.fileSystem()
        val realPath = UPaths.realPath(storage, path)
        return fs.open(realPath, options).await()
    }

    suspend fun makeDir(storage: StorageSpace, path: String) {
        val fs = Contexts.vertx.fileSystem()
        val realPath = UPaths.realPath(storage, path)
        fs.mkdir(realPath).await()
    }

    suspend fun walkFS(
        storage: StorageSpace, fs: FileSystem,
        info: FileInfo, depth: Int, name: String,
        walkFun: (String, FileInfo) -> Errors
    ): Errors {
        var error = walkFun(name, info)
        if (error != Errors.None) {
            if (info.isDir && error == Errors.SkipDir) {
                return Errors.None
            }
            return error
        }
        if (!info.isDir || depth == 0) {
            return Errors.None
        }
        var dep = depth
        if (depth == 1) {
            dep = 0
        }

        val childFiles = getChildFiles(storage, fs, info)
        for (childFile in childFiles) {
            val filepath = UPaths.join(name, childFile.name)
            error = walkFS(storage, fs, childFile, dep, filepath, walkFun)
            if (error != Errors.None) {
                if (!childFile.isDir || error != Errors.SkipDir) {
                    return error
                }
            }
        }

        return Errors.None
    }

    private suspend fun getChildFiles(
        storage: StorageSpace, fs: FileSystem, fi: FileInfo
    ): List<FileInfo> {
        if (!fi.isDir) {
            return emptyList()
        }

        val realPath = UPaths.realPath(storage, fi.path)
        val fResult = runCatching { fs.readDir(realPath).await() }
        if (fResult.isFailure) {
            return emptyList()
        }

        return getMultiFileInfoFromFileSystem(storage, fs, fResult.getOrThrow())
    }

    private suspend fun getMultiFileInfoFromFileSystem(
        storage: StorageSpace, fs: FileSystem, realPaths: List<String>
    ): List<FileInfo> {
        val futures = realPaths.map { it to fs.props(it) }
        CompositeFuture.all(futures.map { it.second }).await()
        return futures.map { it.first to it.second.await() }
            .map { buildFileInfo(storage, it.first, it.second) }
    }

    private suspend fun getFileInfoFromFileSystem(
        storage: StorageSpace, fs: FileSystem, realPath: String
    ): FileInfo {
        val prop = fs.props(realPath).await()
        return buildFileInfo(storage, realPath, prop)
    }

    private fun buildFileInfo(storage: StorageSpace, realPath: String, props: FileProps): FileInfo {
        val path = UPaths.normalize(UPaths.relative(storage.realPrefixPath, Paths.get(realPath)))
        val filename = UPaths.filenameOf(path)
        return FileInfo(
            props.size(), filename, path, props.lastModifiedTime(),
            props.creationTime(), props.isDirectory
        )
    }

}
