/*
 * Copyright 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.MprisReceiverPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationReceiver;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.util.HashMap;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MprisReceiverPlugin extends Plugin implements MediaSessionManager.OnActiveSessionsChangedListener {

    private final static String PACKET_TYPE_MPRIS = "kdeconnect.mpris";
    private final static String PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request";

    private static final String TAG = "MprisReceiver";

    private HashMap<String, MprisReceiverPlayer> players;

    @Override
    public boolean onCreate() {

        if (!hasPermission())
            return false;

        players = new HashMap<>();
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (null == manager)
                return false;

            manager.addOnActiveSessionsChangedListener(MprisReceiverPlugin.this, new ComponentName(context, NotificationReceiver.class), new Handler(Looper.getMainLooper()));

            createPlayers(manager.getActiveSessions(new ComponentName(context, NotificationReceiver.class)));
            sendPlayerList();
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }

        return true;
    }

    private void createPlayers(List<MediaController> sessions) {
        for (MediaController controller : sessions) {
            createPlayer(controller);
        }
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (np.getBoolean("requestPlayerList")) {
            sendPlayerList();
            return true;
        }

        if (!np.has("player")) {
            return false;
        }
        MprisReceiverPlayer player = players.get(np.getString("player"));

        if (null == player) {
            return false;
        }

        if (np.getBoolean("requestNowPlaying", false)) {
            sendMetadata(player);
            return true;
        }

        if (np.has("action")) {
            String action = np.getString("action");

            switch (action) {
                case "PlayPause":
                    player.playPause();
                    break;
                case "Next":
                    player.next();
                    break;
                case "Previous":
                    player.previous();
            }
        }

        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS};
    }

    @Override
    public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {

        if (null == controllers) {
            return;
        }

        players.clear();

        createPlayers(controllers);
        sendPlayerList();

    }

    private void createPlayer(MediaController controller) {
        MprisReceiverPlayer player = new MprisReceiverPlayer(controller, AppsHelper.appNameLookup(context, controller.getPackageName()));
        controller.registerCallback(new MprisReceiverCallback(this, player), new Handler(Looper.getMainLooper()));
        players.put(player.getName(), player);
    }

    private void sendPlayerList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS);
        np.set("playerList", players.keySet());
        device.sendPacket(np);
    }

    void sendPlaying(MprisReceiverPlayer player) {

        NetworkPacket np = new NetworkPacket(MprisReceiverPlugin.PACKET_TYPE_MPRIS);
        np.set("player", player.getName());
        np.set("isPlaying", player.isPlaying());
        device.sendPacket(np);
    }

    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    public void sendMetadata(MprisReceiverPlayer player) {
        NetworkPacket np = new NetworkPacket(MprisReceiverPlugin.PACKET_TYPE_MPRIS);
        np.set("player", player.getName());
        if (player.getArtist().isEmpty()) {
            np.set("nowPlaying", player.getTitle());
        } else {
            np.set("nowPlaying", player.getArtist() + " - " + player.getTitle());
        }
        np.set("title", player.getTitle());
        np.set("artist", player.getArtist());
        np.set("album", player.getAlbum());
        np.set("isPlaying", player.isPlaying());
        np.set("pos", player.getPosition());
        device.sendPacket(np);
    }

    public void sendVolume(MprisReceiverPlayer player) {
        NetworkPacket np = new NetworkPacket(MprisReceiverPlugin.PACKET_TYPE_MPRIS);
        np.set("player", player.getName());
        np.set("volume", player.getVolume());
        device.sendPacket(np);
    }

    @Override
    public AlertDialog getErrorDialog(final Activity deviceActivity) {

        return new AlertDialog.Builder(deviceActivity)
                .setTitle(R.string.pref_plugin_mpris)
                .setMessage(R.string.no_permission_mprisreceiver)
                .setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        deviceActivity.startActivityForResult(intent, MainActivity.RESULT_NEEDS_RELOAD);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Do nothing
                    }
                })
                .create();
    }

    private boolean hasPermission() {
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return (notificationListenerList != null && notificationListenerList.contains(context.getPackageName()));
    }

}
