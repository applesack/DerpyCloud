package xyz.scootaloo.server.service.file

import io.vertx.core.CompositeFuture
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.kotlin.core.file.fileSystemOptionsOf
import io.vertx.kotlin.coroutines.await
import xyz.scootaloo.server.context.Contexts
import xyz.scootaloo.server.context.StorageSpace
import xyz.scootaloo.server.service.lock.Errors
import java.nio.file.Path
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
            val filename = UPaths.join(name, childFile.name)
            error = walkFS(storage, fs, childFile, dep, filename, walkFun)
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
