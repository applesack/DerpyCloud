package xyz.scootaloo.derpycloud.service.file

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.CompositeFuture
import io.vertx.core.file.*
import io.vertx.kotlin.core.file.copyOptionsOf
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import xyz.scootaloo.derpycloud.context.Contexts
import xyz.scootaloo.derpycloud.context.StorageSpace
import xyz.scootaloo.derpycloud.service.utils.Defer
import xyz.scootaloo.derpycloud.service.webdav.Errors
import java.nio.file.NoSuchFileException
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

    fun isFileNotExists(call: Result<Any>): Boolean {
        val ex = call.exceptionOrNull() ?: return false
        return ex is FileSystemException && ex.cause is NoSuchFileException
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

    /**
     * 可能会抛出异常, 如果抛出[NoSuchFileException], 返回404, 其他异常返回403.
     * 如果没有抛出异常, 可以直接将方法返回的状态码作为响应的状态码.
     */
    suspend fun copyFiles(
        storage: StorageSpace, src: String, dst: String,
        overwrite: Boolean, depth: Int, recursion: Int
    ): HttpResponseStatus = Defer.run {
        if (recursion == 1000) { // 默认递归深度1000以内
            return@run HttpResponseStatus.INTERNAL_SERVER_ERROR
        }
        val recursionCp = recursion + 1
        val fs = Contexts.vertx.fileSystem()

        val srcRealPath = UPaths.realPath(storage, src)
        val srcFile = fs.open(srcRealPath, openOptionsOf(read = true)).await()
        defer { srcFile.close().await() }

        var created = false
        val dstRealPath = UPaths.realPath(storage, dst)
        if (!fs.exists(dstRealPath).await()) {
            created = true
        } else {
            // 文件存在
            if (!overwrite) {
                return@run HttpResponseStatus.PRECONDITION_FAILED
            }
            fs.deleteRecursive(dstRealPath, true).await()
        }

        val srcFileProps = fs.props(srcRealPath).await()
        if (srcFileProps.isDirectory) {
            // 如果是文件夹
            fs.mkdir(dstRealPath).await()
            // 如果深度无限, 则继续向下递归
            if (depth == -1) {
                val children = fs.readDir(srcRealPath).await()
                for (child in children) {
                    val filename = UPaths.filenameOf(child)
                    val childSrc = UPaths.join(src, filename)
                    val childDst = UPaths.join(dst, filename)
                    return@run copyFiles(storage, childSrc, childDst, overwrite, depth, recursionCp)
                }
            }
        } else {
            // 如果是文件
            val openOptions = openOptionsOf(write = true, create = true, truncateExisting = true)
            val safeOpen = runCatching { fs.open(dstRealPath, openOptions).await() }
            if (safeOpen.isFailure) {
                return@run HttpResponseStatus.PRECONDITION_FAILED
            }
            val dstFile = safeOpen.getOrThrow()
            defer { dstFile.close().await() }
            val copyOptions = copyOptionsOf(
//                copyAttributes = true, // 非 posix 环境不支持这个api
                replaceExisting = overwrite
            )
            fs.copy(srcRealPath, dstRealPath, copyOptions).await()
        }

        if (created) {
            return@run HttpResponseStatus.CREATED
        }
        HttpResponseStatus.NO_CONTENT
    }

    /**
     * 处理方式与[copyFiles]相同
     */
    suspend fun moveFiles(
        storage: StorageSpace, src: String, dst: String, overwrite: Boolean
    ): HttpResponseStatus = Defer.run {
        var created = false
        val dstRealPath = UPaths.realPath(storage, dst)
        val fs = Contexts.vertx.fileSystem()
        if (!fs.exists(dstRealPath).await()) {
            created = true
        } else if (overwrite) {
            fs.deleteRecursive(dstRealPath, true).await()
        } else {
            // 以不重写的方式将文件移动到一个已存在的位置
            return@run HttpResponseStatus.PRECONDITION_FAILED
        }

        val srcRealPath = UPaths.realPath(storage, src)
        val copyOptions = copyOptionsOf(
//            copyAttributes = true, // 非 posix 环境不支持这个api
            replaceExisting = true
        )
        fs.move(srcRealPath, dstRealPath, copyOptions).await()

        if (created) {
            return@run HttpResponseStatus.CREATED
        }
        HttpResponseStatus.NO_CONTENT
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
        val readDirCall = runCatching { fs.readDir(realPath).await() }
        if (readDirCall.isFailure) {
            return emptyList()
        }

        return getMultiFileInfoFromFileSystem(storage, fs, readDirCall.getOrThrow())
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
        val path = UPaths.relative(storage, Paths.get(realPath))
        val filename = UPaths.filenameOf(path)
        return FileInfo(
            props.size(), filename, path, props.lastModifiedTime(),
            props.creationTime(), props.isDirectory
        )
    }

}
