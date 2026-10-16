package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Components.BackupImageView;
import java.util.ArrayList;

/** Editing chrome stays out of the GL texture; sticker images live on the card face. */
final class WalletStickerEditor extends View {
    final ArrayList<WalletCardStickers.Sticker> stickers;
    private final WalletCardComponent card;
    private final View root, removeTarget, applyButton;
    private final Runnable changed;
    private final Matrix transform = new Matrix(), inverse = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect selectionDash = new DashPathEffect(new float[] { 4, 4 }, 0);
    private final int[] screenLocation = new int[2], rootLocation = new int[2];
    private final float[] mappedPoint = new float[2];
    private boolean draggingExisting;
    private int selected = -1;
    private float lastX, lastY;
    private boolean resizing, deleting;
    private WalletCardStickers.Sticker candidate;
    private WalletCardStickers.Sticker touchStart;
    private View dragSource;
    private float resizeStartRotation, downX, downY;
    private long liftedGiftId;
    private long skinGiftId;
    private float pinchDistance, pinchAngle, pinchSize, pinchRotation;
    private final Runnable liftSticker = this::liftSelectedSticker;

    private void liftSelectedSticker() {
        if (selected < 0 || selected >= stickers.size() || resizing) return;
        final View image = card.getStickerImage(selected);
        if (image != null) startGiftDrag(this, image, stickers.get(selected).gift);
    }

    WalletStickerEditor(Context context, WalletCardComponent card, View root, View removeTarget, View applyButton,
                        int account, Runnable changed) {
        super(context);
        this.card = card;
        this.root = root;
        this.removeTarget = removeTarget;
        this.applyButton = applyButton;
        this.changed = changed;
        stickers = WalletCardStickers.load(account);
        card.setStickers(stickers);
        setWillNotDraw(false);
    }

    boolean containsGift(long id) {
        for (WalletCardStickers.Sticker s : stickers) if (s.gift.id == id) return true;
        return false;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This overlay must not expand the wrap-content pinned preview to the
        // sheet's full height when FrameLayout first measures MATCH_PARENT.
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), card.getMeasuredHeight() + dp(8));
    }

    boolean isGiftLifted(long id) { return liftedGiftId == id; }

    void setSkinGift(TL_stars.TL_starGiftUnique gift) {
        skinGiftId = gift == null ? 0 : gift.id;
        boolean removed = false;
        for (int i = stickers.size() - 1; i >= 0; i--) {
            if (stickers.get(i).gift.id == skinGiftId) {
                stickers.remove(i);
                removed = true;
            }
        }
        if (removed) {
            selected = -1;
            removeCallbacks(liftSticker);
            card.setStickers(stickers);
            showRemoveTarget(false);
            changed.run();
            invalidate();
        }
    }

    boolean startGiftDrag(View tile, View image, TL_stars.TL_starGiftUnique gift) {
        if (gift.id == skinGiftId) return false;
        // A used tile is empty. Its sticker can only be picked up from the card.
        if (tile != this && containsGift(gift.id)) return false;
        if (gift.getDocument() == null || (!containsGift(gift.id) && stickers.size() >= WalletCardStickers.LIMIT)) return false;
        draggingExisting = tile == this;
        final int previousSelection = selected;
        selected = -1;
        final View shadowImage = image;
        final View.DragShadowBuilder shadow = new View.DragShadowBuilder(tile) {
            @Override public void onProvideShadowMetrics(Point size, Point touch) {
                size.set(dp(64), dp(64));
                touch.set(size.x / 2, size.y / 2);
            }
            @Override public void onDrawShadow(Canvas canvas) {
                if (shadowImage.getWidth() <= 0 || shadowImage.getHeight() <= 0) return;
                final Bitmap bitmap = shadowImage instanceof BackupImageView
                        ? ((BackupImageView) shadowImage).getImageReceiver().getBitmap() : null;
                if (bitmap != null && !bitmap.isRecycled()) {
                    final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    final RectF bounds = new RectF(dp(4), dp(4), dp(60), dp(60));
                    border.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                    for (int i = 0; i < 16; i++) {
                        canvas.save();
                        canvas.translate((float) Math.cos(i * Math.PI / 8) * dp(1.5f),
                                (float) Math.sin(i * Math.PI / 8) * dp(1.5f));
                        canvas.drawBitmap(bitmap, null, bounds, border);
                        canvas.restore();
                    }
                    border.setColorFilter(null);
                    canvas.drawBitmap(bitmap, null, bounds, border);
                    return;
                }
                canvas.save();
                canvas.scale(dp(64) / (float) shadowImage.getWidth(), dp(64) / (float) shadowImage.getHeight());
                shadowImage.draw(canvas);
                canvas.restore();
            }
        };
        final boolean started;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            started = tile.startDragAndDrop(null, shadow, gift, 0);
        } else {
            started = tile.startDrag(null, shadow, gift, 0);
        }
        if (started) {
            liftedGiftId = gift.id;
            dragSource = image;
            image.setAlpha(0f);
            showRemoveTarget(draggingExisting);
            tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            selected = previousSelection;
        }
        return started;
    }

    private float[] facePoint(View target, float x, float y) {
        if (!card.getFrontFaceTransform(target, transform) || !transform.invert(inverse)) return null;
        final float[] p = mappedPoint;
        p[0] = x;
        p[1] = y;
        inverse.mapPoints(p);
        p[0] /= AndroidUtilities.density;
        p[1] /= AndroidUtilities.density;
        return p;
    }

    private void showRemoveTarget(boolean visible) {
        removeTarget.setVisibility(visible ? VISIBLE : INVISIBLE);
        applyButton.setVisibility(visible ? INVISIBLE : VISIBLE);
    }

    boolean onRootDrag(DragEvent event) {
        if (!(event.getLocalState() instanceof TL_stars.TL_starGiftUnique)) return false;
        final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) event.getLocalState();
        if (gift.id == skinGiftId && event.getAction() != DragEvent.ACTION_DRAG_ENDED) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: return true;
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DROP:
                final float[] p = facePoint(root, event.getX(), event.getY());
                if (p == null) return false;
                if (candidate == null || candidate.gift.id != gift.id) {
                    candidate = new WalletCardStickers.Sticker(gift, p[0], p[1], 48, 0);
                } else {
                    candidate.x = p[0]; candidate.y = p[1];
                }
                int existing = -1;
                for (int i = 0; i < stickers.size(); i++) {
                    if (stickers.get(i).gift.id == gift.id) {
                        existing = i;
                        candidate.size = stickers.get(i).size;
                        candidate.rotation = stickers.get(i).rotation;
                        break;
                    }
                }
                final boolean valid = card.canPlaceSticker(candidate);
                removeTarget.getLocationOnScreen(screenLocation);
                root.getLocationOnScreen(rootLocation);
                final boolean remove = existing >= 0 && event.getY() + rootLocation[1] >= screenLocation[1]
                        && removeTarget.getVisibility() == VISIBLE;
                removeTarget.setAlpha(remove ? 1f : .65f);
                if (event.getAction() == DragEvent.ACTION_DROP && remove) {
                    stickers.remove(existing);
                    selected = -1;
                    card.setStickers(stickers);
                    showRemoveTarget(false);
                    changed.run();
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DROP && valid) {
                    if (existing >= 0 && !draggingExisting) return false;
                    if (existing >= 0) stickers.set(existing, candidate);
                    else if (stickers.size() < WalletCardStickers.LIMIT) stickers.add(candidate);
                    else return false;
                    selected = existing >= 0 ? existing : stickers.size() - 1;
                    card.setStickers(stickers);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    changed.run();
                }
                invalidate();
                return event.getAction() != DragEvent.ACTION_DROP || valid;
            case DragEvent.ACTION_DRAG_EXITED:
                candidate = null;
                invalidate();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                candidate = null;
                liftedGiftId = 0;
                if (dragSource != null) dragSource.setAlpha(1f);
                dragSource = null;
                showRemoveTarget(false);
                changed.run();
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        final float[] p = facePoint(this, event.getX(), event.getY());
        if (p == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                resizing = false;
                if (selected >= 0 && selected < stickers.size()) {
                    final WalletCardStickers.Sticker s = stickers.get(selected);
                    final float radius = s.size * .72f + 5;
                    resizing = Math.hypot(p[0] - s.x - radius, p[1] - s.y) < 13;
                }
                if (!resizing) {
                    selected = -1;
                    for (int i = stickers.size() - 1; i >= 0; i--) {
                        final WalletCardStickers.Sticker s = stickers.get(i);
                        if (Math.hypot(p[0] - s.x, p[1] - s.y) <= s.size * .65f) { selected = i; break; }
                    }
                }
                invalidate();
                if (selected < 0) return false;
                resizeStartRotation = stickers.get(selected).rotation;
                touchStart = stickers.get(selected).copy();
                lastX = p[0]; lastY = p[1];
                downX = p[0]; downY = p[1];
                if (!resizing) postDelayed(liftSticker, android.view.ViewConfiguration.getLongPressTimeout());
                getParent().requestDisallowInterceptTouchEvent(true);
                root.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(liftSticker);
                if (selected < 0 || event.getPointerCount() != 2) return selected >= 0;
                final float firstX = p[0], firstY = p[1];
                final float[] second = facePoint(this, event.getX(1), event.getY(1));
                if (second == null) return true;
                pinchDistance = Math.max(1, (float) Math.hypot(second[0] - firstX, second[1] - firstY));
                pinchAngle = (float) Math.atan2(second[1] - firstY, second[0] - firstX);
                pinchSize = stickers.get(selected).size;
                pinchRotation = stickers.get(selected).rotation;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinchDistance = 0;
                removeCallbacks(liftSticker);
                lastX = p[0]; lastY = p[1];
                return selected >= 0;
            case MotionEvent.ACTION_MOVE:
                if (selected < 0) return false;
                if (Math.hypot(p[0] - downX, p[1] - downY) > 4) removeCallbacks(liftSticker);
                final WalletCardStickers.Sticker current = stickers.get(selected);
                final WalletCardStickers.Sticker next = current.copy();
                if (event.getPointerCount() >= 2 && pinchDistance > 0) {
                    final float primaryX = p[0], primaryY = p[1];
                    final float[] other = facePoint(this, event.getX(1), event.getY(1));
                    if (other == null) return true;
                    final float distance = (float) Math.hypot(other[0] - primaryX, other[1] - primaryY);
                    final float angle = (float) Math.atan2(other[1] - primaryY, other[0] - primaryX);
                    next.size = Math.max(22, Math.min(80, pinchSize * distance / pinchDistance));
                    next.rotation = pinchRotation + (float) Math.toDegrees(angle - pinchAngle);
                } else if (resizing) {
                    next.size = Math.max(22, Math.min(80, (float) Math.hypot(p[0] - current.x, p[1] - current.y) / .72f));
                    next.rotation = resizeStartRotation + (float) Math.toDegrees(Math.atan2(p[1] - current.y, p[0] - current.x));
                } else {
                    next.x += p[0] - lastX;
                    next.y += p[1] - lastY;
                }
                showRemoveTarget(!resizing && event.getPointerCount() == 1);
                removeTarget.getLocationOnScreen(screenLocation);
                deleting = !resizing && event.getPointerCount() == 1 && event.getRawY() >= screenLocation[1] - dp(16);
                removeTarget.setAlpha(deleting ? 1f : .65f);
                if (card.canPlaceSticker(next)) {
                    stickers.set(selected, next);
                    card.updateStickerTransform(selected, next);
                } else if (!resizing && event.getPointerCount() == 1) {
                    // The card owns only valid placements. Once a moving model
                    // leaves it, the system drag shadow follows the finger over
                    // the entire sheet, including the removal target.
                    final View image = card.getStickerImage(selected);
                    if (image != null && startGiftDrag(this, image, current.gift)) return true;
                }
                lastX = p[0]; lastY = p[1];
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(liftSticker);
                if (selected < 0) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && touchStart != null) {
                    stickers.set(selected, touchStart);
                    card.updateStickerTransform(selected, touchStart);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP && deleting) {
                    stickers.remove(selected);
                    selected = -1;
                    card.setStickers(stickers);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                deleting = false;
                pinchDistance = 0;
                showRemoveTarget(false);
                getParent().requestDisallowInterceptTouchEvent(false);
                root.getParent().requestDisallowInterceptTouchEvent(false);
                changed.run();
                invalidate();
                return true;
        }
        return selected >= 0;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final WalletCardStickers.Sticker s = candidate != null ? candidate
                : selected >= 0 && selected < stickers.size() ? stickers.get(selected) : null;
        if (s == null || !card.getFrontFaceTransform(this, transform)) return;
        canvas.save();
        canvas.concat(transform);
        canvas.scale(AndroidUtilities.density, AndroidUtilities.density);
        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(candidate != null && !card.canPlaceSticker(s) ? 0xFFFF5555 : Color.WHITE);
        paint.setPathEffect(selectionDash);
        final float radius = s.size * .72f + 5;
        canvas.drawCircle(s.x, s.y, radius, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(s.x + radius, s.y, 4, paint);
        canvas.restore();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(liftSticker);
        showRemoveTarget(false);
        super.onDetachedFromWindow();
    }
}
