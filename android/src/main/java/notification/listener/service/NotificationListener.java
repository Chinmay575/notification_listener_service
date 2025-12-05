package notification.listener.service;

import static notification.listener.service.NotificationUtils.getBitmapFromDrawable;
import static notification.listener.service.models.ActionCache.cachedNotifications;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;

import notification.listener.service.models.Action;


@SuppressLint("OverrideAbstract")
@RequiresApi(api = VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationListener extends NotificationListenerService {
    private static NotificationListener instance;

    public static NotificationListener getInstance() {
        return instance;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @RequiresApi(api = VERSION_CODES.KITKAT)
    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        handleNotification(notification, false);
    }

    @RequiresApi(api = VERSION_CODES.KITKAT)
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        handleNotification(sbn, true);
    }

    @RequiresApi(api = VERSION_CODES.KITKAT)
    private void handleNotification(StatusBarNotification notification, boolean isRemoved) {
        String packageName = notification.getPackageName();
        Bundle extras = notification.getNotification().extras;
        boolean isOngoing = (notification.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0;
        byte[] appIcon = getAppIcon(packageName);
        byte[] largeIcon = null;
        Action action = NotificationUtils.getQuickReplyAction(notification.getNotification(), packageName);

        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            largeIcon = getNotificationLargeIcon(getApplicationContext(), notification.getNotification());
        }

        Intent intent = new Intent(NotificationConstants.INTENT);
        intent.putExtra(NotificationConstants.PACKAGE_NAME, packageName);
        intent.putExtra(NotificationConstants.ID, notification.getId());
        intent.putExtra(NotificationConstants.CAN_REPLY, action != null);
        intent.putExtra(NotificationConstants.IS_ONGOING, isOngoing);

        if (NotificationUtils.getQuickReplyAction(notification.getNotification(), packageName) != null) {
            cachedNotifications.put(notification.getId(), action);
        }

        intent.putExtra(NotificationConstants.NOTIFICATIONS_ICON, appIcon);
        intent.putExtra(NotificationConstants.NOTIFICATIONS_LARGE_ICON, largeIcon);

        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

            intent.putExtra(NotificationConstants.NOTIFICATION_TITLE, title == null ? null : title.toString());
            intent.putExtra(NotificationConstants.NOTIFICATION_CONTENT, text == null ? null : text.toString());
            intent.putExtra(NotificationConstants.IS_REMOVED, isRemoved);
            intent.putExtra(NotificationConstants.HAVE_EXTRA_PICTURE, extras.containsKey(Notification.EXTRA_PICTURE));

            if (extras.containsKey(Notification.EXTRA_PICTURE)) {
                Bitmap bmp = (Bitmap) extras.get(Notification.EXTRA_PICTURE);
                if (bmp != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    intent.putExtra(NotificationConstants.EXTRAS_PICTURE, stream.toByteArray());
                } else {
                    Log.w("NotificationListener", "Notification.EXTRA_PICTURE exists but is null.");
                }
            }
        }
        sendBroadcast(intent);
    }


    public byte[] getAppIcon(String packageName) {
        try {
            PackageManager manager = getBaseContext().getPackageManager();
            Drawable icon = manager.getApplicationIcon(packageName);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            getBitmapFromDrawable(icon).compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @RequiresApi(api = VERSION_CODES.M)
    private byte[] getNotificationLargeIcon(Context context, Notification notification) {
        try {
            Icon largeIcon = notification.getLargeIcon();
            if (largeIcon == null) {
                return null;
            }
            Drawable iconDrawable = largeIcon.loadDrawable(context);
            Bitmap iconBitmap = ((BitmapDrawable) iconDrawable).getBitmap();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("ERROR LARGE ICON", "getNotificationLargeIcon: " + e.getMessage());
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public List<Map<String, Object>> getActiveNotificationData() {
        List<Map<String, Object>> notificationList = new ArrayList<>();
        StatusBarNotification[] activeNotifications = getActiveNotifications();

        for (StatusBarNotification sbn : activeNotifications) {
            Map<String, Object> notifData = new HashMap<>();
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            String packageName = sbn.getPackageName();

            // Basic fields
            notifData.put("id", sbn.getId());
            notifData.put("packageName", packageName);
            notifData.put("title", extras.getCharSequence(Notification.EXTRA_TITLE) != null
                    ? extras.getCharSequence(Notification.EXTRA_TITLE).toString()
                    : null);
            notifData.put("content", extras.getCharSequence(Notification.EXTRA_TEXT) != null
                    ? extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                    : null);
            
            // Check if notification is ongoing
            boolean isOngoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
            notifData.put("onGoing", isOngoing);
            
            // Check if notification can be replied to
            Action action = NotificationUtils.getQuickReplyAction(notification, packageName);
            notifData.put("canReply", action != null);
            
            // Cache the action for reply functionality
            if (action != null) {
                cachedNotifications.put(sbn.getId(), action);
            }
            
            // Always set hasRemoved to false for active notifications
            notifData.put("hasRemoved", false);
            
            // Check if notification has extra picture
            boolean hasExtraPicture = extras.containsKey(Notification.EXTRA_PICTURE);
            notifData.put("haveExtraPicture", hasExtraPicture);
            
            // Get app icon
            byte[] appIcon = getAppIcon(packageName);
            notifData.put("appIcon", appIcon);
            
            // Get large icon (API 23+)
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                byte[] largeIcon = getNotificationLargeIcon(getApplicationContext(), notification);
                notifData.put("largeIcon", largeIcon);
            } else {
                notifData.put("largeIcon", null);
            }
            
            // Get extras picture if available
            if (hasExtraPicture) {
                Bitmap bmp = (Bitmap) extras.get(Notification.EXTRA_PICTURE);
                if (bmp != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    notifData.put("notificationExtrasPicture", stream.toByteArray());
                } else {
                    notifData.put("notificationExtrasPicture", null);
                }
            } else {
                notifData.put("notificationExtrasPicture", null);
            }

            notificationList.add(notifData);
        }
        return notificationList;
    }

}
