package xyz.scootaloo.server.context

import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object AppConfig {

    var devMode: Boolean = false

    var isEnableCors: Boolean = false

    var path: String = "derpy"

    val realPathString by lazy { Path(path).absolutePathString() }

    fun init() {
    }

}
