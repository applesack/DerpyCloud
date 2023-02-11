package xyz.scootaloo.derpycloud.middleware

import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler

/**
 * @author AppleSack
 * @since 2023/02/11
 */
object UploadResourceHandler : Handler<RoutingContext> {

    private val bodyHandler = BodyHandler.create()

    override fun handle(event: RoutingContext) {
        // 考虑到大文件上传, 由于不能一次性将内容读取到内存中(会导致OOM), 所以使用了pipeTo的api,
        // 但是这个api不兼容小文件, 由于通常情况是在处理请求体前会有一个或多个异步操作,
        // 在实际处理请求体时, 请求体不可读 (Request has already been read)

        // 为了解决这个问题, 现在规定上传文件 (无论大小), 使用 PUT 作为请求方式
        // 且 PUT 方法的处理器在读取请求体时要调用 request.resume()
        if (event.request().method() == HttpMethod.PUT) {
            event.request().pause()
            event.next()
        } else {
            // 对于其他方法, 使用body-handler进行处理, 使得其他路径可以读到请求体
            bodyHandler.handle(event)
        }
    }

}
