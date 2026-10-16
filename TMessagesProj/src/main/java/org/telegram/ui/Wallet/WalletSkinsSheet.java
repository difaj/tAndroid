package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Gifts.ResaleGiftsFragment;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

/** Card preview with the owned-gift and resale collections used by profile colors. */
public class WalletSkinsSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private static final int DEFAULT_SKIN = 1;
    private static final int PREVIEW = 2;
    private static final int TABS = 3;
    static {
        UItem.UItemFactory.setup(new DefaultSkinCell.Factory());
        UItem.UItemFactory.setup(new PreviewCell.Factory());
        UItem.UItemFactory.setup(new ShopTabsCell.Factory());
    }
    private final StarsController.GiftsList gifts;
    private final long selectedGiftId;
    private final Utilities.Callback<TL_stars.TL_starGiftUnique> whenSelected;
    private TL_stars.TL_starGiftUnique previewGift;
    private boolean previewTouched;
    private TL_stars.TL_starGiftUnique resaleGift;
    private TL_stars.StarGift selectedCollection;
    private ResaleGiftsFragment.ResaleGiftsList resaleGifts;
    private final ArrayList<TL_stars.StarGift> shopCollections = new ArrayList<>();
    private WalletCardComponent previewCard;
    private FrameLayout pinnedPreview;
    private FrameLayout pinnedControls;
    private ShopTabsCell pinnedTabs;
    private HorizontalScrollView filtersScroll;
    private LinearLayout filtersLayout;
    private ResaleGiftsFragment.Filter sortFilter;
    private ResaleGiftsFragment.Filter modelFilter;
    private ResaleGiftsFragment.Filter backdropFilter;
    private ResaleGiftsFragment.Filter patternFilter;
    private ButtonWithCounterView actionButton;
    private UniversalAdapter adapter;
    private int previewReservedHeight;
    private WalletStickerEditor stickerEditor;

    public WalletSkinsSheet(BaseFragment fragment, StarsController.GiftsList gifts,
                            long selectedGiftId, TL_stars.TL_starGiftUnique selectedGift,
                            Utilities.Callback<TL_stars.TL_starGiftUnique> whenSelected) {
        super(fragment.getParentActivity(), fragment, new Params.Builder()
                .actionBarType(ActionBarType.SLIDING)
                .edgeToEdge(EdgeToEdge.V2)
                .resourcesProvider(fragment.getResourceProvider())
                .build());
        this.gifts = gifts;
        this.selectedGiftId = selectedGiftId;
        this.previewGift = selectedGift;
        this.whenSelected = whenSelected;
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
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
        recyclerListView.setPadding(backgroundPaddingLeft + dp(9), 0,
                backgroundPaddingLeft + dp(9), dp(82));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSelectorType(9);
        recyclerListView.setSelectorDrawableColor(0);
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter == null ? null : adapter.getItem(position - 1);
            if (item == null) return;
            if (item.id == DEFAULT_SKIN) {
                selectPreview(null, null);
            } else if (item.object instanceof TL_stars.SavedStarGift) {
                final TL_stars.SavedStarGift saved = (TL_stars.SavedStarGift) item.object;
                if (saved.gift instanceof TL_stars.TL_starGiftUnique) {
                    selectPreview((TL_stars.TL_starGiftUnique) saved.gift, null);
                }
            } else if (item.object instanceof TL_stars.TL_starGiftUnique) {
                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) item.object;
                selectPreview(gift, gift);
            }
        });
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (selectedCollection == null && !gifts.loading && !gifts.endReached
                        && layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    gifts.load();
                } else if (selectedCollection != null && resaleGifts != null
                        && !resaleGifts.loading && !resaleGifts.endReached
                        && layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    resaleGifts.load();
                }
            }
        });
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        StarsController.getInstance(currentAccount).loadStarGifts();
        if (!gifts.loading && !gifts.endReached) gifts.load();
        resolveInitialGift();
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        previewCard.listen(currentAccount, wallet);
        previewCard.setSkinGift(previewGift);
        previewCard.update(wallet);
        final FrameLayout editorRoot = (FrameLayout) pinnedPreview.getParent();
        final TextView removeSticker = new TextView(getContext());
        removeSticker.setText(getString(R.string.Delete));
        removeSticker.setTextSize(16);
        removeSticker.setTextColor(Color.WHITE);
        removeSticker.setGravity(Gravity.CENTER);
        removeSticker.setBackground(Theme.createRoundRectDrawable(dp(24), 0xFFE74B45));
        removeSticker.setVisibility(View.INVISIBLE);
        editorRoot.addView(removeSticker, LayoutHelper.createFrame(140, 48,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));
        stickerEditor = new WalletStickerEditor(getContext(), previewCard, editorRoot,
                removeSticker, actionButton, currentAccount, () -> editorRoot.invalidate());
        stickerEditor.setSkinGift(previewGift);
        pinnedPreview.addView(stickerEditor, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        editorRoot.setOnDragListener((v, event) -> stickerEditor.onRootDrag(event));
        recyclerListView.setOnItemLongClickListener((RecyclerListView.OnItemLongClickListener) (view, position) -> {
            final UItem item = adapter == null ? null : adapter.getItem(position - 1);
            if (selectedCollection != null || item == null
                    || !(item.object instanceof TL_stars.SavedStarGift)
                    || !(view instanceof PeerColorActivity.GiftCell)) return false;
            final TL_stars.SavedStarGift saved = (TL_stars.SavedStarGift) item.object;
            return saved.gift instanceof TL_stars.TL_starGiftUnique
                    && stickerEditor.startGiftDrag(view, ((PeerColorActivity.GiftCell) view).imageView,
                    (TL_stars.TL_starGiftUnique) saved.gift);
        });
        refreshCollections();
        updatePinnedControls();
        adapter.update(false);
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);
        pinnedPreview = new FrameLayout(getContext());
        previewCard = new WalletCardComponent(getContext(), false);
        previewCard.setUseGyroscope(0f);
        previewCard.setDiamondOnCard(true);
        pinnedPreview.addView(previewCard, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP,
                12, 0, 12, 8));
        // Below the action bar, but above the scrolling gift grid.
        containerView.addView(pinnedPreview, 1, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        pinnedControls = new FrameLayout(getContext());
        pinnedControls.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        pinnedTabs = new ShopTabsCell(getContext(), resourcesProvider);
        pinnedControls.addView(pinnedTabs, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 44, Gravity.TOP));

        filtersLayout = new LinearLayout(getContext());
        filtersLayout.setOrientation(LinearLayout.HORIZONTAL);
        filtersLayout.setPadding(dp(11), dp(6), dp(11), dp(6));
        filtersScroll = new HorizontalScrollView(getContext());
        filtersScroll.setHorizontalScrollBarEnabled(false);
        filtersScroll.setClipToPadding(false);
        filtersScroll.addView(filtersLayout);
        pinnedControls.addView(filtersScroll, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 47, Gravity.TOP, 0, 44, 0, 0));
        createFilters();
        containerView.addView(pinnedControls, 2, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 91, Gravity.TOP));
        actionButton = new ButtonWithCounterView(getContext(), resourcesProvider).setRound();
        actionButton.setOnClickListener(v -> applyOrBuy());
        containerView.addView(actionButton, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 18, 0, 18, 16));
        updateActionButton();
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        if (stickerEditor != null) {
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                final View view = recyclerListView.getChildAt(i);
                if (!(view instanceof PeerColorActivity.GiftCell)) continue;
                final int position = recyclerListView.getChildAdapterPosition(view);
                final UItem item = adapter == null ? null : adapter.getItem(position - 1);
                if (item == null || !(item.object instanceof TL_stars.SavedStarGift)) continue;
                final TL_stars.SavedStarGift saved = (TL_stars.SavedStarGift) item.object;
                final org.telegram.ui.Components.BackupImageView image = ((PeerColorActivity.GiftCell) view).imageView;
                final boolean used = stickerEditor.containsGift(saved.gift.id) || stickerEditor.isGiftLifted(saved.gift.id);
                final int visibility = used ? View.INVISIBLE : View.VISIBLE;
                if (image.getVisibility() != visibility) {
                    image.setVisibility(visibility);
                    image.getImageReceiver().setAllowStartAnimation(!used);
                    if (used) image.getImageReceiver().stopAnimation();
                    else image.getImageReceiver().startAnimation();
                }
            }
        }
        if (pinnedPreview != null && actionBar != null) {
            final float y = actionBar.getY() + actionBar.getHeight() + dp(8);
            if (pinnedPreview.getTranslationY() != y) pinnedPreview.setTranslationY(y);
            final float controlsY = y + pinnedPreview.getHeight();
            if (pinnedControls != null && pinnedControls.getTranslationY() != controlsY) {
                pinnedControls.setTranslationY(controlsY);
            }
            // Match the profile-color editor: a fixed preview with the grid
            // starting below it, rather than another opaque panel over the list.
            final int listTop = Math.min(recyclerListView.getHeight(), Math.max(0,
                    Math.round(controlsY + (pinnedControls == null ? 0 : pinnedControls.getHeight())
                            - recyclerListView.getY())));
            // Derive the reserved row from the actual pinned layout. The sliding
            // action bar includes status/header insets that a card-aspect estimate
            // cannot account for. Only calibrate while the sheet is collapsed;
            // scrolling the expanded grid must still move rows behind the header.
            final RecyclerView.ViewHolder padding = recyclerListView.findViewHolderForAdapterPosition(0);
            final RecyclerView.ViewHolder preview = recyclerListView.findViewHolderForAdapterPosition(2);
            if (progressToFullView == 0f && padding != null && preview != null
                    && padding.itemView.getBottom() > actionBar.getHeight()) {
                final int reserved = Math.max(pinnedPreview.getHeight(),
                        listTop - padding.itemView.getBottom() - dp(12)
                                - (pinnedControls == null ? 0 : pinnedControls.getHeight()));
                if (previewReservedHeight != reserved) {
                    previewReservedHeight = reserved;
                    preview.itemView.requestLayout();
                }
            }
            final Rect clip = recyclerListView.getClipBounds();
            if (clip == null || clip.top != listTop || clip.right != recyclerListView.getWidth()
                    || clip.bottom != recyclerListView.getHeight()) {
                recyclerListView.setClipBounds(new Rect(0, listTop,
                        recyclerListView.getWidth(), recyclerListView.getHeight()));
            }
        }
    }

    private void selectPreview(TL_stars.TL_starGiftUnique gift, TL_stars.TL_starGiftUnique resale) {
        previewTouched = true;
        previewGift = gift;
        resaleGift = resale;
        if (previewCard != null) previewCard.setSkinGift(gift);
        if (stickerEditor != null) stickerEditor.setSkinGift(gift);
        updateActionButton();
        adapter.update(false);
    }

    private void resolveInitialGift() {
        if (previewTouched || previewGift != null || selectedGiftId == 0) return;
        for (TL_stars.SavedStarGift saved : gifts.gifts) {
            if (saved.gift instanceof TL_stars.TL_starGiftUnique && saved.gift.id == selectedGiftId) {
                previewGift = (TL_stars.TL_starGiftUnique) saved.gift;
                if (previewCard != null) previewCard.setSkinGift(previewGift);
                if (stickerEditor != null) stickerEditor.setSkinGift(previewGift);
                return;
            }
        }
    }

    private void updateActionButton() {
        if (actionButton == null) return;
        if (resaleGift != null) {
            if (resaleGift.resale_ton_only) {
                final CharSequence label = org.telegram.messenger.LocaleController.formatString(R.string.ResellGiftBuyTON,
                        resaleGift.getResellAmount(AmountUtils.Currency.TON).asFormatString());
                actionButton.setText(StarsIntroActivity.replaceWhiteGram(label, 1.4f));
            } else {
                actionButton.setText(StarsIntroActivity.replaceStars(
                        org.telegram.messenger.LocaleController.formatPluralStringComma("ResellGiftBuy",
                                (int) resaleGift.getResellAmount(AmountUtils.Currency.STARS).asDecimal())));
            }
        } else {
            actionButton.setText(getString(R.string.ApplyTheme));
        }
    }

    private void applyOrBuy() {
        if (actionButton == null || actionButton.isLoading()) return;
        if (resaleGift == null) {
            if (stickerEditor != null) WalletCardStickers.save(currentAccount, stickerEditor.stickers);
            whenSelected.run(previewGift);
            dismiss();
            return;
        }
        final TL_stars.TL_starGiftUnique gift = resaleGift;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final AmountUtils.Currency currency = gift.resale_ton_only
                ? AmountUtils.Currency.TON : AmountUtils.Currency.STARS;
        actionButton.setLoading(true);
        StarsController.getInstance(currentAccount, currency).getResellingGiftForm(gift, selfId, form -> {
            if (form == null) {
                actionButton.setLoading(false);
                return;
            }
            actionButton.setLoading(false);
            final StarGiftSheet.PaymentFormState initial = new StarGiftSheet.PaymentFormState(currency, form);
            new StarGiftSheet.ResaleBuyTransferAlert(getContext(), resourcesProvider, gift, initial,
                    currentAccount, selfId, gift.title + " #" + org.telegram.messenger.LocaleController.formatNumber(gift.num, ','),
                    false, (state, progress) -> {
                progress.init();
                StarsController.getInstance(currentAccount, state.currency).buyResellingGift(
                        state.form, gift, selfId, (success, error) -> {
                    progress.end();
                    if (success) {
                        gifts.invalidate(true);
                        if (stickerEditor != null) WalletCardStickers.save(currentAccount, stickerEditor.stickers);
                        whenSelected.run(gift);
                        dismiss();
                    }
                });
            }).show();
        });
    }

    private void refreshCollections() {
        final long selectedCollectionId = selectedCollection == null ? 0 : selectedCollection.id;
        selectedCollection = null;
        shopCollections.clear();
        for (TL_stars.StarGift gift : StarsController.getInstance(currentAccount).sortedGifts) {
            if (gift.availability_resale > 0) {
                shopCollections.add(gift);
                if (gift.id == selectedCollectionId) selectedCollection = gift;
            }
        }
        if (selectedCollectionId != 0 && selectedCollection == null && resaleGifts != null) {
            resaleGifts.cancel();
            resaleGifts = null;
        }
    }

    private ArrayList<CharSequence> getTabs() {
        final ArrayList<CharSequence> tabs = new ArrayList<>();
        tabs.add(getString(R.string.Gift2TabMine));
        for (TL_stars.StarGift gift : shopCollections) {
            final SpannableStringBuilder label = new SpannableStringBuilder("x ");
            final TextPaint paint = new TextPaint();
            paint.setTextSize(dp(14));
            final AnimatedEmojiSpan icon = new AnimatedEmojiSpan(gift.getDocument(), paint.getFontMetricsInt());
            icon.size = dp(14);
            label.setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            label.append(gift.title);
            tabs.add(label);
        }
        return tabs;
    }

    private int getSelectedTab() {
        return selectedCollection == null ? 0 : Math.max(0, shopCollections.indexOf(selectedCollection) + 1);
    }

    private void selectTab(int index) {
        final TL_stars.StarGift collection = index <= 0 || index > shopCollections.size()
                ? null : shopCollections.get(index - 1);
        if (selectedCollection == collection) return;
        if (resaleGifts != null) resaleGifts.cancel();
        selectedCollection = collection;
        resaleGifts = collection == null ? null : new ResaleGiftsFragment.ResaleGiftsList(
                currentAccount, collection.id, loaded -> {
                    adapter.update(false);
                    updateFilterLabels();
                });
        if (resaleGifts != null) resaleGifts.load();
        resaleGift = null;
        updateActionButton();
        updatePinnedControls();
        adapter.update(false);
        // Each collection starts at its first row; the previous grid's scroll
        // offset must not leave new gifts underneath the pinned filters.
        ((GridLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }

    private void createFilters() {
        sortFilter = new ResaleGiftsFragment.Filter(getContext(), resourcesProvider);
        filtersLayout.addView(sortFilter, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        sortFilter.setOnClickListener(v -> showSortingOptions());

        modelFilter = new ResaleGiftsFragment.Filter(getContext(), resourcesProvider);
        filtersLayout.addView(modelFilter, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        modelFilter.setOnClickListener(v -> showModelOptions());

        backdropFilter = new ResaleGiftsFragment.Filter(getContext(), resourcesProvider);
        filtersLayout.addView(backdropFilter, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        backdropFilter.setOnClickListener(v -> showBackdropOptions());

        patternFilter = new ResaleGiftsFragment.Filter(getContext(), resourcesProvider);
        filtersLayout.addView(patternFilter, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        patternFilter.setOnClickListener(v -> showPatternOptions());
    }

    private void updatePinnedControls() {
        if (pinnedTabs == null) return;
        pinnedTabs.set(TABS, getTabs(), getSelectedTab(), this::selectTab);
        final boolean shop = resaleGifts != null;
        filtersScroll.setVisibility(shop ? View.VISIBLE : View.GONE);
        final FrameLayout.LayoutParams controlsParams = (FrameLayout.LayoutParams) pinnedControls.getLayoutParams();
        if (controlsParams != null) {
            final int height = dp(shop ? 91 : 44);
            if (controlsParams.height != height) {
                controlsParams.height = height;
                pinnedControls.setLayoutParams(controlsParams);
            }
        }
        if (!shop) return;
        updateFilterLabels();
    }

    private void updateFilterLabels() {
        if (resaleGifts == null || sortFilter == null) return;
        sortFilter.setSorting(resaleGifts.getSorting());
        modelFilter.setValue(filterLabel(R.string.Gift2ResaleFilterModel,
                resaleGifts.modelAttributes.size(), resaleGifts.notSelectedModelAttributes.size(),
                "Gift2ResaleFilterModels"));
        backdropFilter.setValue(filterLabel(R.string.Gift2ResaleFilterBackdrop,
                resaleGifts.backdropAttributes.size(), resaleGifts.notSelectedBackdropAttributes.size(),
                "Gift2ResaleFilterBackdrops"));
        patternFilter.setValue(filterLabel(R.string.Gift2ResaleFilterSymbol,
                resaleGifts.patternAttributes.size(), resaleGifts.notSelectedPatternAttributes.size(),
                "Gift2ResaleFilterSymbols"));
    }

    private CharSequence filterLabel(int defaultRes, int total, int excluded, String pluralKey) {
        final int selected = total - excluded;
        return selected <= 0 || selected == total
                ? getString(defaultRes)
                : org.telegram.messenger.LocaleController.formatPluralStringComma(pluralKey, selected);
    }

    private void showSortingOptions() {
        if (resaleGifts == null) return;
        final ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, sortFilter);
        for (ResaleGiftsFragment.ResaleGiftsList.Sorting sorting :
                ResaleGiftsFragment.ResaleGiftsList.Sorting.values()) {
            final int icon = sorting == ResaleGiftsFragment.ResaleGiftsList.Sorting.BY_PRICE
                    ? R.drawable.menu_sort_value
                    : sorting == ResaleGiftsFragment.ResaleGiftsList.Sorting.BY_DATE
                    ? R.drawable.menu_sort_date : R.drawable.menu_sort_number;
            options.addChecked(resaleGifts.getSorting() == sorting, icon,
                    getString(sorting.buttonStringResId), () -> {
                        if (resaleGifts != null) {
                            resaleGifts.setSorting(sorting);
                            updateFilterLabels();
                        }
                    });
        }
        options.addGap();
        options.addChecked(!resaleGifts.isStarsOnly() && !resaleGifts.isGramsOnly(),
                getString(R.string.GiftResaleFilterAllListings), () -> {
                    if (resaleGifts != null) resaleGifts.setStarsOnly(false);
                });
        options.addChecked(resaleGifts.isStarsOnly(),
                getString(R.string.GiftResaleFilterForStarsOnly), () -> {
                    if (resaleGifts != null) resaleGifts.setStarsOnly(true);
                });
        options.addChecked(resaleGifts.isGramsOnly(),
                getString(R.string.GiftResaleFilterForGramsOnly), () -> {
                    if (resaleGifts != null) resaleGifts.setCurrencyFilter(false, true);
                });
        options.setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8)).show();
    }

    private void showModelOptions() {
        if (resaleGifts == null || resaleGifts.modelAttributes.isEmpty()) return;
        final ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, modelFilter, false, true)
                .setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8));
        final ArrayList<TL_stars.starGiftAttributeModel> attributes =
                new ArrayList<>(resaleGifts.modelAttributes);
        final String[] query = { "" };
        Collections.sort(attributes, (a, b) -> Integer.compare(
                resaleGifts.modelAttributesCounter.getOrDefault(b.document.id, 0),
                resaleGifts.modelAttributesCounter.getOrDefault(a.document.id, 0)));
        final UniversalRecyclerView list = new UniversalRecyclerView(getContext(), currentAccount, 0,
                (items, adapter) -> {
                    for (TL_stars.starGiftAttributeModel attribute : attributes) {
                        if (!matchesAttribute(attribute.name, query[0])) continue;
                        items.add(ResaleGiftsFragment.ModelItem.Factory.asModel(attribute,
                                resaleGifts.modelAttributesCounter.getOrDefault(attribute.document.id, 0), query[0])
                                .setChecked(!resaleGifts.notSelectedModelAttributes.contains(attribute.document.id)));
                    }
                }, (item, view, position, x, y) -> {
                    final TL_stars.starGiftAttributeModel attribute =
                            (TL_stars.starGiftAttributeModel) item.object;
                    toggleModel(attribute.document.id);
                    options.dismiss();
                }, null, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                        Math.min((int) (org.telegram.messenger.AndroidUtilities.displaySize.y * .35f),
                                View.MeasureSpec.getSize(heightMeasureSpec)),
                        View.MeasureSpec.getMode(heightMeasureSpec)));
            }
        };
        list.adapter.setApplyBackground(false);
        addAttributeSearch(options, list, query, attributes.size());
        if (!resaleGifts.notSelectedModelAttributes.isEmpty()) {
            options.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                resaleGifts.notSelectedModelAttributes.clear();
                resaleGifts.reload();
            }).addGap();
        }
        options.addView(list);
        options.setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8)).show();
    }

    private void toggleModel(long id) {
        if (resaleGifts == null) return;
        final HashSet<Long> excluded = resaleGifts.notSelectedModelAttributes;
        if (excluded.isEmpty()) {
            for (TL_stars.starGiftAttributeModel attribute : resaleGifts.modelAttributes) {
                if (attribute.document.id != id) excluded.add(attribute.document.id);
            }
        } else if (!excluded.add(id)) {
            excluded.remove(id);
        }
        resaleGifts.reload();
        updateFilterLabels();
    }

    private void showBackdropOptions() {
        if (resaleGifts == null || resaleGifts.backdropAttributes.isEmpty()) return;
        final ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, backdropFilter, false, true)
                .setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8));
        final ArrayList<TL_stars.starGiftAttributeBackdrop> attributes =
                new ArrayList<>(resaleGifts.backdropAttributes);
        final String[] query = { "" };
        Collections.sort(attributes, (a, b) -> Integer.compare(
                resaleGifts.backdropAttributesCounter.getOrDefault(b.backdrop_id, 0),
                resaleGifts.backdropAttributesCounter.getOrDefault(a.backdrop_id, 0)));
        final UniversalRecyclerView list = new UniversalRecyclerView(getContext(), currentAccount, 0,
                (items, adapter) -> {
                    for (TL_stars.starGiftAttributeBackdrop attribute : attributes) {
                        if (!matchesAttribute(attribute.name, query[0])) continue;
                        items.add(ResaleGiftsFragment.BackdropItem.Factory.asBackdrop(attribute,
                                resaleGifts.backdropAttributesCounter.getOrDefault(attribute.backdrop_id, 0), query[0])
                                .setChecked(!resaleGifts.notSelectedBackdropAttributes.contains(attribute.backdrop_id)));
                    }
                }, (item, view, position, x, y) -> {
                    final TL_stars.starGiftAttributeBackdrop attribute =
                            (TL_stars.starGiftAttributeBackdrop) item.object;
                    toggleBackdrop(attribute.backdrop_id);
                    options.dismiss();
                }, null, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                        Math.min((int) (org.telegram.messenger.AndroidUtilities.displaySize.y * .35f),
                                View.MeasureSpec.getSize(heightMeasureSpec)),
                        View.MeasureSpec.getMode(heightMeasureSpec)));
            }
        };
        list.adapter.setApplyBackground(false);
        addAttributeSearch(options, list, query, attributes.size());
        if (!resaleGifts.notSelectedBackdropAttributes.isEmpty()) {
            options.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                resaleGifts.notSelectedBackdropAttributes.clear();
                resaleGifts.reload();
            }).addGap();
        }
        options.addView(list);
        options.setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8)).show();
    }

    private void toggleBackdrop(int id) {
        if (resaleGifts == null) return;
        final HashSet<Integer> excluded = resaleGifts.notSelectedBackdropAttributes;
        if (excluded.isEmpty()) {
            for (TL_stars.starGiftAttributeBackdrop attribute : resaleGifts.backdropAttributes) {
                if (attribute.backdrop_id != id) excluded.add(attribute.backdrop_id);
            }
        } else if (!excluded.add(id)) {
            excluded.remove(id);
        }
        resaleGifts.reload();
        updateFilterLabels();
    }

    private void showPatternOptions() {
        if (resaleGifts == null || resaleGifts.patternAttributes.isEmpty()) return;
        final ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, patternFilter, false, true)
                .setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8));
        final ArrayList<TL_stars.starGiftAttributePattern> attributes =
                new ArrayList<>(resaleGifts.patternAttributes);
        final String[] query = { "" };
        Collections.sort(attributes, (a, b) -> Integer.compare(
                resaleGifts.patternAttributesCounter.getOrDefault(b.document.id, 0),
                resaleGifts.patternAttributesCounter.getOrDefault(a.document.id, 0)));
        final UniversalRecyclerView list = new UniversalRecyclerView(getContext(), currentAccount, 0,
                (items, adapter) -> {
                    for (TL_stars.starGiftAttributePattern attribute : attributes) {
                        if (!matchesAttribute(attribute.name, query[0])) continue;
                        items.add(ResaleGiftsFragment.PatternItem.Factory.asPattern(attribute,
                                resaleGifts.patternAttributesCounter.getOrDefault(attribute.document.id, 0), query[0])
                                .setChecked(!resaleGifts.notSelectedPatternAttributes.contains(attribute.document.id)));
                    }
                }, (item, view, position, x, y) -> {
                    final TL_stars.starGiftAttributePattern attribute =
                            (TL_stars.starGiftAttributePattern) item.object;
                    togglePattern(attribute.document.id);
                    options.dismiss();
                }, null, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                        Math.min((int) (org.telegram.messenger.AndroidUtilities.displaySize.y * .35f),
                                View.MeasureSpec.getSize(heightMeasureSpec)),
                        View.MeasureSpec.getMode(heightMeasureSpec)));
            }
        };
        list.adapter.setApplyBackground(false);
        addAttributeSearch(options, list, query, attributes.size());
        if (!resaleGifts.notSelectedPatternAttributes.isEmpty()) {
            options.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                resaleGifts.notSelectedPatternAttributes.clear();
                resaleGifts.reload();
            }).addGap();
        }
        options.addView(list);
        options.setDrawScrim(false).setOnTopOfScrim().translate(0, dp(-8)).show();
    }

    private static boolean matchesAttribute(String name, String query) {
        final String normalized = name.toLowerCase(Locale.ROOT);
        return query.isEmpty() || normalized.contains(query)
                || normalized.contains(org.telegram.messenger.AndroidUtilities.translitSafe(query));
    }

    private void addAttributeSearch(ItemOptions options, UniversalRecyclerView list,
                                    String[] query, int count) {
        if (count <= 8) return;
        options.needsFocus();
        final EditTextCaption search = new EditTextCaption(getContext(), resourcesProvider);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setHint(getString(R.string.Gift2ResaleFiltersSearch));
        search.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        search.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        search.setBackground(null);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                query[0] = s.toString().trim().toLowerCase(Locale.ROOT);
                list.adapter.update(false);
            }
        });
        options.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        options.addGap();
        options.setOnDismiss(() -> org.telegram.messenger.AndroidUtilities.hideKeyboard(search));
    }

    private void togglePattern(long id) {
        if (resaleGifts == null) return;
        final HashSet<Long> excluded = resaleGifts.notSelectedPatternAttributes;
        if (excluded.isEmpty()) {
            for (TL_stars.starGiftAttributePattern attribute : resaleGifts.patternAttributes) {
                if (attribute.document.id != id) excluded.add(attribute.document.id);
            }
        } else if (!excluded.add(id)) {
            excluded.remove(id);
        }
        resaleGifts.reload();
        updateFilterLabels();
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
        final UItem previewItem = UItem.ofFactory(PreviewCell.Factory.class).setId(PREVIEW).setSpanCount(3);
        previewItem.object = this;
        items.add(previewItem);
        items.add(UItem.asSpace(selectedCollection == null ? dp(44) : dp(91)).setSpanCount(3));
        // Keep the first row clear of the pinned filter bar and its rounded shadow.
        items.add(UItem.asSpace(dp(8)).setSpanCount(3));

        if (selectedCollection != null) {
            if (resaleGifts != null) {
                for (TL_stars.TL_starGiftUnique gift : resaleGifts.gifts) {
                    items.add(GiftSheet.GiftCell.Factory.asStarGift(getSelectedTab(), gift,
                            false, false, false, true, false, true));
                }
                if (resaleGifts.loading || !resaleGifts.endReached) {
                    for (int i = 0; i < 6; i++) {
                        items.add(UItem.asFlicker(200 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                    }
                }
            }
            items.add(UItem.asSpace(dp(24)));
            return;
        }

        items.add(UItem.ofFactory(DefaultSkinCell.Factory.class).setId(DEFAULT_SKIN)
                .setChecked(previewGift == null).setSpanCount(1));

        int uniqueCount = 0;
        for (TL_stars.SavedStarGift saved : gifts.gifts) {
            if (!(saved.gift instanceof TL_stars.TL_starGiftUnique)) continue;
            uniqueCount++;
            items.add(PeerColorActivity.GiftCell.Factory.asGiftCell(saved)
                    .setChecked(previewGift != null && saved.gift.id == previewGift.id).setSpanCount(1));
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
            resolveInitialGift();
            adapter.update(false);
        } else if (id == NotificationCenter.starGiftsLoaded && account == currentAccount && adapter != null) {
            refreshCollections();
            updatePinnedControls();
            adapter.update(false);
        }
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
        if (resaleGifts != null) resaleGifts.cancel();
        super.dismiss();
    }

    private static class PreviewCell extends FrameLayout {
        private WalletSkinsSheet sheet;
        PreviewCell(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            // The pinned card uses the container width, while this row is
            // measured inside the grid's horizontal padding.
            final int cardWidth = sheet != null && sheet.container.getMeasuredWidth() > 0
                    ? sheet.container.getMeasuredWidth() - dp(24) : width - dp(6);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    sheet != null && sheet.previewReservedHeight > 0
                            ? sheet.previewReservedHeight
                            : Math.round(cardWidth / 1.64f) + dp(16),
                    MeasureSpec.EXACTLY));
        }

        static class Factory extends UItem.UItemFactory<PreviewCell> {
            @Override
            public PreviewCell createView(Context context, RecyclerListView listView, int currentAccount,
                                          int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PreviewCell(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                 UniversalRecyclerView listView) {
                // The card is a pinned sibling of the RecyclerView; this item reserves its height.
                ((PreviewCell) view).sheet = (WalletSkinsSheet) item.object;
            }
        }
    }

    private static class ShopTabsCell extends GiftSheet.Tabs {
        ShopTabsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, false, resourcesProvider);
        }

        static class Factory extends UItem.UItemFactory<ShopTabsCell> {
            @Override
            public ShopTabsCell createView(Context context, RecyclerListView listView, int currentAccount,
                                           int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ShopTabsCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                 UniversalRecyclerView listView) {
                final WalletSkinsSheet sheet = (WalletSkinsSheet) item.object;
                ((ShopTabsCell) view).set(TABS, sheet.getTabs(), sheet.getSelectedTab(), sheet::selectTab);
            }
        }
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
