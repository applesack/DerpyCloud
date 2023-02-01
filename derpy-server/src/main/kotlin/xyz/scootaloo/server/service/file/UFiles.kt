package xyz.scootaloo.server.service.file

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

}
