package org.telegram.ui.Components;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;

import java.io.File;
import java.util.ArrayList;

/** Local device files are kept separately from synced message drafts. */
public final class ChatMediaDraftStore {

    private ChatMediaDraftStore() {}

    private static String key(long dialogId, long topicId) {
        return "chat_media_draft_" + dialogId + "_" + topicId;
    }

    public static void save(SharedPreferences preferences, long dialogId, long topicId,
                            ArrayList<MediaController.PhotoEntry> photos) {
        String key = key(dialogId, topicId);
        if (photos.isEmpty()) {
            preferences.edit().remove(key).apply();
            return;
        }
        JSONArray items = new JSONArray();
        try {
            for (MediaController.PhotoEntry entry : photos) {
                if (entry == null || entry.path == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("bucket", entry.bucketId);
                item.put("id", entry.imageId);
                item.put("date", entry.dateTaken);
                item.put("path", entry.path);
                item.put("orientation", entry.orientation);
                item.put("duration", entry.duration);
                item.put("video", entry.isVideo);
                item.put("width", entry.width);
                item.put("height", entry.height);
                item.put("size", entry.size);
                item.put("invert", entry.invert);
                item.put("imagePath", entry.imagePath);
                item.put("thumbPath", entry.thumbPath);
                items.put(item);
            }
            preferences.edit().putString(key, items.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static ArrayList<MediaController.PhotoEntry> load(SharedPreferences preferences, long dialogId, long topicId) {
        ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
        String stored = preferences.getString(key(dialogId, topicId), null);
        if (stored == null) {
            return photos;
        }
        try {
            JSONArray items = new JSONArray(stored);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String path = item.optString("path", null);
                if (path == null || !new File(path).isFile()) {
                    continue;
                }
                MediaController.PhotoEntry entry = new MediaController.PhotoEntry(
                        item.optInt("bucket"), item.optInt("id"), item.optLong("date"), path,
                        item.optInt("orientation"), item.optInt("duration"), item.optBoolean("video"),
                        item.optInt("width"), item.optInt("height"), item.optLong("size"));
                entry.invert = item.optInt("invert");
                String imagePath = item.optString("imagePath", null);
                entry.imagePath = imagePath != null && new File(imagePath).isFile() ? imagePath : null;
                String thumbPath = item.optString("thumbPath", null);
                entry.thumbPath = thumbPath != null && new File(thumbPath).isFile() ? thumbPath : null;
                photos.add(entry);
            }
            if (photos.size() != items.length()) {
                save(preferences, dialogId, topicId, photos);
            }
        } catch (Exception e) {
            FileLog.e(e);
            preferences.edit().remove(key(dialogId, topicId)).apply();
        }
        return photos;
    }
}
