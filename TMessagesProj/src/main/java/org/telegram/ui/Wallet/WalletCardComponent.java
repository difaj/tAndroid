package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;

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
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Stars.StarsController;

public class WalletCardComponent extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int CARD_NUMBER_FONT_WEIGHT = 400;
    private static final int QR_BUTTON_RIGHT_PADDING = 46;
    private static final float QR_BUTTON_WIDTH = 48f;
    // Design-space tops of the balance and fiat lines; baselines match the design
    // (the balance font sits higher in its line than the design font).
    private static final float BALANCE_TOP = 71f;
    private static final float USD_BALANCE_TOP = 101f;
    private static Typeface cardNumberTypeface;
    private static Typeface cardHolderTypeface;

    private boolean diamondOnCard;
    private float balanceDrawScale = 1f;
    private final Matrix balanceDiamondTransform = new Matrix();
    private final Matrix inverseDiamondFaceTransform = new Matrix();
    private final Matrix nextDiamondTransform = new Matrix();
    private final RectF diamondDrawBounds = new RectF();
    private final RectF diamondCardBounds = new RectF();
    private float diamondStickerFallback;
    private boolean hasBalanceDiamondTransform;
    // Temporary material comparison: all wallet cards use the optimized Canvas renderer.


    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private final Matrix faceProjectionMatrix = new Matrix();
    private final Matrix targetTransformMatrix = new Matrix();
    private final Matrix inverseTargetTransformMatrix = new Matrix();
    private View cardView;
    private FrameLayout frontFace;
    private TL_stars.TL_starGiftUnique skinGift;
    private FrameLayout skinLayer;
    private FrameLayout stickerLayer;
    private final HashMap<Long, WalletStickerImage> stickerImages = new HashMap<>();
    private ArrayList<WalletCardStickers.Sticker> stickers = new ArrayList<>();
    private int skinTextColor = Color.WHITE;
    private OnClickListener cardClickListener;
    private final TextView cardHolderView;
    private final View cardNumberView;
    private final Paint cardNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private float numberStickerShadow;
    private final org.telegram.ui.Components.AnimatedFloat numberShadowAnimated = new org.telegram.ui.Components.AnimatedFloat(
            this::onNumberShadowFrame, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
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
        this.cardIconResId = cardIconResId;
        this.qrButtonRightPadding = qrButtonRightPadding;
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        createCardImplementation();

        cardHolderView = new TextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawStickerShadowed(holderShadowAnimated.set(holderStickerShadow), getPaint()::setShadowLayer, () -> super.onDraw(canvas));
            }
        };
        cardHolderView.setTypeface(getCardHolderTypeface(context));
        cardHolderView.setTextColor(Color.WHITE);
        cardHolderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        cardHolderView.setEllipsize(TextUtils.TruncateAt.END);
        cardHolderView.setMaxLines(1);
        cardHolderView.setSingleLine();
        frontFace.addView(cardHolderView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.BOTTOM,
                24, 0, 64, 17.2f
        ));

        // Card number above the stickers; shadowed while a sticker slides under it.
        cardNumberView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                final float t = numberShadowAnimated.set(numberStickerShadow);
                if (cardNumber == null || getWidth() <= 0) return;
                drawCardNumber(canvas, t);
            }
        };
        frontFace.addView(cardNumberView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
                Gravity.LEFT | Gravity.TOP,
                24, BALANCE_TOP, 24, 0
        );
        frontFace.addView(balanceLayout, balanceCardLayoutParams);

        balanceView = new AnimatedTextView(context, false, true, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                // Fit the complete line, including its icon and unit, inside the
                // existing card/overlay projection without changing its geometry.
                final float textWidth = getDrawable().getCurrentWidth();
                final float cardScale = balanceCardScale();
                final float actionBarScale = textWidth > 0 ? Math.min(1f, balanceActionBarWidth / textWidth) : 1f;
                final float scale = cardScale + (actionBarScale - cardScale) * balanceTransitionProgress;
                balanceDrawScale = scale;
                final int save = canvas.save();
                canvas.scale(scale, scale, 0f, getHeight() / 2f);
                drawStickerShadowed(balanceShadowAnimated.set(balanceStickerShadow) * (1f - balanceTransitionProgress),
                        getDrawable()::setShadowLayer, () -> super.onDraw(canvas));
                canvas.restoreToCount(save);
            }
        };
        // Lay out the full balance before scaling; the normal view width would ellipsize it.
        balanceView.getDrawable().setOverrideFullWidth(dp(4096));
        balanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        balanceView.setScaleProperty(0.6f);
        balanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        balanceView.setTextSize(dp(23));
        balanceView.setTextColor(Color.WHITE);
        balanceView.setIgnoreRTL(true);
        balanceLayout.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        usdBalanceView = new AnimatedTextView(context, false, true, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawStickerShadowed(balanceShadowAnimated.set(balanceStickerShadow) * (1f - balanceTransitionProgress),
                        getDrawable()::setShadowLayer, () -> super.onDraw(canvas));
            }
        };
        usdBalanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        usdBalanceView.setScaleProperty(0.6f);
        usdBalanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        usdBalanceView.setTextSize(dp(14));
        usdBalanceView.setTextColor(0xFF87EFFF);
        usdBalanceView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        usdBalanceView.setTextDirection(View.TEXT_DIRECTION_LTR);
        usdBalanceView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        usdBalanceView.setIgnoreRTL(true);
        balanceLayout.addView(usdBalanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, USD_BALANCE_TOP - BALANCE_TOP - 28, 0, 0));
    }

    /** Opt in only for the main wallet screen; other cards retain the text icon. */
    public void setDiamondOnCard(boolean enabled) {
        diamondOnCard = enabled;

        balanceView.invalidate();
    }

    private float[] stickerStyle;
    private float[] skinStyle;
    private float[] appliedStickerStyle;

    float[] getStickerStyle() {
        return stickerStyle;
    }

    int stickerColor(int color) {
        return stickerStyle == null ? color : WalletGiftPalette.tint(color, stickerStyle);
    }

    private float balanceCardScale() {
        final float textWidth = balanceView.getDrawable().getCurrentWidth();
        final float qrLeft = frontFace.getWidth() * (336f - qrButtonRightPadding - QR_BUTTON_WIDTH) / 336f;
        final float cardWidth = Math.max(0f, qrLeft - balanceCardLayoutParams.leftMargin - dp(8));
        return textWidth > 0 ? Math.min(1f, cardWidth / textWidth) : 1f;
    }

    java.util.ArrayList<RectF> getStickerObstacles() {
        java.util.ArrayList<RectF> bounds = new java.util.ArrayList<>();
        float k = frontFace.getWidth()/336f;
        // Measure the visible glyphs, not the match-parent text containers.
        // Use the balance's resting place on the card: while scrolling it moves into an overlay and rescales.
        float cardScale = balanceCardScale();
        float balanceWidth = balanceView.getDrawable().getCurrentWidth()*cardScale;
        float usdWidth = usdBalanceView.getDrawable().getCurrentWidth();
        float left = balanceCardLayoutParams.leftMargin, top = balanceCardLayoutParams.topMargin;
        float balanceCenter = top+(balanceView.getTop()+balanceView.getBottom())/2f, balanceHalf = balanceView.getHeight()*cardScale/2f;
        bounds.add(new RectF(left, balanceCenter-balanceHalf, left+balanceWidth, balanceCenter+balanceHalf));
        float ownerWidth = Math.min(cardHolderView.getWidth(), cardHolderView.getPaint().measureText(cardHolderView.getText().toString()));
        bounds.add(new RectF(cardHolderView.getLeft(), cardHolderView.getTop(),
                cardHolderView.getLeft()+ownerWidth, cardHolderView.getBottom()));
        bounds.add(new RectF(frontFace.getWidth()-34*k,18*k,frontFace.getWidth()-12*k,187*k));
        android.graphics.Path qr = new android.graphics.Path();
        getQrButtonPath(qr,frontFace.getWidth(),frontFace.getHeight());
        RectF qrBounds = new RectF();qr.computeBounds(qrBounds,true);bounds.add(qrBounds);
        // Separate from the balance so a narrower currency line leaves the space beside it free.
        bounds.add(new RectF(left, top+usdBalanceView.getTop(), left+usdWidth, top+usdBalanceView.getBottom()));
        bounds.add(diamondOnCard ? new RectF(diamondCardBounds) : new RectF());
        return bounds;
    }

    void getQrButtonPath(android.graphics.Path path, float width, float height) {
        path.rewind();
        float top = 74 / 205f, bottom = 110 / 205f, ry = 8 / 205f;
        if (!isUse2D()) {
            // QR material UVs use a symmetric 1.219512 model height; the UI face
            // uses the mesh's asymmetric bounds. Convert before cutting the mask.
            float span = WalletCardGeometry.TOP - WalletCardGeometry.BOTTOM;
            top = (WalletCardGeometry.TOP - (.5f-top)*1.219512f)/span;
            bottom = (WalletCardGeometry.TOP - (.5f-bottom)*1.219512f)/span;
            ry *= 1.219512f/span;
        }
        path.addRoundRect((336 - qrButtonRightPadding - QR_BUTTON_WIDTH) * width / 336f, top * height,
                (336 - qrButtonRightPadding) * width / 336f, bottom * height,
                8 * width / 336f, ry * height, android.graphics.Path.Direction.CW);
    }

    void setStickerImpact(float scale) {
        cardView.setScaleX(scale);
        cardView.setScaleY(scale);
    }

    private int kickSignX, kickSignY;

    /** Tilts the card by a sticker's impact: h/v are -1..1 with positive pushing the right/bottom edge away. */
    void setStickerKick(float h, float v) {
        if (kickSignX == 0) {
            // Find which rotation sign pushes an edge away, from the projection itself.
            WalletCardProjection projection = new WalletCardProjection();
            float[] c = new float[8];
            projection.project(1000, 600, 0, 5, false, c);
            kickSignY = Math.hypot(c[2]-c[4], c[3]-c[5]) < Math.hypot(c[0]-c[6], c[1]-c[7]) ? 1 : -1;
            projection.project(1000, 600, 5, 0, false, c);
            kickSignX = Math.hypot(c[4]-c[6], c[5]-c[7]) < Math.hypot(c[0]-c[2], c[1]-c[3]) ? 1 : -1;
        }
        final float x = kickSignX * v * 10f, y = kickSignY * h * 10f;
        ((WalletCard2DView) cardView).setStickerKick(x, y);
    }

    /** Stickers go below the balance and holder so these stay readable on top. */
    int getStickerLayerIndex() {
        int holder = frontFace.indexOfChild(cardHolderView), balance = frontFace.indexOfChild(balanceLayout);
        if (holder < 0) return balance < 0 ? frontFace.getChildCount() : balance;
        return balance < 0 ? holder : Math.min(holder, balance);
    }

    /** Targets, 0..1 per text: how much a sticker under it should darken its shadow. */
    private float balanceStickerShadow, holderStickerShadow;
    private final org.telegram.ui.Components.AnimatedFloat balanceShadowAnimated = new org.telegram.ui.Components.AnimatedFloat(
            this::onBalanceShadowFrame, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final org.telegram.ui.Components.AnimatedFloat holderShadowAnimated = new org.telegram.ui.Components.AnimatedFloat(
            this::onHolderShadowFrame, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
    // The flat diamond takes over whenever the balance is shadowed, so it can carry the shadow too.
    private final org.telegram.ui.Components.AnimatedFloat diamondFallbackAnimated = new org.telegram.ui.Components.AnimatedFloat(
            this::onDiamondFallbackFrame, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
    private float diamondFallback;

    private WalletDiamondSprite diamondSprite;

    private void updateDiamondSpriteTint() {
        if (diamondSprite == null) return;
        final float[] material = WalletGiftPalette.material(stickerStyle == null ? new float[]{0, 1, 1, 0, 1, 1, .5f, .5f, 1, 1} : stickerStyle);
        final float mix = stickerStyle != null && stickerStyle.length > 8 ? Math.max(0f, Math.min(1f, stickerStyle[8])) : 1f;
        diamondSprite.setTint(AndroidUtilities.lerp(material[30], material[33], mix),
                AndroidUtilities.lerp(material[31], material[34], mix),
                AndroidUtilities.lerp(material[32], material[35], mix));
    }

    private void onBalanceShadowFrame() {
        balanceView.invalidate();
        usdBalanceView.invalidate();
    }

    private void onHolderShadowFrame() {
        cardHolderView.invalidate();
    }

    private void onDiamondFallbackFrame() {
        balanceView.invalidate();
    }

    private void onNumberShadowFrame() {
        cardNumberView.invalidate();
    }

    /** Card number laid out in a 1640x1000 design space mapped onto the face. */
    private void drawCardNumber(Canvas canvas, float t) {
        final float w = getFrontFace().getWidth(), h = getFrontFace().getHeight();
        final float scale = 1640 / 336f, px = w / 1640f;
        float cy = 102.5f * scale / 1000f;
        if (!isUse2D()) {
            // The engraving texture spans the mesh's symmetric 1.219512 height, like the QR button.
            final float span = WalletCardGeometry.TOP - WalletCardGeometry.BOTTOM;
            cy = (WalletCardGeometry.TOP - (.5f - cy) * 1.219512f) / span;
        }
        cardNumberPaint.setTypeface(getCardNumberTypeface(getContext()));
        cardNumberPaint.setTextSize(9.7f * scale * px);
        cardNumberPaint.setTextAlign(Paint.Align.CENTER);
        // Same ink as the 2D and 3D engraving.
        cardNumberPaint.setColor(isUse2D() ? WalletGiftPalette.cardNumber2DColor(stickerStyle)
                : WalletGiftPalette.cardNumberColor(stickerStyle));
        final Paint.FontMetrics metrics = cardNumberPaint.getFontMetrics();
        final float baseline = -(metrics.ascent + metrics.descent) * .5f, gap = 5.6f * scale * px;
        final String[] lines = formatCardAddress(cardNumber).split("\\n");
        canvas.save();
        canvas.translate(313f * scale * px, cy * h);
        canvas.rotate(90f);
        // Rotated 90°: local (a, b) lands at screen (-b, a), so swap offsets to keep the shadow pointing down.
        // Both card-number shadow passes use twice the blur and half the opacity.
        drawStickerShadowed(t, (r, dx, dy, color) -> cardNumberPaint.setShadowLayer(
                r * 2f, dy, -dx,
                androidx.core.graphics.ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * .5f))), () -> {
            canvas.drawText(lines[0], 0, -gap + baseline, cardNumberPaint);
            if (lines.length > 1) canvas.drawText(lines[1], 0, gap + baseline, cardNumberPaint);
        });
        canvas.restore();
    }

    void setStickerTextShadow(float balance, float holder, float diamond, float number) {
        number = Math.round(number * 20) / 20f;
        if (number != numberStickerShadow) {
            numberStickerShadow = number;
            cardNumberView.invalidate();
        }
        // The diamond is either 3D or flat; a partial mix of the two looks broken, so only the swap is animated.
        diamond = Math.max(diamond, balance) > 0f ? 1f : 0f;
        if (diamond != diamondStickerFallback) {
            diamondStickerFallback = diamond;
            balanceView.invalidate();
        }
        balance = Math.round(balance * 20) / 20f;
        holder = Math.round(holder * 20) / 20f;
        if (balance != balanceStickerShadow) {
            balanceStickerShadow = balance;
            balanceView.invalidate();
            usdBalanceView.invalidate();
        }
        if (holder != holderStickerShadow) {
            holderStickerShadow = holder;
            cardHolderView.invalidate();
        }
    }

    /** Set while text is redrawn for its outline shadow; images in it are already drawn. */
    private boolean outlineShadowPass;

    private interface ShadowLayer {
        void set(float radius, float dx, float dy, int color);
    }

    /** Draws text with a soft drop shadow, then again with a tight centered outline shadow as t (sticker under it) grows. */
    private void drawStickerShadowed(float t, ShadowLayer shadow, Runnable draw) {
        shadow.set(t > 0 ? dp(5) : 0, 0, dp(1), Color.argb((int) (89 * t), 0, 0, 0));
        draw.run();
        if (t <= 0) return;
        shadow.set(AndroidUtilities.dpf2(1.5f), 0, 0, Color.argb((int) (140 * t), 0, 0, 0));
        outlineShadowPass = true;
        draw.run();
        outlineShadowPass = false;
    }

    void setStickerStyle(float[] style) {
        appliedStickerStyle = style;
        if (skinStyle != null && (style == null || (style.length >= 18 && style[10] < 0 && style[14] < 0))) {
            style = skinStyle;
        }
        stickerStyle = style;
        updateDiamondSpriteTint();
        balanceView.invalidate();
        // Refresh the number's recorded ink color even when sticker coverage is unchanged.
        cardNumberView.invalidate();
        ((WalletCard2DView) cardView).setStickerStyle(style);
        usdBalanceView.setTextColor(style == null ? 0xFF87EFFF : WalletGiftPalette.tint(0xFF87EFFF, style));
        cardHolderView.invalidate();
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

        balanceView.invalidate();
        usdBalanceView.invalidate();
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

    boolean isBackFacing() {
        return cardView instanceof WalletCard2DView && ((WalletCard2DView) cardView).isBackFacing();
    }

    void whenMaterialReady(Runnable action) {
        if (cardView instanceof WalletCard2DView) ((WalletCard2DView) cardView).whenMaterialReady(action);
        else action.run();
    }

    public void setCardHolder(CharSequence holder) {
        cardHolderView.setText(holder);
    }

    public void setCardNumber(String formattedNumber) {
        if (formattedNumber == null || formattedNumber.equals(cardNumber)) {
            return;
        }
        cardNumber = formattedNumber;
        ((WalletCard2DView) cardView).setHasCardNumber(true);
        cardNumberView.invalidate();
    }

    /** Maps design-space face coordinates, including its canvas projection, into target. */
    public boolean getFrontFaceTransform(View target, Matrix result) {
        if (!isAttachedToWindow() || !target.isAttachedToWindow()
                || getRootView() != target.getRootView()) {
            return false;
        }
        final View faceParent = (View) frontFace.getParent();
        final View projectedParent = (View) faceParent.getParent();

            ((WalletCard2DView) cardView).getFrontFaceProjection(faceProjectionMatrix);

        transformToRoot(projectedParent, result);
        result.preConcat(faceProjectionMatrix);
        result.preTranslate(frontFace.getLeft() - faceParent.getScrollX(),
                frontFace.getTop() - faceParent.getScrollY());
        result.preConcat(frontFace.getMatrix());
        transformToRoot(target, targetTransformMatrix);
        if (!targetTransformMatrix.invert(inverseTargetTransformMatrix)) return false;
        result.postConcat(inverseTargetTransformMatrix);
        return true;
    }

    static void transformToRoot(View view, Matrix result) {
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
        if (callback != null) {
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
        return true;
    }

    public void setCardOnClickListener(OnClickListener listener) {
        cardClickListener = listener;
        cardView.setOnClickListener(listener);
    }

    public void setCardIcon(int drawableResId) {
        cardIconResId = drawableResId;

            ((WalletCard2DView) cardView).setCardIcon(drawableResId);

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
        skinStyle = null;
        if (gift != null) {
            final float[] tone = WalletGiftPalette.of(backdrop);
            skinStyle = new float[] {tone[0], tone[1], tone[2], tone[0], tone[1], tone[2], .5f, .5f, 1f, 1f,
                    -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f};
            WalletGiftPalette.putBackdrop(skinStyle, 0, backdrop);
            WalletGiftPalette.putBackdrop(skinStyle, 1, backdrop);
        }
        setStickerStyle(appliedStickerStyle);

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
        if (gift != null) {
            for (WalletCardStickers.Sticker sticker : stickers) {
                if (sticker.gift.id == gift.id) {
                    setStickers(stickers);
                    break;
                }
            }
        }
        if (wallet != null && wallet.isReady()) {
            setWalletBalance(wallet, wallet.getBalance(), false);
        }
    }

    public int getSkinTextColor() {
        return skinTextColor;
    }

    public void reloadStickers() {
        setStickers(WalletCardStickers.load(currentAccount));
    }

    public void reloadStickers(int account) {
        currentAccount = account;
        reloadStickers();
    }

    View getStickerImage(int index) {
        return index >= 0 && index < stickers.size()
                ? stickerImages.get(stickers.get(index).gift.id) : null;
    }

    void setStickers(ArrayList<WalletCardStickers.Sticker> value) {
        final ArrayList<WalletCardStickers.Sticker> filtered = new ArrayList<>();
        for (WalletCardStickers.Sticker sticker : value) {
            if (skinGift == null || sticker.gift.id != skinGift.id) filtered.add(sticker);
        }
        stickers = filtered;
        if (stickerLayer == null) {
            stickerLayer = new FrameLayout(getContext());
            stickerLayer.setClipChildren(false);
            // Pattern first, decorations next, typography last.
            frontFace.addView(stickerLayer, skinLayer == null ? 0 : 1,
                    LayoutHelper.createFrame(336, 205, Gravity.TOP | Gravity.LEFT));
        }
        final HashMap<Long, WalletStickerImage> unused = new HashMap<>(stickerImages);
        for (WalletCardStickers.Sticker sticker : stickers) {
            final TLRPC.Document document = sticker.gift.getDocument();
            if (document == null) continue;
            WalletStickerImage image = stickerImages.get(sticker.gift.id);
            unused.remove(sticker.gift.id);
            if (image == null) {
                image = new WalletStickerImage(getContext(), document.id);
                image.getImageReceiver().setAllowStartAnimation(false);
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
                image.setImage(ImageLocation.getForDocument(document), "80_80_nolimit_pcache",
                        ImageLocation.getForDocument(thumb, document), "80_80_nolimit_pcache",
                        DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, .3f), sticker.gift);
                stickerLayer.addView(image, LayoutHelper.createFrame(80, 80, Gravity.TOP | Gravity.LEFT));
                stickerImages.put(sticker.gift.id, image);
            }
            applyStickerTransform(image, sticker);
        }
        for (Long id : unused.keySet()) {
            stickerLayer.removeView(stickerImages.remove(id));
        }
    }

    void updateStickerTransform(int index, WalletCardStickers.Sticker sticker) {
        final View image = stickerImages.get(sticker.gift.id);
        if (image == null) return;
        applyStickerTransform(image, sticker);
        if (index < stickers.size()) stickers.set(index, sticker);
    }

    private void applyStickerTransform(View image, WalletCardStickers.Sticker sticker) {
        image.setTranslationX(dp(sticker.x - 40));
        image.setTranslationY(dp(sticker.y - 40));
        image.setScaleX(sticker.size / 80f);
        image.setScaleY(sticker.size / 80f);
        image.setRotation(sticker.rotation);
    }

    /** Text is above the sticker layer; only the card edges constrain placement. */
    boolean canPlaceSticker(WalletCardStickers.Sticker sticker) {
        final double angle = Math.toRadians(sticker.rotation);
        final float half = sticker.size * (float) (Math.abs(Math.cos(angle)) + Math.abs(Math.sin(angle))) / 2;
        final RectF bounds = new RectF(sticker.x - half, sticker.y - half, sticker.x + half, sticker.y + half);
        return bounds.left >= 8 && bounds.top >= 8 && bounds.right <= 328 && bounds.bottom <= 197;
    }

    /** Adds a vertical tilt in degrees to the live sensor and drag rotation. */
    public void setAdditionalTilt(float tilt) {
        additionalTilt = tilt;

            ((WalletCard2DView) cardView).setAdditionalTilt(tilt);

    }

    /** Scales sensor tilt: 0 disables it, 0.5 applies half, and 1 applies full tilt. */
    public void setUseGyroscope(float t) {
        gyroscopeTiltScale = t;

            ((WalletCard2DView) cardView).setUseGyroscope(t);

    }

    public float getCardRotationX() {

            return ((WalletCard2DView) cardView).getCardRotationX();
    }

    public float getCardRotationY() {

            return ((WalletCard2DView) cardView).getCardRotationY();
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
        reloadStickers();
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
        final DecimalFormat format = new DecimalFormat("#,##0.#########", new DecimalFormatSymbols(Locale.US));
        // Share the wallet's rounding, then add grouping without rounding a second time.
        final String gramsBalance = format.format(new BigDecimal(GramWallet.formatGramsBalance(nanotons, false)));
        return GramWallet.formatGramsScaled(gramsBalance, 0.8f);
    }

    ColoredImageSpan createBalanceDiamondSpan() {
        return new ColoredImageSpan(R.drawable.wallet_gram_large) {
            @Override
            public void draw(Canvas canvas, CharSequence text, int start, int end,
                             float x, int top, int y, int bottom, Paint paint) {
                if (outlineShadowPass) return;
                setAlpha(1f);
                if (diamondOnCard && isUse2D()) {
                    // Use the same faceted artwork as the GL card's flat fallback.
                    // Keep the existing crossfade to the toolbar's currency symbol.
                    float t = Math.max(0f, Math.min(1f, balanceTransitionProgress));
                    if (t == 0f) {
                        float width = drawable.getIntrinsicWidth() * balanceDrawScale;
                        float height = drawable.getIntrinsicHeight() * balanceDrawScale;
                        float left = x * balanceDrawScale + balanceCardLayoutParams.leftMargin;
                        float topOnCard = balanceView.getHeight() / 2f - height / 2f + balanceCardLayoutParams.topMargin;
                        diamondCardBounds.set(left, topOnCard, left + width, topOnCard + height);
                    }
                    if (t < 1f) {
                        if (diamondSprite == null) {
                            diamondSprite = new WalletDiamondSprite(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                            updateDiamondSpriteTint();
                        }
                        final android.graphics.drawable.Drawable icon = drawable;
                        diamondSprite.setBounds(icon.getBounds());
                        diamondSprite.setShadowAlpha(balanceShadowAnimated.get());
                        drawable = diamondSprite;
                        recolorDrawable = false;
                        setAlpha(1f - t);
                        super.draw(canvas, text, start, end, x, top, y, bottom, paint);
                        drawable = icon;
                        recolorDrawable = true;
                    }
                    setAlpha(t);
                    if (t == 0f) return;
                }

                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
            }
        };
    }

    public void setProfileCard(int account, TLRPC.User user, TL_wallet.card profileCard) {
        setCardHolder(UserObject.getUserName(user).toUpperCase(Locale.ROOT));
        if (!TextUtils.isEmpty(profileCard.address)) {
            setCardNumber(formatAddress(profileCard.address.toUpperCase(Locale.ROOT)));
        }
        setCardBalance(account, profileCard.balance, false);
        final CharSequence currency = GramWallet.getInstance(account).formatCurrency(profileCard.balance);
        if (!TextUtils.isEmpty(currency)) {
            usdBalanceView.setText(currency, false);
        }
    }

    private void setWalletBalance(GramWallet wallet, long nanotons, boolean animate) {
        setCardBalance(wallet.currentAccount, nanotons, animate);
    }

    private void setCardBalance(int account, long nanotons, boolean animate) {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        final SpannableString icon = new SpannableString("GRAM");
        icon.setSpan(createBalanceDiamondSpan(), 0, icon.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(icon).append(" ");
        text.append(formatBalance(nanotons));
        text.append(" ");
        final int labelStart = text.length();
        text.append(LocaleController.getString(R.string.GramCurrency));
        text.setSpan(new android.text.style.CharacterStyle() {
            @Override public void updateDrawState(android.text.TextPaint paint) {
                paint.setColor(skinGift != null ? 0x80FFFFFF : stickerColor(0xFF6DDCFF));
            }
        }, labelStart, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // A skin changes spans without changing the string; AnimatedTextView's
        // animated equality check would otherwise keep the previous styling.
        balanceView.setText(text, animate);

        final double grams = (double) nanotons / 1_000_000_000.0;
        final long usdCents = Math.round(grams
                * MessagesController.getInstance(account).config.tonUsdRate.get()
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

            final WalletCard2DView view = new WalletCard2DView(
                    getContext(),
                    cardIconResId,
                    qrButtonRightPadding
            );
            cardView = view;
            frontFace = view.getFrontFace();

        if (stickerStyle != null) setStickerStyle(stickerStyle);
        setAdditionalTilt(additionalTilt);
        setUseGyroscope(gyroscopeTiltScale);

        cardView.setOnClickListener(cardClickListener);
        addView(cardView, 0, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT
        ));
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
                cardNumberTypeface = AndroidUtilities.getTypeface("fonts/rmono.ttf");
            }
        }
        return cardNumberTypeface;
    }
}
