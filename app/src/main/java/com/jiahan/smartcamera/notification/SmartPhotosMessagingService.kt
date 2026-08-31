package com.jiahan.smartcamera.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jiahan.smartcamera.MainActivity
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.di.IoDispatcher
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DATA_KEY_NOTE_ID = "noteId"
private const val DATA_KEY_MEDIA_URL = "mediaUrl"

@AndroidEntryPoint
class SmartPhotosMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var errorHandler: ErrorHandler

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    // Hilt field injection runs in onCreate(), after this class's constructor -- so
    // ioDispatcher isn't available yet at property-initialization time. `by lazy` defers
    // construction to first access (onNewToken()/onMessageReceived()), which always happens
    // after onCreate().
    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

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
        val mediaUrl = message.data[DATA_KEY_MEDIA_URL]
        val title = message.notification?.title
            ?: getString(R.string.notification_note_processed_title)
        val body = message.notification?.body
            ?: getString(R.string.notification_note_processed_body)
        // Fetching/cropping the media preview needs to happen off the main thread, and
        // onMessageReceived() offers no callback for "done displaying" -- so the notification is
        // built and shown from within this coroutine rather than being returned out of it.
        serviceScope.launch {
            val icon = mediaUrl?.let { loadRoundedIcon(it) }
            showNotification(title = title, body = body, noteId = noteId, largeIcon = icon)
        }
    }

    private suspend fun loadRoundedIcon(url: String): Bitmap? {
        // toCircularBitmap() below draws the result onto a software Canvas, and a
        // Bitmap.Config.HARDWARE bitmap can't be drawn that way -- it throws "Software
        // rendering doesn't support hardware bitmaps". Coil defaults to allowHardware = true,
        // so this request must opt out explicitly.
        val request = ImageRequest.Builder(this).data(url).allowHardware(false).build()
        val result = SingletonImageLoader.get(this).execute(request)
        if (result !is SuccessResult) {
            return null
        }
        val bitmap = result.image.asDrawable(resources).toBitmap()
        return bitmap.toCircularBitmap()
    }

    private fun Bitmap.toCircularBitmap(): Bitmap {
        val output = createBitmap(width, height)
        val canvas = Canvas(output)
        val rect = Rect(0, 0, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawOval(RectF(rect), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, rect, rect, paint)
        return output
    }

    private fun showNotification(
        title: String,
        body: String?,
        noteId: String?,
        largeIcon: Bitmap?
    ) {
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
                .setSmallIcon(UiR.drawable.photo_camera)
                .setLargeIcon(largeIcon)
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