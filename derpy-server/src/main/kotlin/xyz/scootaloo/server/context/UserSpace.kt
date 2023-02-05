package xyz.scootaloo.server.context

import xyz.scootaloo.server.service.webdav.LockSystem

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class UserSpace(
    val storageSpace: StorageSpace,
    val lock: LockSystem
) {
}
