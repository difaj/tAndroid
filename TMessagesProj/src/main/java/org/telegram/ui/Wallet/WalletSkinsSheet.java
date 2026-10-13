package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/** Selects one of the same owned collectible gifts shown in profile color settings. */
public class WalletSkinsSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private static final int DEFAULT_SKIN = 1;
    static { UItem.UItemFactory.setup(new DefaultSkinCell.Factory()); }
    private final StarsController.GiftsList gifts;
    private final long selectedGiftId;
    private final Utilities.Callback<TL_stars.TL_starGiftUnique> whenSelected;
    private UniversalAdapter adapter;

    public WalletSkinsSheet(BaseFragment fragment, StarsController.GiftsList gifts,
                            long selectedGiftId, Utilities.Callback<TL_stars.TL_starGiftUnique> whenSelected) {
        super(fragment.getParentActivity(), fragment, new Params.Builder()
                .actionBarType(ActionBarType.SLIDING)
                .edgeToEdge(EdgeToEdge.V2)
                .resourcesProvider(fragment.getResourceProvider())
                .build());
        this.gifts = gifts;
        this.selectedGiftId = selectedGiftId;
        this.whenSelected = whenSelected;
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        setSlidingActionBar();

        final ExtendedGridLayoutManager layoutManager = new ExtendedGridLayoutManager(getContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                final UItem item = adapter == null ? null : adapter.getItem(position - 1);
                return item == null || item.spanCount == UItem.MAX_SPAN_COUNT ? 3 : item.spanCount;
            }
        });
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setPadding(backgroundPaddingLeft + dp(9), 0, backgroundPaddingLeft + dp(9), 0);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSelectorType(9);
        recyclerListView.setSelectorDrawableColor(0);
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter == null ? null : adapter.getItem(position - 1);
            if (item == null) return;
            if (item.id == DEFAULT_SKIN) {
                whenSelected.run(null);
                dismiss();
            } else if (item.object instanceof TL_stars.SavedStarGift) {
                final TL_stars.SavedStarGift saved = (TL_stars.SavedStarGift) item.object;
                if (saved.gift instanceof TL_stars.TL_starGiftUnique) {
                    whenSelected.run((TL_stars.TL_starGiftUnique) saved.gift);
                    dismiss();
                }
            }
        });
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!gifts.loading && !gifts.endReached
                        && layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    gifts.load();
                }
            }
        });
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        if (!gifts.loading && !gifts.endReached) gifts.load();
        adapter.update(false);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.WalletSkins);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0,
                this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (gifts == null) return;
        items.add(UItem.asSpace(dp(12)));
        items.add(UItem.ofFactory(DefaultSkinCell.Factory.class).setId(DEFAULT_SKIN)
                .setChecked(selectedGiftId == 0).setSpanCount(1));

        int uniqueCount = 0;
        for (TL_stars.SavedStarGift saved : gifts.gifts) {
            if (!(saved.gift instanceof TL_stars.TL_starGiftUnique)) continue;
            uniqueCount++;
            items.add(PeerColorActivity.GiftCell.Factory.asGiftCell(saved)
                    .setChecked(saved.gift.id == selectedGiftId).setSpanCount(1));
        }
        if (uniqueCount == 0 && gifts.endReached) {
            items.add(UItem.asShadow(getString(R.string.WalletSkinsEmpty)));
        }
        if (gifts.loading || !gifts.endReached) {
            final int loadingCells = uniqueCount == 0 ? 8 : 3 - (uniqueCount + 1) % 3;
            for (int i = 0; i < loadingCells; i++) {
                items.add(UItem.asFlicker(100 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
        }
        items.add(UItem.asSpace(dp(24)));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded && account == currentAccount
                && args.length > 1 && args[1] == gifts && adapter != null) {
            adapter.update(false);
        }
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        super.dismiss();
    }

    /** A first-class tile, sized like an owned gift, for restoring the blue card. */
    private static class DefaultSkinCell extends FrameLayout {
        private final GradientDrawable background;

        DefaultSkinCell(Context context) {
            super(context);
            setMinimumHeight(dp(104));
            final FrameLayout card = new FrameLayout(context);
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT, Gravity.FILL, 3, 4, 3, 4));
            final boolean dark = Theme.isCurrentThemeDark();
            background = new GradientDrawable();
            background.setColor(dark ? 0xFF55575C : 0xFFD6D8DC);
            background.setCornerRadius(dp(11));
            card.setBackground(background);
            ScaleStateListAnimator.apply(card, 0.025f, 1.25f);

            final TextView label = new TextView(context);
            label.setText(getString(R.string.WalletSkinDefault));
            label.setTextColor(dark ? Color.WHITE : 0xFF404348);
            label.setTextSize(16);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            card.addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT, Gravity.CENTER, 4, 0, 4, 0));
        }

        void setSelectedSkin(boolean selected) {
            background.setStroke(dp(selected ? 2 : 0), selected ? Color.WHITE : Color.TRANSPARENT);
            invalidate();
        }

        static class Factory extends UItem.UItemFactory<DefaultSkinCell> {
            @Override
            public DefaultSkinCell createView(Context context, RecyclerListView listView, int currentAccount,
                                              int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DefaultSkinCell(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                 org.telegram.ui.Components.UniversalRecyclerView listView) {
                ((DefaultSkinCell) view).setSelectedSkin(item.checked);
            }
        }
    }
}
