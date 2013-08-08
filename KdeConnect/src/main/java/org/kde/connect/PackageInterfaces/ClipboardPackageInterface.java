package org.kde.connect.PackageInterfaces;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public class ClipboardPackageInterface extends BasePackageInterface {

    private boolean ignore_next_clipboard_change = false;

    private Context context;
    private ClipboardManager cm;
    private ClipboardManager.OnPrimaryClipChangedListener listener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            try {
                if (ignore_next_clipboard_change) {
                    ignore_next_clipboard_change = false;
                    return;
                }
                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_CLIPBOARD);
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                np.set("content",item.coerceToText(context).toString());
                sendPackage(np);
            } catch(Exception e) {
                //Probably clipboard was not text
            }
        }
    };

    @Override
    public boolean onCreate(Context context) {

        this.context = context;

        cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(listener);

        return false;

    }

    @Override
    public void onDestroy() {
        cm.removePrimaryClipChangedListener(listener);
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_CLIPBOARD)) {
            ignore_next_clipboard_change = true;
            cm.setText(np.getString("content"));
            return true;
        }
        return false;
    }

    public boolean onDeviceConnected(Device d) {
        return false;
    }

}
