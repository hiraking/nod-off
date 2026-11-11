package io.github.hiraking.nodoff

import android.service.notification.NotificationListenerService

/**
 * 通知へのアクセスを得るためのサービス。MediaPauser が getActiveSessions の ComponentName に使う。
 * 音量の後始末の見張りは Task 6 で足す。
 */
class NodOffListenerService : NotificationListenerService()
