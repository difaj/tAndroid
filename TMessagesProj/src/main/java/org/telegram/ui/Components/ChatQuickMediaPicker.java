package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** A recent-media picker aligned with the chat input field. */
public class ChatQuickMediaPicker implements NotificationCenter.NotificationCenterDelegate {

    public interface Delegate {
        void openCamera();
        void selectionChanged(ArrayList<MediaController.PhotoEntry> selected);
    }

    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Delegate delegate;
    private final LinkedHashMap<Integer, MediaController.PhotoEntry> selected = new LinkedHashMap<>();
    private final HorizontalScrollView scroller;
    private final LinearLayout mediaRow;
    private final PopupWindow popupWindow;
    private MediaController.AlbumEntry mediaAlbum;
    private int nextMediaIndex;
    private boolean loadingGallery;

    public ChatQuickMediaPicker(Context context, Theme.ResourcesProvider resourcesProvider,
                                BlurredBackgroundDrawableViewFactory glassFactory,
                                BlurredBackgroundColorProvider glassColorProvider, Delegate delegate) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        this.delegate = delegate;

        LinearLayout card = new LinearLayout(context);
        card.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        card.setBackground(glassFactory.create(card, glassColorProvider, true)
                .setRadius(AndroidUtilities.dp(22))
                .setPadding(AndroidUtilities.dp(7)));
        card.setClipToOutline(true);

        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        mediaRow = new LinearLayout(context);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(mediaRow);
        scroller.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollX + scroller.getWidth() >= mediaRow.getWidth() - AndroidUtilities.dp(68)) {
                appendMediaBatch();
            }
        });
        card.addView(scroller, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(68)));

        popupWindow = new PopupWindow(card, -1, AndroidUtilities.dp(88), false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setOnDismissListener(() -> NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad));
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void show(View anchor, int inputTopOnScreen, ArrayList<MediaController.PhotoEntry> existingSelection) {
        if (popupWindow.isShowing()) {
            dismiss();
            return;
        }
        selected.clear();
        for (MediaController.PhotoEntry entry : existingSelection) {
            selected.put(entry.imageId, entry);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        refreshMedia();
        popupWindow.setWidth(anchor.getWidth());
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int yOffset = inputTopOnScreen + AndroidUtilities.dp(8)
                - anchorLocation[1] - anchor.getHeight() - popupWindow.getHeight();
        popupWindow.showAsDropDown(anchor, 0,
                yOffset, Gravity.LEFT);
    }

    public void dismiss() {
        popupWindow.dismiss();
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private ArrayList<MediaController.PhotoEntry> getSelected() {
        return new ArrayList<>(selected.values());
    }

    private void refreshMedia() {
        mediaAlbum = null;
        nextMediaIndex = 0;
        mediaRow.removeAllViews();
        scroller.scrollTo(0, 0);
        FrameLayout cameraTile = new FrameLayout(context);
        cameraTile.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9),
                Theme.multAlpha(color(Theme.key_glass_defaultIcon), 0.08f)));
        ImageView cameraIcon = new ImageView(context);
        cameraIcon.setImageResource(R.drawable.outline_attach_camera_24);
        cameraIcon.setColorFilter(new PorterDuffColorFilter(color(Theme.key_glass_defaultIcon), PorterDuff.Mode.SRC_IN));
        cameraTile.addView(cameraIcon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        cameraTile.setContentDescription(LocaleController.getString(R.string.VoipCamera));
        addTile(cameraTile);
        cameraTile.setOnClickListener(v -> {
            dismiss();
            delegate.openCamera();
        });

        MediaController.AlbumEntry album = MediaController.allMediaAlbumEntry;
        if (album == null) {
            if (!loadingGallery) {
                loadingGallery = true;
                MediaController.loadGalleryPhotosAlbums(0);
            }
            return;
        }
        loadingGallery = false;
        mediaAlbum = album;
        appendMediaBatch();
    }

    private void appendMediaBatch() {
        if (mediaAlbum == null || nextMediaIndex >= mediaAlbum.photos.size()) {
            return;
        }
        int end = Math.min(nextMediaIndex + 20, mediaAlbum.photos.size());
        int start = nextMediaIndex;
        nextMediaIndex = end;
        for (int i = start; i < end; i++) {
            MediaController.PhotoEntry entry = mediaAlbum.photos.get(i);
            FrameLayout tile = new FrameLayout(context);
            BackupImageView image = new BackupImageView(context);
            image.setRoundRadius(AndroidUtilities.dp(9));
            image.getImageReceiver().setAspectFit(false);
            String path = entry.thumbPath != null ? entry.thumbPath : (entry.isVideo ? "vthumb://" : "thumb://") + entry.imageId + ":" + entry.path;
            image.setImage(path, "72_72", Theme.chat_attachEmptyDrawable);
            tile.addView(image, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            if (entry.isVideo) {
                ImageView videoIcon = new ImageView(context);
                videoIcon.setImageResource(R.drawable.play_mini_video);
                tile.addView(videoIcon, LayoutHelper.createFrame(20, 20, Gravity.BOTTOM | Gravity.LEFT, 4, 0, 0, 4));
            }
            TextView badge = new TextView(context);
            badge.setTextSize(12);
            badge.setTextColor(0xffffffff);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(11), color(Theme.key_dialogButton)));
            tile.addView(badge, LayoutHelper.createFrame(22, 22, Gravity.TOP | Gravity.RIGHT, 0, 3, 3, 0));
            badge.setVisibility(View.GONE);
            addTile(tile);
            tile.setOnClickListener(v -> {
                if (selected.remove(entry.imageId) == null) {
                    selected.put(entry.imageId, entry);
                }
                delegate.selectionChanged(getSelected());
                dismiss();
            });
            tile.setTag(new Object[]{entry.imageId, badge});
        }
        updateBadges();
    }

    private void addTile(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
        params.rightMargin = AndroidUtilities.dp(4);
        mediaRow.addView(view, params);
    }

    private void updateBadges() {
        int index = 1;
        LinkedHashMap<Integer, Integer> order = new LinkedHashMap<>();
        for (Integer id : selected.keySet()) {
            order.put(id, index++);
        }
        for (int i = 1; i < mediaRow.getChildCount(); i++) {
            View tile = mediaRow.getChildAt(i);
            Object[] data = (Object[]) tile.getTag();
            TextView badge = (TextView) data[1];
            Integer position = order.get((Integer) data[0]);
            badge.setVisibility(position == null ? View.GONE : View.VISIBLE);
            if (position != null) {
                badge.setText(String.valueOf(position));
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad && isShowing()) {
            refreshMedia();
        }
    }
}
