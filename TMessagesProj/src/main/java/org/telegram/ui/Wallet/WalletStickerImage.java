package org.telegram.ui.Wallet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.LruCache;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BackupImageView;

/** Rasterize the white die-cut border once, rather than tracing it every GL frame. */
final class WalletStickerImage extends BackupImageView {
    private static final LruCache<Long, Bitmap> CACHE = new LruCache<Long, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(Long key, Bitmap value) { return value.getByteCount(); }
    };
    private final long documentId;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private Bitmap outlined;
    private boolean preparing;

    WalletStickerImage(Context context, long documentId) {
        super(context);
        this.documentId = documentId;
        outlined = CACHE.get(documentId);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (outlined == null) outlined = CACHE.get(documentId);
        if (outlined != null) {
            bounds.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(outlined, null, bounds, paint);
            return;
        }
        super.onDraw(canvas);
        final Bitmap bitmap = getImageReceiver().getBitmap();
        if (preparing || !getImageReceiver().hasNotThumb() || bitmap == null || bitmap.isRecycled()) return;
        preparing = true;
        // Capture the current model frame before handing it to the worker.
        final Bitmap snapshot = Bitmap.createScaledBitmap(bitmap, 116, 116, true).copy(Bitmap.Config.ARGB_8888, false);
        Utilities.globalQueue.postRunnable(() -> {
            final Bitmap result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            final Canvas out = new Canvas(result);
            final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            border.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            for (int i = 0; i < 16; i++) {
                final double angle = i * Math.PI / 8;
                out.drawBitmap(snapshot, 6 + (float) Math.cos(angle) * 3.5f,
                        6 + (float) Math.sin(angle) * 3.5f, border);
            }
            border.setColorFilter(null);
            out.drawBitmap(snapshot, 6, 6, border);
            snapshot.recycle();
            CACHE.put(documentId, result);
            AndroidUtilities.runOnUIThread(() -> {
                outlined = result;
                getImageReceiver().stopAnimation();
                invalidate();
            });
        });
    }
}
