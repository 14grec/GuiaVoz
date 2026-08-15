package br.com.exemplo.guiavoz.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;

import br.com.exemplo.guiavoz.whatsapp.WhatsAppNotificationService;

import java.util.List;

public final class MediaActionController {
    public enum Action { PLAY, PAUSE, NEXT }

    private final MediaSessionManager manager;
    private final ComponentName notificationListener;

    public MediaActionController(Context context) {
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        notificationListener = new ComponentName(context, WhatsAppNotificationService.class);
    }

    public boolean execute(Action action) {
        if (manager == null) return false;
        try {
            List<MediaController> controllers = manager.getActiveSessions(notificationListener);
            if (controllers.isEmpty()) return false;
            MediaController.TransportControls controls = controllers.get(0).getTransportControls();
            switch (action) {
                case PLAY -> controls.play();
                case PAUSE -> controls.pause();
                case NEXT -> controls.skipToNext();
            }
            return true;
        } catch (SecurityException error) {
            return false;
        }
    }
}
