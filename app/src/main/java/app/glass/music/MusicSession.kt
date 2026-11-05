package app.glass.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.os.SystemClock

/** Uses the user's explicitly enabled notification listener; no private Yandex APIs. */
class MediaWatcher : NotificationListenerService()

class MusicSession(private val context: Context) {
    private var controller: MediaController? = null
    private var lastCheck = 0L
    var title: String = "Яндекс Музыка"
        private set
    var artist: String = "Включите любой трек"
        private set
    var artwork: Bitmap? = null
        private set
    var playing = false
        private set
    val connected: Boolean get() = controller != null

    fun refresh() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheck < 1200L) return
        lastCheck = now
        try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = mgr.getActiveSessions(ComponentName(context, MediaWatcher::class.java))
            controller = list.firstOrNull { it.packageName == "ru.yandex.music" }
            val c = controller
            if (c == null) {
                title = "Яндекс Музыка"; artist = "Разрешите уведомления и запустите трек"
                artwork = null; playing = false
            } else {
                val m = c.metadata
                title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Яндекс Музыка"
                artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "Моя волна"
                artwork = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } catch (_: SecurityException) {
            controller = null
        } catch (_: RuntimeException) {
            controller = null
        }
    }
    fun previous() { refresh(); controller?.transportControls?.skipToPrevious() }
    fun next() { refresh(); controller?.transportControls?.skipToNext() }
    fun toggle() {
        refresh()
        if (playing) controller?.transportControls?.pause() else controller?.transportControls?.play()
        lastCheck = 0L
    }
}
