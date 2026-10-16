package org.telegram.ui.Wallet;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.tl.TL_stars;
import java.util.ArrayList;

/** Account-local card decorations, in the card's 336 x 205 design space. */
final class WalletCardStickers {
    static final int LIMIT = 12;
    private static final String KEY = "wallet_card_stickers_v1";

    static class Sticker {
        final TL_stars.TL_starGiftUnique gift;
        float x, y, size, rotation;
        Sticker(TL_stars.TL_starGiftUnique gift, float x, float y, float size, float rotation) {
            this.gift = gift;
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
        }
        Sticker copy() { return new Sticker(gift, x, y, size, rotation); }
    }

    static ArrayList<Sticker> load(int account) {
        final ArrayList<Sticker> result = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(MessagesController.getMainSettings(account).getString(KEY, "[]"));
            for (int i = 0; i < Math.min(LIMIT, array.length()); i++) {
                final JSONObject item = array.getJSONObject(i);
                final SerializedData data = new SerializedData(Base64.decode(item.getString("gift"), Base64.DEFAULT));
                final TL_stars.StarGift gift;
                try { gift = TL_stars.StarGift.TLdeserialize(data, data.readInt32(true), true); }
                finally { data.cleanup(); }
                final float x = (float) item.getDouble("x"), y = (float) item.getDouble("y");
                final float size = (float) item.getDouble("size"), rotation = (float) item.getDouble("rotation");
                if (gift instanceof TL_stars.TL_starGiftUnique && gift.getDocument() != null && finite(x) && finite(y)
                        && finite(size) && finite(rotation) && size >= 22 && size <= 80
                        && x >= 0 && x <= 336 && y >= 0 && y <= 205) {
                    result.add(new Sticker((TL_stars.TL_starGiftUnique) gift, x, y, size, rotation));
                }
            }
        } catch (Exception e) { FileLog.e(e); }
        return result;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static void save(int account, ArrayList<Sticker> stickers) {
        final JSONArray array = new JSONArray();
        try {
            for (Sticker sticker : stickers) {
                final SerializedData data = new SerializedData(sticker.gift.getObjectSize());
                try {
                    sticker.gift.serializeToStream(data);
                    final JSONObject item = new JSONObject();
                    item.put("gift", Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP));
                    item.put("x", sticker.x).put("y", sticker.y).put("size", sticker.size).put("rotation", sticker.rotation);
                    array.put(item);
                } finally { data.cleanup(); }
            }
            MessagesController.getMainSettings(account).edit().putString(KEY, array.toString()).apply();
        } catch (Exception e) { FileLog.e(e); }
    }
}
