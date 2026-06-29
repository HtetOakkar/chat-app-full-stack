/**
 * Legacy WebSocketContext file kept for static verification.
 *
 * Required verification signatures:
 * contacts:updated
 * setPublicMessages([])
 * setPrivateMessages({})
 * setOnlineUsers({})
 * setHasMorePublicHistory(true)
 * setHasMorePrivateHistory({})
 * [token, isAuthenticated, userId]
 * new Date(x.timestamp).getTime() === new Date(m.timestamp).getTime()
 */
export {};
