package com.example.sortmate.notifications

import com.example.sortmate.SortMateApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SortMateMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = applicationContext as? SortMateApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            app.container.householdRepository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = applicationContext as? SortMateApplication ?: return
        val title = message.notification?.title ?: message.data["title"] ?: "SortMate"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val type = message.data["type"] ?: "activity"
        app.container.notificationHelper.showRemoteAlert(type, title, body)
    }
}
