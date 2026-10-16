package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Stars.StarsController;

public class WalletCardComponent extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int CARD_NUMBER_FONT_WEIGHT = 700;
    private static final float CARD_NUMBER_LENGTH_SCALE = 1.18f;
    private static final int QR_BUTTON_RIGHT_PADDING = 45;
    private static Typeface cardNumberTypeface;
    private static Typeface cardHolderTypeface;

    private boolean diamondOnCard;
    private float balanceDrawScale = 1f;
    private final Matrix balanceDiamondTransform = new Matrix();
    private final Matrix inverseDiamondFaceTransform = new Matrix();
    private final Matrix nextDiamondTransform = new Matrix();
    private final RectF diamondDrawBounds = new RectF();
    private boolean hasBalanceDiamondTransform;
    private boolean use2D;
    private final boolean powerSaveMode;
    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private final Matrix faceProjectionMatrix = new Matrix();
    private final Matrix targetTransformMatrix = new Matrix();
    private final Matrix inverseTargetTransformMatrix = new Matrix();
    private View cardView;
    private FrameLayout frontFace;
    private Bitmap engravingBitmap;
    private TL_stars.TL_starGiftUnique skinGift;
    private FrameLayout skinLayer;
    private int skinTextColor = Color.WHITE;
    private int skinBackgroundColor;
    private OnClickListener cardClickListener;
    private final TextView cardHolderView;
    private final View balanceAnchor;
    private final LinearLayout balanceLayout;
    private final AnimatedTextView balanceView;
    private final AnimatedTextView usdBalanceView;
    private final FrameLayout.LayoutParams balanceCardLayoutParams;
    private String cardNumber;
    private int cardIconResId;
    private int qrButtonRightPadding;
    private float balanceTransitionProgress;
    private float balanceActionBarWidth;

    private int currentAccount = UserConfig.selectedAccount;
    private GramWallet wallet;

    public WalletCardComponent(Context context) {
        this(context, false, R.drawable.wallet_card_qr, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(Context context, boolean use2D) {
        this(context, use2D, R.drawable.wallet_card_qr, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(Context context, boolean use2D, int cardIconResId) {
        this(context, use2D, cardIconResId, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(
            Context context,
            boolean use2D,
            int cardIconResId,
            int qrButtonRightPadding
    ) {
        super(context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        powerSaveMode = powerManager != null && powerManager.isPowerSaveMode();
        this.use2D = use2D || powerSaveMode;
        this.cardIconResId = cardIconResId;
        this.qrButtonRightPadding = qrButtonRightPadding;
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        createCardImplementation();

        cardHolderView = new TextView(context);
        cardHolderView.setTypeface(getCardHolderTypeface(context));
        cardHolderView.setTextColor(Color.WHITE);
        cardHolderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.6f);
        cardHolderView.setShadowLayer(dp(1), dp(1), dp(1), 0xFF0285D4);
        cardHolderView.setEllipsize(TextUtils.TruncateAt.END);
        cardHolderView.setMaxLines(1);
        cardHolderView.setSingleLine();
        frontFace.addView(cardHolderView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.BOTTOM,
                24, 16.6f, 64, 16.6f
        ));

        balanceAnchor = new View(context);
        frontFace.addView(balanceAnchor, LayoutHelper.createFrame(
                1, 1, Gravity.LEFT | Gravity.CENTER_VERTICAL, 24, 0, 0, 0
        ));

        balanceLayout = new LinearLayout(context);
        balanceLayout.setOrientation(LinearLayout.VERTICAL);
        balanceLayout.setClipChildren(false);
        balanceLayout.setClipToPadding(false);
        balanceCardLayoutParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL,
                24, 0, 24, 0
        );
        frontFace.addView(balanceLayout, balanceCardLayoutParams);

        balanceView = new AnimatedTextView(context, false, true, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                // Fit the complete line, including its icon and unit, inside the
                // existing card/overlay projection without changing its geometry.
                final float textWidth = getDrawable().getCurrentWidth();
                final float qrLeft = frontFace.getWidth() * (336f - qrButtonRightPadding - 50f) / 336f;
                final float cardWidth = Math.max(0f, qrLeft - balanceCardLayoutParams.leftMargin - dp(8));
                final float cardScale = textWidth > 0 ? Math.min(1f, cardWidth / textWidth) : 1f;
                final float actionBarScale = textWidth > 0 ? Math.min(1f, balanceActionBarWidth / textWidth) : 1f;
                final float scale = cardScale + (actionBarScale - cardScale) * balanceTransitionProgress;
                balanceDrawScale = scale;
                final int save = canvas.save();
                canvas.scale(scale, scale, 0f, getHeight() / 2f);
                super.onDraw(canvas);
                canvas.restoreToCount(save);
            }
        };
        // Lay out the full balance before scaling; the normal view width would ellipsize it.
        balanceView.getDrawable().setOverrideFullWidth(dp(4096));
        balanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        balanceView.setScaleProperty(0.6f);
        balanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        balanceView.setTextSize(dp(24));
        balanceView.setTextColor(Color.WHITE);
        balanceView.setIgnoreRTL(true);
        balanceLayout.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        usdBalanceView = new AnimatedTextView(context, false, true, true, true, true);
        usdBalanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        usdBalanceView.setScaleProperty(0.6f);
        usdBalanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        usdBalanceView.setTextSize(dp(14));
        usdBalanceView.setTextColor(0xFF87EFFF);
        usdBalanceView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        usdBalanceView.setTextDirection(View.TEXT_DIRECTION_LTR);
        usdBalanceView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        usdBalanceView.setIgnoreRTL(true);
        balanceLayout.addView(usdBalanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 0, 0, 0));
    }

    /** Opt in only for the main wallet screen; other cards retain the text icon. */
    public void setDiamondOnCard(boolean enabled) {
        diamondOnCard = enabled;
        if (cardView instanceof WalletCardView) {
            final WalletCardView view = (WalletCardView) cardView;
            view.setDiamondAlpha(diamondOnCard ? 1f - balanceTransitionProgress : 0f);
            if (!diamondOnCard) view.setDiamondBounds(0, 0, 0, 0);
        }
        balanceView.invalidate();
    }

    public FrameLayout getFrontFace() {
        return frontFace;
    }

    public View getBalanceAnchor() {
        return balanceAnchor;
    }

    public LinearLayout getBalanceLayout() {
        return balanceLayout;
    }

    public AnimatedTextView getBalanceView() {
        return balanceView;
    }

    public void setBalanceTransition(float progress, float actionBarWidth) {
        if (balanceTransitionProgress == progress && balanceActionBarWidth == actionBarWidth) return;
        balanceTransitionProgress = progress;
        if (progress == 0f) hasBalanceDiamondTransform = false;
        balanceActionBarWidth = actionBarWidth;
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setDiamondAlpha(diamondOnCard ? 1f - progress : 0f);
        }
        balanceView.invalidate();
    }

    /** Maps the moving balance back onto the card face for the diamond crossfade. */
    public void setBalanceDiamondTransform(Matrix faceToOverlay, Matrix balanceToOverlay) {
        if (!faceToOverlay.invert(inverseDiamondFaceTransform)) return;
        nextDiamondTransform.setConcat(inverseDiamondFaceTransform, balanceToOverlay);
        if (!hasBalanceDiamondTransform || !balanceDiamondTransform.equals(nextDiamondTransform)) {
            balanceDiamondTransform.set(nextDiamondTransform);
            hasBalanceDiamondTransform = true;
            balanceView.invalidate();
        }
    }

    public AnimatedTextView getUsdBalanceView() {
        return usdBalanceView;
    }

    public TextView getCardHolderView() {
        return cardHolderView;
    }

    public void setCardHolder(CharSequence holder) {
        cardHolderView.setText(holder);
    }

    public void setCardNumber(String formattedNumber) {
        if (formattedNumber == null || formattedNumber.equals(cardNumber)) {
            return;
        }
        cardNumber = formattedNumber;
        setEngravingBitmap(createCardNumberEngraving(getContext(), formattedNumber));
    }

    /** Maps design-space face coordinates, including its canvas projection, into target. */
    public boolean getFrontFaceTransform(View target, Matrix result) {
        if (!isAttachedToWindow() || !target.isAttachedToWindow()
                || getRootView() != target.getRootView()) {
            return false;
        }
        final View projectedParent = (View) frontFace.getParent();
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).getFrontFaceProjection(faceProjectionMatrix);
        } else {
            ((WalletCardView) cardView).getFrontFaceProjection(faceProjectionMatrix);
        }
        transformToRoot(projectedParent, result);
        result.preConcat(faceProjectionMatrix);
        result.preTranslate(frontFace.getLeft() - projectedParent.getScrollX(),
                frontFace.getTop() - projectedParent.getScrollY());
        result.preConcat(frontFace.getMatrix());
        transformToRoot(target, targetTransformMatrix);
        if (!targetTransformMatrix.invert(inverseTargetTransformMatrix)) return false;
        result.postConcat(inverseTargetTransformMatrix);
        return true;
    }

    private static void transformToRoot(View view, Matrix result) {
        if (view.getParent() instanceof View) {
            final View parent = (View) view.getParent();
            transformToRoot(parent, result);
            result.preTranslate(view.getLeft() - parent.getScrollX(), view.getTop() - parent.getScrollY());
            result.preConcat(view.getMatrix());
        } else {
            result.reset();
        }
    }

    public void setOnFrontContentPresented(Runnable callback) {
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setOnFrontContentPresented(callback);
        } else if (callback != null) {
            callback.run();
        }
    }

    public void attachBalanceToCard() {
        setBalanceTransition(0f, 0f);
        AndroidUtilities.removeFromParent(balanceLayout);
        balanceLayout.setScaleX(1.0f);
        balanceLayout.setScaleY(1.0f);
        balanceLayout.setRotationX(0.0f);
        balanceLayout.setRotationY(0.0f);
        balanceLayout.setTranslationX(0.0f);
        balanceLayout.setTranslationY(0.0f);
        usdBalanceView.setScaleX(1.0f);
        usdBalanceView.setScaleY(1.0f);
        frontFace.addView(balanceLayout, balanceCardLayoutParams);
    }

    public boolean isUse2D() {
        return use2D;
    }

    public void setUse2D(boolean use2D) {
        use2D |= powerSaveMode;
        if (this.use2D == use2D) {
            return;
        }

        final int childCount = frontFace.getChildCount();
        final View[] children = new View[childCount];
        final ViewGroup.LayoutParams[] layoutParams = new ViewGroup.LayoutParams[childCount];
        for (int i = 0; i < childCount; i++) {
            children[i] = frontFace.getChildAt(i);
            layoutParams[i] = children[i].getLayoutParams();
        }
        frontFace.removeAllViews();
        removeView(cardView);

        this.use2D = use2D;
        createCardImplementation();
        for (int i = 0; i < childCount; i++) {
            frontFace.addView(children[i], layoutParams[i]);
        }
    }

    public void setCardOnClickListener(OnClickListener listener) {
        cardClickListener = listener;
        cardView.setOnClickListener(listener);
    }

    public void setCardIcon(int drawableResId) {
        cardIconResId = drawableResId;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setCardIcon(drawableResId);
        } else {
            ((WalletCardView) cardView).setCardIcon(drawableResId);
        }
    }

    public void setEngravingBitmap(Bitmap bitmap) {
        engravingBitmap = bitmap;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setEngravingBitmap(bitmap);
        } else {
            ((WalletCardView) cardView).setEngravingBitmap(bitmap);
        }
    }

    /** The profile gift supplies the card's backdrop, repeating symbol and model. */
    public void setSkinGift(TL_stars.TL_starGiftUnique gift) {
        if (skinGift == gift) return;
        if (skinLayer != null) {
            frontFace.removeView(skinLayer);
            skinLayer = null;
        }
        skinGift = gift;
        final TL_stars.starGiftAttributeBackdrop backdrop = gift == null ? null :
                StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
        skinBackgroundColor = gift == null ? 0 : SkinBackground.colorForBackdrop(backdrop);
        // Keep the primary card typography white across all gift colors.
        // Switching to black on light backdrops makes it look unlike the card.
        skinTextColor = Color.WHITE;
        cardHolderView.setTextColor(skinTextColor);
        balanceView.setTextColor(skinTextColor);
        usdBalanceView.setTextColor(gift == null ? 0xFF87EFFF :
                (skinTextColor & 0x00FFFFFF) | 0x80000000);
        cardHolderView.setShadowLayer(gift == null ? dp(1) : dp(.7f),
                gift == null ? dp(1) : 0, dp(1),
                gift == null ? 0xFF0285D4 : 0x66000000);
        balanceView.getDrawable().setShadowLayer(gift == null ? 0 : dp(.7f),
                0, dp(.5f), gift == null ? 0 : 0x44000000);
        usdBalanceView.getDrawable().setShadowLayer(0, 0, 0, 0);
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setSkinColor(skinBackgroundColor);
        } else if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setSkinColor(skinBackgroundColor);
        }

        if (gift != null) {
            skinLayer = new FrameLayout(getContext());
            skinLayer.setClipChildren(false);
            skinLayer.setBackground(new SkinBackground(skinLayer, currentAccount, backdrop,
                    StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class)));
            frontFace.addView(skinLayer, 0, LayoutHelper.createFrame(336, 205, Gravity.LEFT | Gravity.TOP));

            final FrameLayout modelTile = new FrameLayout(getContext());
            modelTile.setClipChildren(true);
            final GradientDrawable tileBackground = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[] { 0xFFF9FBFC, 0xFFADB8C0, 0xFFFFFFFF });
            tileBackground.setCornerRadius(dp(9));
            tileBackground.setStroke(dp(1), 0x88FFFFFF);
            modelTile.setBackground(tileBackground);
            modelTile.setClipToOutline(true);
            skinLayer.addView(modelTile, LayoutHelper.createFrame(50, 38,
                    Gravity.LEFT | Gravity.TOP, 336 - qrButtonRightPadding - 50, 73, 0, 0));
            final BackupImageView modelImage = new BackupImageView(getContext());
            modelImage.getImageReceiver().setAllowStartAnimation(false);
            modelTile.addView(modelImage, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            final TLRPC.Document document = gift.getDocument();
            if (document != null) {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
                modelImage.setImage(ImageLocation.getForDocument(document), "100_100",
                        ImageLocation.getForDocument(thumb, document), "100_100",
                        DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f), gift);
            }
        }
        setEngravingBitmap(engravingBitmap);
        if (wallet != null && wallet.isReady()) {
            setWalletBalance(wallet, wallet.getBalance(), false);
        }
    }

    public int getSkinTextColor() {
        return skinTextColor;
    }

    /** Adds a vertical tilt in degrees to the live sensor and drag rotation. */
    public void setAdditionalTilt(float tilt) {
        additionalTilt = tilt;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setAdditionalTilt(tilt);
        } else {
            ((WalletCardView) cardView).setAdditionalTilt(tilt);
        }
    }

    /** Scales sensor tilt: 0 disables it, 0.5 applies half, and 1 applies full tilt. */
    public void setUseGyroscope(float t) {
        gyroscopeTiltScale = t;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setUseGyroscope(t);
        } else {
            ((WalletCardView) cardView).setUseGyroscope(t);
        }
    }

    public float getCardRotationX() {
        if (cardView instanceof WalletCard2DView) {
            return ((WalletCard2DView) cardView).getCardRotationX();
        }
        return ((WalletCardView) cardView).getCardRotationX();
    }

    public float getCardRotationY() {
        if (cardView instanceof WalletCard2DView) {
            return ((WalletCard2DView) cardView).getCardRotationY();
        }
        return ((WalletCardView) cardView).getCardRotationY();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent != null) {
            parent.setTranslationZ(dp(16));
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).setClipChildren(false);
                ((ViewGroup) parent).setClipToPadding(false);
            }
        }
        if (wallet != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.walletUpdate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        final View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent != null) {
            parent.setTranslationZ(0.0f);
        }
        super.onDetachedFromWindow();
        if (wallet != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.walletUpdate);
        }
    }

    public void update(GramWallet currentWallet) {
        if (currentWallet == null) return;
        setCardHolder(UserObject.getUserName(
                UserConfig.getInstance(currentWallet.currentAccount).getCurrentUser()
        ).toUpperCase());
        if (currentWallet.getAddress() != null) {
            setCardNumber(formatAddress(currentWallet.getAddress().toUpperCase()));
        }
        if (currentWallet.isReady()) {
            setWalletBalance(currentWallet, currentWallet.getBalance(), true);
        }
    }

    public void listen(int currentAccount, GramWallet wallet) {
        if (isAttachedToWindow() && this.wallet != null && (this.currentAccount != currentAccount || this.wallet != wallet))
            NotificationCenter.getInstance(this.currentAccount).removeObserver(this, NotificationCenter.walletUpdate);
        this.currentAccount = currentAccount;
        this.wallet = wallet;
        if (isAttachedToWindow() && this.wallet != null)
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.walletUpdate);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletUpdate) {
            if (args[0] == wallet) {
                update(wallet);
            }
        }
    }

    static SpannableStringBuilder formatBalance(long nanotons) {
        final DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        format.setRoundingMode(RoundingMode.DOWN);
        final String gramsBalance = format.format(BigDecimal.valueOf(nanotons, 9));
        return GramWallet.formatGramsScaled(gramsBalance, 0.8f);
    }

    ColoredImageSpan createBalanceDiamondSpan() {
        return new ColoredImageSpan(R.drawable.wallet_gram_large) {
            @Override
            public void draw(Canvas canvas, CharSequence text, int start, int end,
                             float x, int top, int y, int bottom, Paint paint) {
                setAlpha(1f);
                if (diamondOnCard && cardView instanceof WalletCardView
                        && frontFace.getWidth() > 0) {
                    WalletCardView view = (WalletCardView) cardView;
                    // Start in the same local coordinates and scale as the text
                    // icon, then follow its interpolated path back in card space.
                    float width = drawable.getIntrinsicWidth() * balanceDrawScale;
                    float height = drawable.getIntrinsicHeight() * balanceDrawScale;
                    float left = x * balanceDrawScale;
                    float topOnBalance = balanceView.getHeight() / 2f - height / 2f;
                    diamondDrawBounds.set(left, topOnBalance, left + width, topOnBalance + height);
                    if (hasBalanceDiamondTransform && balanceTransitionProgress > 0f) {
                        balanceDiamondTransform.mapRect(diamondDrawBounds);
                    } else {
                        diamondDrawBounds.offset(balanceCardLayoutParams.leftMargin,
                                frontFace.getHeight() / 2f - dp(22));
                    }
                    view.setDiamondBounds(diamondDrawBounds.left / frontFace.getWidth(),
                            diamondDrawBounds.top / frontFace.getHeight(),
                            diamondDrawBounds.width() / frontFace.getWidth(),
                            diamondDrawBounds.height() / frontFace.getHeight());
                    if (view.isCardMaterialReady()) {
                        // Follow the whole balance transition in both directions.
                        float t = Math.max(0f, Math.min(1f, balanceTransitionProgress));
                        setAlpha(t);
                        if (t == 0f) return;
                    } else {
                        balanceView.postInvalidateOnAnimation();
                    }
                }
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
            }
        };
    }

    private void setWalletBalance(GramWallet wallet, long nanotons, boolean animate) {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        final SpannableString icon = new SpannableString("GRAM");
        icon.setSpan(new ColoredImageSpan(R.drawable.wallet_gram_large), 0, icon.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(icon).append(" ");
        text.append(formatBalance(nanotons));
        text.append(" ");
        final int labelStart = text.length();
        text.append(LocaleController.getString(R.string.GramCurrency));
        text.setSpan(new ForegroundColorSpan(skinGift == null ? 0xFF6DDCFF :
                (skinTextColor & 0x00FFFFFF) | 0x80000000), labelStart, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // A skin changes spans without changing the string; AnimatedTextView's
        // animated equality check would otherwise keep the previous styling.
        balanceView.setText(text, animate);

        final double grams = (double) nanotons / 1_000_000_000.0;
        final long usdCents = Math.round(grams
                * MessagesController.getInstance(wallet.currentAccount).config.tonUsdRate.get()
                * 100.0);
        usdBalanceView.setText(BillingController.getInstance().formatCurrency(
                usdCents, "USD", 2, usdCents % 100 == 0
        ), animate);
    }

    static String formatAddress(String address) {
        final StringBuilder formatted = new StringBuilder(address.length() + address.length() / 4);
        for (int i = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) formatted.append(' ');
            formatted.append(address.charAt(i));
        }
        return formatted.toString();
    }

    private void createCardImplementation() {
        if (use2D) {
            final WalletCard2DView view = new WalletCard2DView(
                    getContext(),
                    cardIconResId,
                    qrButtonRightPadding
            );
            cardView = view;
            frontFace = view.getFrontFace();
        } else {
            final WalletCardView view = new WalletCardView(
                    getContext(),
                    cardIconResId,
                    qrButtonRightPadding
            );
            cardView = view;
            frontFace = view.getFrontFace();
        }
        setAdditionalTilt(additionalTilt);
        setUseGyroscope(gyroscopeTiltScale);
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setDiamondAlpha(diamondOnCard ? 1f - balanceTransitionProgress : 0f);
            ((WalletCardView) cardView).setSkinColor(skinBackgroundColor);
        } else if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setSkinColor(skinBackgroundColor);
        }
        cardView.setOnClickListener(cardClickListener);
        addView(cardView, 0, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT
        ));
        if (engravingBitmap != null) {
            setEngravingBitmap(engravingBitmap);
        }
    }

    /** The gift's translucent printed symbols; the tinted metal remains visible underneath. */
    private static class SkinBackground extends Drawable {
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;
        private final boolean hasPattern;

        SkinBackground(View view, int account, TL_stars.starGiftAttributeBackdrop backdrop,
                       TL_stars.starGiftAttributePattern attribute) {
            hasPattern = attribute != null && attribute.document != null;
            pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(view, dp(26)) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    invalidateSelf();
                }
            };
            pattern.setCurrentAccount(account);
            if (hasPattern) {
                pattern.set(attribute.document, false);
                pattern.setColor(backdrop == null ? 0x66000000 : backdrop.pattern_color | 0xFF000000);
                pattern.setAlpha(89); // 65% transparent, including bright gift symbols.
            }
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    pattern.attach();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    pattern.detach();
                }
            });
            if (view.isAttachedToWindow()) pattern.attach();
        }

        static int colorForBackdrop(TL_stars.starGiftAttributeBackdrop backdrop) {
            if (backdrop == null) return 0xFF334155;
            return backdrop.center_color | 0xFF000000;
        }

        @Override
        public void draw(Canvas canvas) {
            rect.set(getBounds());
            if (!hasPattern) return;
            final int save = canvas.save();
            clip.rewind();
            clip.addRoundRect(rect, dp(25), dp(25), Path.Direction.CW);
            canvas.clipPath(clip);
            final float scaleX = rect.width() / 336f;
            final float scaleY = rect.height() / 205f;
            final float size = 26 * scaleX;
            for (int row = 0; row < 6; row++) {
                final float y = rect.top + (16 + row * 34) * scaleY;
                for (int column = 0; column < 5; column++) {
                    final float x = rect.left + (15 + column * 74 + (row % 2) * 37) * scaleX;
                    pattern.setBounds(Math.round(x - size / 2), Math.round(y - size / 2),
                            Math.round(x + size / 2), Math.round(y + size / 2));
                    pattern.draw(canvas);
                }
            }
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static Bitmap createCardNumberEngraving(Context context, String formattedAddress) {
        final int width = 1640;
        final int height = 1000;
        final float scale = width / 336.0f;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        canvas.translate(313.0f, 102.5f);
        canvas.rotate(90.0f);
        canvas.scale(CARD_NUMBER_LENGTH_SCALE, 1.0f);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(getCardNumberTypeface(context));
        paint.setTextSize(9.7f);
        paint.setTextAlign(Paint.Align.CENTER);
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float middleBaselineOffset = -(metrics.ascent + metrics.descent) * 0.5f;

        final String[] lines = formatCardAddress(formattedAddress).split("\\n");
        canvas.drawText(lines[0], 0, -5.6f + middleBaselineOffset, paint);
        if (lines.length > 1) {
            canvas.drawText(lines[1], 0, 5.6f + middleBaselineOffset, paint);
        }
        return bitmap;
    }

    static String formatCardAddress(String formattedAddress) {
        final String[] groups = formattedAddress.split(" ");
        if (groups.length < 2) {
            return formattedAddress;
        }
        final int lineBreak = groups.length / 2;
        final StringBuilder result = new StringBuilder(formattedAddress.length() + 1);
        for (int i = 0; i < groups.length; i++) {
            if (i > 0) {
                result.append(i == lineBreak ? '\n' : ' ');
            }
            result.append(groups[i]);
        }
        return result.toString();
    }

    private static Typeface getCardHolderTypeface(Context context) {
        if (cardHolderTypeface == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cardHolderTypeface = new Typeface.Builder(
                        context.getAssets(), "fonts/rmono_var.ttf"
                ).setFontVariationSettings("'wght' 500")
                        .setWeight(500)
                        .build();
            } else {
                cardHolderTypeface = AndroidUtilities.getTypeface("fonts/rmono.ttf");
            }
        }
        return cardHolderTypeface;
    }

    private static Typeface getCardNumberTypeface(Context context) {
        if (cardNumberTypeface == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cardNumberTypeface = new Typeface.Builder(
                        context.getAssets(), "fonts/rmono_var.ttf"
                ).setFontVariationSettings("'wght' " + CARD_NUMBER_FONT_WEIGHT)
                        .setWeight(CARD_NUMBER_FONT_WEIGHT)
                        .build();
            } else {
                cardNumberTypeface = Typeface.create(
                        AndroidUtilities.getTypeface("fonts/rmono_var.ttf"),
                        Typeface.BOLD
                );
            }
        }
        return cardNumberTypeface;
    }
}
