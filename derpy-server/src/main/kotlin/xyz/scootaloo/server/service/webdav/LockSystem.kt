package xyz.scootaloo.server.service.webdav

/**
 * @author AppleSack
 * @since  2023/02/01
 */
interface LockSystem {

    /**
     * **确认调用者是否可以用给定[conditions]条件声明[name0]指定的所有的锁，并且持有这些锁**;
     * 将对所有指定的资源的标记为独占访问. 最多可以指定两个资源. 如果指定的资源名称为空将被忽略;
     *
     * - 返回一个元组, 第一个元素是一个回调, 第二个元素是操作状态; 如果操作状态为[Errors.None],
     * 那么调用第一个元素将释放之前持有的锁; 如果操作状态为其他状态, 那么返回的回调无效, 并不会持有锁;
     * 在WebDAV的意义上, 调用释放不会解锁资源, 但是一旦确认声明操作成功, 在该锁被释放之前无法再次确认;
     *
     * - 如果[confirm]调用返回[Errors.ConfirmationFailed], 则处理程序将继续尝试使用其他的锁;
     * 如果抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * @param conditions 条件, http请求中的if-header
     * @param name0      资源名1
     * @param name1      资源名2
     */
    fun confirm(conditions: List<Condition>, name0: String, name1: String = ""): Pair<() -> Unit, Errors>

    /**
     * **使用提供的信息(深度, 持续时间, 所有者, 资源名)去创建一个锁**,
     * 其中锁的深度只有两个取值, 0 或者 infinite;
     *
     * - 如果该调用返回了[Errors.Locked], 响应应该写入"423 Locked"状态码,
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * - 返回的token可以标识这个调用创建的锁, 这个锁的路径应该是一个绝对路径(路径格式在**RFC3986**中定义), 且不能包含空格
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.10.6
     *
     * @param details 创建锁所需要的信息
     */
    fun create(details: LockDetails): Pair<String, Errors>

    /**
     * **使用给定的token刷新锁**
     *
     * - 如果[refresh]调用返回了[Errors.Locked], 响应应该写入"423 Locked"状态码;
     * 如果调用返回了[Errors.NoSuchLock], 响应应该写入"412 Precondition Failed"状态码;
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.10.6
     */
    fun refresh(token: String, ttl: Long): Pair<LockDetails, Errors>

    /**
     * **使用指定的token解锁**
     *
     * - 如果[unlock]调用返回[Errors.Forbidden], 响应应该写入"403 Forbidden"状态码;
     * 如果调用返回[Errors.Locked], 响应应该写入"423 Locked"状态码;
     * 如果调用返回[Errors.ConfirmationFailed], 响应应该写入"409 Conflict"状态码;
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.11.1
     */
    fun unlock(token: String): Errors

}
