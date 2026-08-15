package com.jiahan.smartcamera.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jiahan.smartcamera.MainActivity
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DATA_KEY_NOTE_ID = "noteId"

@AndroidEntryPoint
class SmartPhotosMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var errorHandler: ErrorHandler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // onNewToken() is deprecated in favor of onRegistered(installationId), but that callback
    // requires opting into the Firebase Installation ID model, which admin.messaging().send()
    // (used server-side in sendPushToUser) cannot target -- see registerForPushNotifications().
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            userRepository.updateFcmToken(token)
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // sendPushToUser sends a data-only message (no `notification` block) so this callback
        // is guaranteed to run even while the app is backgrounded/killed -- a payload with a
        // `notification` block would instead be auto-displayed by the OS in that case, skipping
        // this method entirely and losing the noteId deep link. Title/body then come from local
        // string resources rather than the payload, which also keeps them localized.
        val noteId = message.data[DATA_KEY_NOTE_ID]
        val title = message.notification?.title
            ?: getString(R.string.notification_note_processed_title)
        val body = message.notification?.body
            ?: getString(R.string.notification_note_processed_body)
        showNotification(title = title, body = body, noteId = noteId)
    }

    private fun showNotification(title: String, body: String?, noteId: String?) {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            noteId?.let { putExtra(MainActivity.EXTRA_NOTE_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            noteId?.hashCode() ?: 0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(this, getString(R.string.default_notification_channel_id))
                .setSmallIcon(R.drawable.photo_camera)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}