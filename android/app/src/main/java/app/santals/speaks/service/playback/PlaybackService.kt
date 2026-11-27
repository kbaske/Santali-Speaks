package app.santals.speaks.service.playback

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.santals.speaks.presentation.MainActivity
import app.santals.speaks.R
import androidx.concurrent.futures.ResolvableFuture
import com.google.common.util.concurrent.ListenableFuture

/**
 * Background playback service. Media3 uses it to:
 *  - keep audio playing when the app is in the background (foreground service, media type);
 *  - show the media notification with play/pause and the lock-screen controls;
 *  - manage audio focus (calls, other apps) and pause when headphones are unplugged.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()

        val openAppIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openAppPending = PendingIntent.getActivity(this, 0, openAppIntent, FLAG_IMMUTABLE)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPending)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(STOP_COMMAND)
                .build()
            val stopButton = CommandButton.Builder()
                .setDisplayName(getString(R.string.player_stop))
                .setIconResId(R.drawable.ic_stop)
                .setSessionCommand(STOP_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(listOf(stopButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_STOP) {
                session.player.stop()
                session.player.clearMediaItems()
                PlaybackEvents.requestStop()
                val future = ResolvableFuture.create<SessionResult>()
                future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                return future
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        private const val SEEK_STEP_MS = 10_000L
        const val ACTION_STOP = "app.santals.speaks.STOP"
        val STOP_COMMAND = SessionCommand(ACTION_STOP, Bundle.EMPTY)
    }
}
