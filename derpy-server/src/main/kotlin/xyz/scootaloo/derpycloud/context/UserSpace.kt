package xyz.scootaloo.derpycloud.context

import xyz.scootaloo.derpycloud.service.webdav.LockSystem

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class UserSpace(
    val storageSpace: StorageSpace,
    val lock: LockSystem
) {
}
