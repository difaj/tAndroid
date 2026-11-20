package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.SweepGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    public void setActive(boolean f) {
        this.active = f;
        if (!f) cancelGesture();
        updateAnimationState();
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
        foregroundView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawOrbitLayer(canvas, true);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return handleTouch(event, true);
            }
        };
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private final View foregroundView;
    private final int touchSlop;
    private boolean hostResumed = true;
    private boolean attached;
    private boolean frameScheduled;
    private boolean observingAudio;
    private long lastFrameNanos;
    private float motionTime;
    private float spinVelocity;
    private boolean physicsSleeping;
    private float stationaryTime;
    private float orbitCenterX, orbitCenterY, orbitRadiusX, orbitRadiusY;
    private float musicLevel, musicPresence, musicFlash, previousMusicTarget;
    private float musicBass, musicHop, musicHopVelocity;
    private Runnable musicFrameCallback;
    private float notifiedMusicDim;
    private float photoMotionX, photoMotionY, photoMotionScale = 1f;
    private long musicDocumentId;
    private Gift[] drawOrder = new Gift[0];

    public View getForegroundView() {
        return foregroundView;
    }

    public void setMusicFrameCallback(Runnable callback) {
        musicFrameCallback = callback;
    }

    public float getMusicDimming() {
        return musicPresence * .18f * collapseProgress * (1f - expandProgress) * getAlpha();
    }

    public void setHostResumed(boolean resumed) {
        hostResumed = resumed;
        physicsSleeping = false;
        stationaryTime = 0;
        if (!resumed) {
            cancelGesture();
            spinVelocity = 0;
            resetPhotoMotion();
        }
        updateAnimationState();
    }

    public void setMusicDocumentId(long documentId) {
        if (musicDocumentId == documentId) return;
        musicDocumentId = documentId;
        musicLevel = musicBass = musicHop = musicHopVelocity = musicFlash = previousMusicTarget = 0;
        updateAnimationState();
        invalidateLayers();
    }

    private boolean canAnimate() {
        return active && hostResumed && attached && isShown() && getWindowVisibility() == VISIBLE
                && (!gifts.isEmpty() || musicPresence > .001f || MediaController.getInstance()
                        .isProfileMusicPlaying(currentAccount, dialogId, musicDocumentId))
                && expandProgress < 1f && collapseProgress > 0f && actionBarProgress < 1f
                && (LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND) || hasInteraction());
    }

    private boolean hasInteraction() {
        if (pressedGift != null || avatarSpinCandidate || Math.abs(spinVelocity) > .01f) return true;
        for (Gift gift : gifts) {
            if (Math.abs(gift.extraSpeed) > .01f) return true;
            if (!physicsSleeping && (gift.offsetX != 0 || gift.offsetY != 0 || gift.vx != 0 || gift.vy != 0)) return true;
        }
        return false;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (foregroundView != null) foregroundView.invalidate();
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (foregroundView != null) foregroundView.setAlpha(alpha);
    }

    private void invalidateLayers() {
        invalidate();
        final float dim = getMusicDimming();
        if (musicFrameCallback != null && Math.abs(dim - notifiedMusicDim) > .001f) {
            notifiedMusicDim = dim;
            musicFrameCallback.run();
        }
    }

    private void updateAnimationState() {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)
                && (musicPresence != 0 || musicLevel != 0 || musicBass != 0 || musicHop != 0)) {
            musicPresence = musicLevel = musicBass = musicHop = musicHopVelocity = musicFlash = 0;
            invalidateLayers();
        }
        final boolean animate = canAnimate();
        final boolean observe = animate && musicDocumentId != 0 && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
        if (observingAudio != observe) {
            MediaController.getInstance().setPlaybackLevelObserver(this, observe);
            observingAudio = observe;
        }
        if (animate && !frameScheduled) {
            lastFrameNanos = 0;
            frameScheduled = true;
            postOnAnimation(orbitFrame);
        } else if (!animate) {
            resetPhotoMotion();
            removeCallbacks(orbitFrame);
            frameScheduled = false;
            lastFrameNanos = 0;
        }
    }

    private final Runnable orbitFrame = new Runnable() {
        @Override
        public void run() {
            frameScheduled = false;
            if (!canAnimate()) {
                updateAnimationState();
                return;
            }
            final long now = System.nanoTime();
            final float dt = lastFrameNanos == 0 ? 0f : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
            lastFrameNanos = now;
            final boolean ambientMotion = LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
            if (ambientMotion) motionTime += dt;
            final float target = ambientMotion
                    ? MediaController.getInstance().getPlaybackLevel(currentAccount, dialogId, musicDocumentId) : 0f;
            final float response = target > musicLevel ? 18f : 5f;
            musicLevel += (target - musicLevel) * (1f - (float) Math.exp(-response * dt));
            final boolean playing = ambientMotion && MediaController.getInstance()
                    .isProfileMusicPlaying(currentAccount, dialogId, musicDocumentId);
            musicPresence += ((playing ? 1f : 0f) - musicPresence) * (1f - (float) Math.exp(-5f * dt));
            if (!playing && musicPresence < .001f) musicPresence = 0;
            final float bassTarget = ambientMotion
                    ? MediaController.getInstance().getPlaybackBass(currentAccount, dialogId, musicDocumentId) : 0f;
            musicBass += (bassTarget - musicBass) * (1f - (float) Math.exp(-(bassTarget > musicBass ? 25f : 9f) * dt));
            final float bassAttack = Math.max(0f, bassTarget - previousMusicTarget - .025f);
            musicFlash = Math.min(1f, Math.max(musicFlash * (float) Math.exp(-8f * dt), bassAttack * 1.3f));
            musicHopVelocity = Math.min(14f, musicHopVelocity + bassAttack * 14f);
            previousMusicTarget = bassTarget;
            if (!avatarSpinning) spinVelocity *= (float) Math.exp(-1.25f * dt);
            if (Math.abs(spinVelocity) < .01f) spinVelocity = 0;
            simulateBubbles(dt, ambientMotion);
            updatePhotoMotion();
            invalidateLayers();
            frameScheduled = true;
            postOnAnimation(this);
        }
    };

    private void simulateBubbles(float dt, boolean ambientMotion) {
        // Physics is independent of the display rate; at 90/120 Hz this normally needs one step.
        final int steps = Math.max(1, (int) Math.ceil(dt * 120f));
        final float step = dt / steps;
        for (int n = 0; n < steps; n++) {
            final boolean motion = ambientMotion || hasInteraction();
            musicHopVelocity += (-120f * musicHop - 14f * musicHopVelocity) * step;
            musicHop += musicHopVelocity * step;
            for (Gift gift : gifts) {
                gift.phaseRate = (ambientMotion ? gift.orbitSpeed : 0f) + spinVelocity + gift.extraSpeed;
                gift.contactTime = Math.max(0f, gift.contactTime - step);
                if (avatarSpinning || gift == pressedGift) gift.phaseRate = 0;
                if (motion) gift.phase += gift.phaseRate * step;
                gift.phase %= (float) Math.PI * 2;
                gift.extraSpeed *= (float) Math.exp(-1.6f * step);
                if (Math.abs(gift.extraSpeed) < .01f) gift.extraSpeed = 0;
                if (gift != pressedGift) {
                    gift.vx += (-32f * gift.offsetX - 9f * gift.vx) * step;
                    gift.vy += (-32f * gift.offsetY - 9f * gift.vy) * step;
                    gift.offsetX += gift.vx * step;
                    gift.offsetY += gift.vy * step;
                    if (Math.abs(gift.offsetX) + Math.abs(gift.offsetY) < dp(.1f)
                            && Math.abs(gift.vx) + Math.abs(gift.vy) < dp(.5f)) {
                        gift.offsetX = gift.offsetY = gift.vx = gift.vy = 0;
                    }
                } else {
                    gift.holdVX *= (float) Math.exp(-14f * step);
                    gift.holdVY *= (float) Math.exp(-14f * step);
                }
                updateOrbitBody(gift);
            }
            if (collapseProgress > .85f && orbitRadiusX > 0) resolveBubbleContacts();
        }
        for (Gift gift : gifts) updateFloatingSymbols(gift, dt);
        if (!ambientMotion && pressedGift == null && !avatarSpinCandidate && spinVelocity == 0) {
            boolean quiet = true;
            for (Gift gift : gifts) {
                if (Math.abs(gift.vx) + Math.abs(gift.vy) > dp(3) || Math.abs(gift.extraSpeed) > .01f) {
                    quiet = false;
                    break;
                }
            }
            stationaryTime = quiet ? stationaryTime + dt : 0;
            // A resting contact may retain a small spring offset: it must not keep power-saving mode awake.
            if (stationaryTime > .35f) physicsSleeping = true;
        } else {
            physicsSleeping = false;
            stationaryTime = 0;
        }
    }

    private void updateOrbitBody(Gift gift) {
        final float sp = (float) Math.sin(gift.phase), cp = (float) Math.cos(gift.phase);
        final float ct = (float) Math.cos(gift.orbitTilt), st = (float) Math.sin(gift.orbitTilt);
        final float rx = orbitRadiusX * gift.orbitSize, ry = orbitRadiusY * gift.orbitHeight;
        gift.depth = sp;
        gift.targetX = orbitCenterX + rx * cp * ct - ry * sp * st + dp(.6f) * musicHop * sp;
        gift.targetY = orbitCenterY + rx * cp * st + ry * sp * ct
                + dp(3) * (float) Math.sin(motionTime * 1.3f + gift.phase * 2)
                - dp(3) * musicHop * (.85f + .15f * cp);
        // Only the user's hand lifts a bubble above the avatar. A released bubble obeys its orbital depth immediately.
        gift.inFront = gift.depth >= 0 || gift == pressedGift;
        if (gift == pressedGift) {
            gift.offsetX = dragX - gift.targetX;
            gift.offsetY = dragY - gift.targetY;
        }
        gift.bodyX = gift.targetX + gift.offsetX;
        gift.bodyY = gift.targetY + gift.offsetY;
        gift.bodyVX = gift == pressedGift ? gift.holdVX
                : (-rx * sp * ct - ry * cp * st) * gift.phaseRate + gift.vx + dp(.6f) * musicHopVelocity * sp;
        gift.bodyVY = gift == pressedGift ? gift.holdVY
                : (-rx * sp * st + ry * cp * ct) * gift.phaseRate + gift.vy - dp(3) * musicHopVelocity * (.85f + .15f * cp);
        gift.bodyRadius = dp(23) * (.88f + .14f * gift.depth) * gift.bounce.getScale(-.60f)
                * (1f + musicBass * .025f + musicFlash * .02f) * 1.035f;
    }

    private void resolveBubbleContacts() {
        if (pressedGift == null) {
            boolean activeContact = false;
            for (Gift gift : gifts) {
                if (gift.contactTime > 0) {
                    activeContact = true;
                    break;
                }
            }
            if (!activeContact) return;
        }
        // Small pairwise circle solver: no allocations, no layout changes, no fixed orbit slots.
        // A held bubble has infinite mass; other bubbles bounce off it without moving the finger.
        for (int iteration = 0; iteration < 6; iteration++) {
            boolean contact = false;
            for (int i = 0; i < gifts.size(); i++) {
                final Gift a = gifts.get(i);
                for (int j = i + 1; j < gifts.size(); j++) {
                    final Gift b = gifts.get(j);
                    if (a != pressedGift && b != pressedGift && a.contactTime <= 0f && b.contactTime <= 0f) continue;
                    float dx = b.bodyX - a.bodyX, dy = b.bodyY - a.bodyY;
                    final float minimum = a.bodyRadius + b.bodyRadius + dp(2);
                    final float squared = dx * dx + dy * dy;
                    if (squared >= minimum * minimum) continue;
                    contact = true;
                    // Propagate the remaining interaction window, never extend it on each bounce.
                    final float window = a == pressedGift || b == pressedGift ? .35f : Math.max(a.contactTime, b.contactTime);
                    a.contactTime = Math.max(a.contactTime, window);
                    b.contactTime = Math.max(b.contactTime, window);
                    float distance = (float) Math.sqrt(squared);
                    if (distance < .001f) {
                        dx = (float) Math.cos(a.phase + i);
                        dy = (float) Math.sin(a.phase + i);
                        distance = 0;
                    } else {
                        dx /= distance;
                        dy /= distance;
                    }
                    final float invA = a == pressedGift ? 0f : 1f, invB = b == pressedGift ? 0f : 1f;
                    final float mass = invA + invB;
                    final float correction = Math.max(0f, minimum - distance - dp(.02f)) * .9f / mass;
                    a.offsetX -= dx * correction * invA;
                    a.offsetY -= dy * correction * invA;
                    b.offsetX += dx * correction * invB;
                    b.offsetY += dy * correction * invB;
                    a.bodyX -= dx * correction * invA;
                    a.bodyY -= dy * correction * invA;
                    b.bodyX += dx * correction * invB;
                    b.bodyY += dy * correction * invB;
                    final float approach = (b.bodyVX - a.bodyVX) * dx + (b.bodyVY - a.bodyVY) * dy;
                    if (approach < 0) {
                        final float impulse = -1.55f * approach / mass;
                        a.vx -= dx * impulse * invA;
                        a.vy -= dy * impulse * invA;
                        b.vx += dx * impulse * invB;
                        b.vy += dy * impulse * invB;
                        a.bodyVX -= dx * impulse * invA;
                        a.bodyVY -= dy * impulse * invA;
                        b.bodyVX += dx * impulse * invB;
                        b.bodyVY += dy * impulse * invB;
                    }
                }
            }
            if (!contact) break;
        }
    }

    private void updateFloatingSymbols(Gift gift, float dt) {
        if (dt <= 0) return;
        final float changeX = gift.bodyVX - gift.patternMotionVX;
        final float changeY = gift.bodyVY - gift.patternMotionVY;
        gift.patternMotionVX = gift.bodyVX;
        gift.patternMotionVY = gift.bodyVY;
        // Ignore the slow orbital acceleration: only a shake, throw or impact lifts the settled symbols.
        final boolean shaken = changeX * changeX + changeY * changeY > dp(18) * dp(18);
        if (shaken) {
            gift.patternSleeping = false;
            gift.patternRestTime = 0;
            gift.snowAgitation = Math.min(1f, gift.snowAgitation + (float) Math.hypot(changeX, changeY) / dp(500));
            final float impulseX = Math.max(-1.6f, Math.min(1.6f, -changeX / dp(23) * .04f));
            final float impulseY = Math.max(-1.6f, Math.min(1.6f, -changeY / dp(23) * .04f));
            for (int i = 0; i < gift.patternX.length; i++) {
                final float angle = i * 2.399963f + motionTime * .9f + gift.phase * .2f;
                final float energy = Math.min(1.3f, (float) Math.hypot(impulseX, impulseY));
                final float scatter = .6f + energy * 1.1f;
                gift.patternVX[i] += impulseX * .25f + (float) Math.cos(angle) * scatter;
                gift.patternVY[i] += impulseY * .25f + (float) Math.sin(angle) * scatter;
                gift.patternSpin[i] += impulseX * (12 + i % 7) + impulseY * (i % 2 == 0 ? 12 : -12);
            }
        }
        gift.snowAgitation *= (float) Math.exp(-1.4f * dt);
        if (gift.patternSleeping) return;
        final int steps = Math.max(1, (int) Math.ceil(dt * 120f));
        final float step = dt / steps;
        final float damping = (float) Math.exp(-1.6f * step);
        for (int n = 0; n < steps; n++) {
            for (int i = 0; i < gift.patternX.length; i++) {
                gift.patternVX[i] = Math.max(-3f, Math.min(3f, gift.patternVX[i]));
                gift.patternVY[i] = Math.max(-3f, Math.min(3f, gift.patternVY[i]));
                gift.patternVX[i] = (gift.patternVX[i] + gift.snowAgitation * .45f
                        * (float) Math.sin(motionTime * 2f + i * 2.4f) * step) * damping;
                gift.patternVY[i] = (gift.patternVY[i] + (1.2f * (1f - .9f * gift.snowAgitation) + gift.snowAgitation * .35f
                        * (float) Math.cos(motionTime * 1.7f + i * 1.3f)) * step) * damping;
                gift.patternX[i] += gift.patternVX[i] * step;
                gift.patternY[i] += gift.patternVY[i] * step;
                gift.patternSpin[i] *= damping;
                gift.patternRotation[i] = (gift.patternRotation[i] + gift.patternSpin[i] * step) % 360;
            }
            // Separate the flakes throughout the cavity; during a shake the extra clearance prevents a clump.
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < gift.patternX.length; i++) {
                    for (int j = i + 1; j < gift.patternX.length; j++) {
                        float dx = gift.patternX[j] - gift.patternX[i], dy = gift.patternY[j] - gift.patternY[i];
                        final float minimum = (gift.patternSize[i] + gift.patternSize[j]) / 46f + gift.snowAgitation * .10f;
                        final float squared = dx * dx + dy * dy;
                        if (squared >= minimum * minimum) continue;
                        float distance = (float) Math.sqrt(squared);
                        if (distance < .0001f) {
                            dx = 1f;
                            dy = 0f;
                            distance = 0;
                        } else {
                            dx /= distance;
                            dy /= distance;
                        }
                        final float push = (minimum - distance) * .48f;
                        gift.patternX[i] -= dx * push;
                        gift.patternY[i] -= dy * push;
                        gift.patternX[j] += dx * push;
                        gift.patternY[j] += dy * push;
                        final float approach = (gift.patternVX[j] - gift.patternVX[i]) * dx
                                + (gift.patternVY[j] - gift.patternVY[i]) * dy;
                        if (approach < 0) {
                            final float impulse = -.575f * approach;
                            gift.patternVX[i] -= dx * impulse;
                            gift.patternVY[i] -= dy * impulse;
                            gift.patternVX[j] += dx * impulse;
                            gift.patternVY[j] += dy * impulse;
                        }
                    }
                    final float squared = gift.patternX[i] * gift.patternX[i] + gift.patternY[i] * gift.patternY[i];
                    final float wall = .96f - gift.patternSize[i] / 46f;
                    if (squared > wall * wall) {
                        final float length = (float) Math.sqrt(squared);
                        final float nx = gift.patternX[i] / length, ny = gift.patternY[i] / length;
                        gift.patternX[i] = nx * wall;
                        gift.patternY[i] = ny * wall;
                        final float outward = gift.patternVX[i] * nx + gift.patternVY[i] * ny;
                        if (outward > 0) {
                            gift.patternVX[i] -= 1.25f * outward * nx;
                            gift.patternVY[i] -= 1.25f * outward * ny;
                        }
                    }
                }
            }
        }
        float meanSquaredSpeed = 0;
        for (int i = 0; i < gift.patternX.length; i++) {
            meanSquaredSpeed += gift.patternVX[i] * gift.patternVX[i] + gift.patternVY[i] * gift.patternVY[i];
        }
        meanSquaredSpeed /= gift.patternX.length;
        gift.patternRestTime = !shaken && gift.snowAgitation < .02f && meanSquaredSpeed < .0016f ? gift.patternRestTime + dt : 0;
        if (gift.patternRestTime > .4f) {
            gift.patternSleeping = true;
            for (int i = 0; i < gift.patternX.length; i++) {
                gift.patternVX[i] = gift.patternVY[i] = gift.patternSpin[i] = 0;
            }
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!isVisible) cancelGesture();
        updateAnimationState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // View's constructor can invoke this before the foreground layer exists.
        if (foregroundView != null) updateAnimationState();
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidateLayers();
            updateAnimationState();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidateLayers();
            updateAnimationState();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidateLayers();
        updateAnimationState();
    }


    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;

        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.messagePlayingDidReset);

        for (Gift gift : gifts) {
            gift.emojiDrawable.addView(this);
            gift.emojiDrawable.addView(foregroundView);
            gift.pattern.attach();
        }

        update();
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        cancelGesture();
        updateAnimationState();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);

        for (Gift gift : gifts) {
            gift.emojiDrawable.removeView(this);
            gift.emojiDrawable.removeView(foregroundView);
            gift.pattern.detach();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        } else {
            updateAnimationState();
            invalidateLayers();
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        public final int edgeColor;
        public final int patternColor;
        public final TLRPC.Document patternDocument;
        public final long patternDocumentId;
        private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;
        private Path bubblePath;
        private Paint rimPaint;
        private Paint shinePaint;
        private Paint glowPaint;
        private Paint flashPaint;
        private float phase, orbitSpeed, orbitSize, orbitTilt, orbitHeight, extraSpeed;
        private float contactTime, phaseRate;
        private float bodyX, bodyY, bodyVX, bodyVY, bodyRadius, holdVX, holdVY;
        private float patternMotionVX, patternMotionVY, patternRestTime;
        private boolean patternSleeping;
        private float snowAgitation;
        private RadialGradient reflectionGradient;
        private Matrix reflectionMatrix;
        private Paint reflectionPaint;
        private float[] patternX, patternY, patternSize, patternRotation, patternOpacity, patternVX, patternVY, patternSpin;
        private float offsetX, offsetY, vx, vy;
        private float targetX, targetY, depth, renderAlpha;
        private boolean inFront;

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop == null ? 0xFF72BFD4 : backdrop.center_color | 0xFF000000;
            edgeColor = backdrop == null ? color : backdrop.edge_color | 0xFF000000;
            patternColor = backdrop == null ? Color.WHITE : backdrop.pattern_color | 0xFF000000;
            final TL_stars.starGiftAttributePattern attribute = findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);
            patternDocument = attribute == null ? null : attribute.document;
            patternDocumentId = patternDocument == null ? 0 : patternDocument.id;
            slug = gift.slug;
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            edgeColor = status.edge_color | 0xFF000000;
            patternColor = status.pattern_color | 0xFF000000;
            patternDocument = null;
            patternDocumentId = status.pattern_document_id;
            slug = status.slug;
        }

        private void initBubble() {
            // Reuse particle state: the symbols settle in the bowl and react to inertia when shaken.
            final java.util.Random random = new java.util.Random(id ^ patternDocumentId);
            patternX = new float[18];
            patternY = new float[18];
            patternSize = new float[18];
            patternRotation = new float[18];
            patternOpacity = new float[18];
            patternVX = new float[18];
            patternVY = new float[18];
            patternSpin = new float[18];
            for (int i = 0; i < patternX.length; i++) {
                patternSize[i] = 3f + random.nextFloat() * 2.5f;
                patternRotation[i] = -55f + random.nextFloat() * 110f;
                patternOpacity[i] = .22f + random.nextFloat() * .22f;
                // Start scattered in the lower bowl; gravity and contacts settle the individual symbols.
                patternX[i] = -.75f + random.nextFloat() * 1.5f;
                patternY[i] = .3f + random.nextFloat() * .48f;
                final float wall = .96f - patternSize[i] / 46f;
                final float distance = (float) Math.hypot(patternX[i], patternY[i]);
                if (distance > wall) {
                    patternX[i] *= wall / distance;
                    patternY[i] *= wall / distance;
                }
            }
            orbitSpeed = .40f + random.nextFloat() * .18f;
            orbitSize = .82f + random.nextFloat() * .32f;
            orbitTilt = -.25f + random.nextFloat() * .5f;
            orbitHeight = .75f + random.nextFloat() * .4f;
            reflectionGradient = new RadialGradient(0, 0, 1f, new int[] {
                    ColorUtils.blendARGB(color, Color.WHITE, .60f),
                    ColorUtils.blendARGB(color, Color.WHITE, .18f), ColorUtils.setAlphaComponent(color, 0)
            }, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP);
            reflectionMatrix = new Matrix();
            reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            reflectionPaint.setStyle(Paint.Style.STROKE);
            reflectionPaint.setShader(reflectionGradient);
            bubblePath = new Path();
            flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            flashPaint.setColor(Color.WHITE);
            rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(dpf2(0.7f));
            rimPaint.setShader(new SweepGradient(0, 0, new int[] {
                    0x28FFFFFF, 0xB0FFFFFF, ColorUtils.setAlphaComponent(color, 160),
                    ColorUtils.setAlphaComponent(edgeColor, 80), 0xC0FFFFFF,
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(color, Color.WHITE, .55f), 90), 0x28FFFFFF
            }, null));
            shinePaint.setStyle(Paint.Style.STROKE);
            shinePaint.setStrokeWidth(dpf2(1.2f));
            shinePaint.setStrokeCap(Paint.Cap.ROUND);
            glowPaint.setShader(new RadialGradient(0, 0, dp(38), new int[] {
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(color, Color.WHITE, .5f), 110),
                    Color.TRANSPARENT
            }, null, Shader.TileMode.CLAMP));
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
            pattern = b.pattern;
            phase = b.phase;
            orbitSpeed = b.orbitSpeed;
            orbitSize = b.orbitSize;
            orbitTilt = b.orbitTilt;
            orbitHeight = b.orbitHeight;
            patternX = b.patternX;
            patternY = b.patternY;
            patternSize = b.patternSize;
            patternRotation = b.patternRotation;
            patternOpacity = b.patternOpacity;
            patternVX = b.patternVX;
            patternVY = b.patternVY;
            patternSpin = b.patternSpin;
            patternMotionVX = b.patternMotionVX;
            patternMotionVY = b.patternMotionVY;
            patternRestTime = b.patternRestTime;
            patternSleeping = b.patternSleeping;
            contactTime = b.contactTime;
            extraSpeed = b.extraSpeed;
            offsetX = b.offsetX;
            offsetY = b.offsetY;
            vx = b.vx;
            vy = b.vy;
            bubblePath = b.bubblePath;
            rimPaint = b.rimPaint;
            shinePaint = b.shinePaint;
            glowPaint = b.glowPaint;
            flashPaint = b.flashPaint;
            reflectionGradient = b.reflectionGradient;
            reflectionMatrix = b.reflectionMatrix;
            reflectionPaint = b.reflectionPaint;
            snowAgitation = b.snowAgitation;
        }

        private void draw(Canvas canvas, float cx, float cy, float ascale, float alpha) {
            renderAlpha = alpha;
            if (alpha <= 0.01f) {
                bounds.setEmpty();
                return;
            }
            final float radius = dp(23);
            final float scale = ascale * bounce.getScale(-0.60f) * (1f + musicBass * .025f + musicFlash * .02f);
            bounds.set(cx - radius * scale, cy - radius * scale, cx + radius * scale, cy + radius * scale);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            if (musicLevel > .01f) {
                glowPaint.setAlpha((int) (200 * musicBass * alpha));
                canvas.drawCircle(0, 0, dp(38), glowPaint);
            }
            final float wobbleTime = motionTime * 1.4f + phase;
            buildOrganicPath(bubblePath, radius, wobbleTime, .035f);
            gradientPaint.setAlpha((int) (255 * alpha));
            canvas.drawPath(bubblePath, gradientPaint);
            flashPaint.setAlpha((int) ((musicBass * 12 + musicFlash * 50) * alpha));
            canvas.drawPath(bubblePath, flashPaint);
            canvas.save();
            canvas.clipPath(bubblePath);
            // Use the collectible's actual pattern document and backdrop tint.
            if (patternDocumentId != 0) {
                for (int i = 0; i < patternX.length; i++) {
                    final float x = radius * patternX[i], y = radius * patternY[i];
                    final int half = dp(patternSize[i] / 2f);
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.rotate(patternRotation[i]);
                    pattern.setAlpha((int) (255 * alpha * patternOpacity[i]));
                    pattern.setBounds(-half, -half, half, half);
                    pattern.draw(canvas);
                    canvas.restore();
                }
            }
            if (emojiDrawable != null) {
                final int sz = dp(27);
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (255 * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
            rimPaint.setAlpha((int) ((165 + 35 * musicBass + 20 * musicFlash) * alpha));
            canvas.drawPath(bubblePath, rimPaint);
            shinePaint.setColor(Color.WHITE);
            shinePaint.setAlpha((int) (120 * alpha));
            canvas.drawArc(-radius + dp(3), -radius + dp(3), radius - dp(3), radius - dp(3),
                    208, 42, false, shinePaint);
            shinePaint.setAlpha((int) (48 * alpha));
            canvas.drawArc(-radius + dp(2), -radius + dp(2), radius - dp(2), radius - dp(2),
                    35, 32, false, shinePaint);
            canvas.restore();
        }
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void update() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            cancelGesture();
            for (Gift gift : gifts) {
                gift.emojiDrawable.removeView(this);
                gift.emojiDrawable.removeView(foregroundView);
                gift.pattern.detach();
            }
            gifts.clear();
            oldGifts.clear();
            giftIds.clear();
            drawOrder = new Gift[0];
            updateAnimationState();
            invalidateLayers();
            return;
        }

        cancelGesture();
        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null && (g.color != oldGift.color || g.edgeColor != oldGift.edgeColor
                    || g.patternColor != oldGift.patternColor || g.patternDocumentId != oldGift.patternDocumentId
                    || g.documentId != oldGift.documentId)) {
                oldGift.emojiDrawable.removeView(this);
                oldGift.emojiDrawable.removeView(foregroundView);
                oldGift.pattern.detach();
                oldGift = null;
                changed = true;
            }
            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.initBubble();
                g.gradient = new RadialGradient(-dp(7), -dp(8), dp(35), new int[] {
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(g.color, Color.WHITE, .65f), 96),
                        ColorUtils.setAlphaComponent(g.color, 38), ColorUtils.setAlphaComponent(g.edgeColor, 100)
                }, new float[] {0, .6f, 1}, Shader.TileMode.CLAMP);
                g.phase = .3f + i * ((float) Math.PI * 2 / Math.max(1, gifts.size()));
                g.pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(ProfileGiftsView.this, false, dp(8),
                        AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
                g.pattern.setCurrentAccount(currentAccount);
                if (g.patternDocument != null) g.pattern.set(g.patternDocument, false);
                else if (g.patternDocumentId != 0) g.pattern.set(g.patternDocumentId, false);
                g.pattern.setColor(ColorUtils.blendARGB(g.patternColor, Color.WHITE, .65f));
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    g.emojiDrawable.addView(this);
                    g.emojiDrawable.addView(foregroundView);
                    g.pattern.attach();
                }
            }
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable.removeView(foregroundView);
                g.pattern.detach();
                g.emojiDrawable = null;
                g.gradient = null;
            }
        }

        drawOrder = gifts.toArray(new Gift[0]);
        oldGifts.clear();
        if (changed) invalidateLayers();
        updateAnimationState();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        drawOrbitLayer(canvas, false);
    }

    private void drawOrbitLayer(Canvas canvas, boolean front) {
        updateAnimationState();
        if (expandProgress >= 1f || collapseProgress <= 0f) {
            for (Gift gift : gifts) gift.bounds.setEmpty();
            return;
        }
        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = avatarContainer.getWidth() * avatarContainer.getScaleX();
        final float realH = avatarContainer.getHeight() * avatarContainer.getScaleY();
        // The actual avatar is the centre, including its transition translation and scale.
        final float aw = Math.max(realW, dpf2(80)), ah = Math.max(realH, dpf2(80));
        final float cx = realX + realW / 2f, cy = realY + realH / 2f;
        final float radiusX = aw * .95f + dp(14);
        final float radiusY = ah * .60f;
        orbitCenterX = cx;
        orbitCenterY = cy;
        orbitRadiusX = radiusX;
        orbitRadiusY = radiusY;
        final float visibility = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight
                + ActionBar.getCurrentActionBarHeight())) / dp(50)) * (1f - expandProgress) * (1f - actionBarProgress);
        if (visibility <= .01f) {
            for (Gift gift : gifts) gift.bounds.setEmpty();
            return;
        }
        final float collapse = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(collapseProgress);
        for (Gift gift : gifts) updateOrbitBody(gift);
        // A small stable insertion sort, with no per-frame object allocation.
        for (int i = 1; i < drawOrder.length; i++) {
            Gift gift = drawOrder[i];
            int j = i - 1;
            while (j >= 0 && drawOrder[j].depth > gift.depth) {
                drawOrder[j + 1] = drawOrder[j--];
            }
            drawOrder[j + 1] = gift;
        }
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        if (!front) drawAvatarReflections(canvas, cx, cy, realW / 2f, visibility);
        for (Gift gift : drawOrder) {
            if (gift.inFront != front) continue;
            float x = gift.bodyX, y = gift.bodyY;
            final float enter = gift.animatedFloat.set(1f);
            x = lerp(realX + realW / 2f, x, collapse);
            y = lerp(realY + realH / 2f, y, collapse);
            final float scale = lerp(.55f, 1f, collapse) * lerp(.6f, 1f, enter) * (.88f + .14f * gift.depth);
            final boolean freeDrag = gift == pressedGift || gift.contactTime > 0f;
            if (!freeDrag) {
                canvas.save();
                canvas.clipRect(0, 0, getWidth(), expandY);
            }
            gift.draw(canvas, x, y, scale, enter * visibility);
            if (!freeDrag) canvas.restore();
        }
        canvas.restore();
    }

    private void updatePhotoMotion() {
        final float visibility = musicPresence * collapseProgress * (1f - expandProgress) * getAlpha();
        final float amount = dp(.4f + .6f * musicBass) * visibility;
        photoMotionX = (float) Math.cos(motionTime * 1.45f) * amount;
        photoMotionY = (float) Math.sin(motionTime * 1.45f) * amount - dp(1.2f) * musicHop * visibility;
        photoMotionScale = 1f + visibility * (.003f + Math.abs(musicHop) * .009f);
        avatarImage.setMusicMotion(photoMotionX / Math.max(.01f, avatarContainer.getScaleX()),
                photoMotionY / Math.max(.01f, avatarContainer.getScaleY()), photoMotionScale);
    }

    private void resetPhotoMotion() {
        photoMotionX = photoMotionY = 0;
        photoMotionScale = 1f;
        avatarImage.setMusicMotion(0, 0, 1f);
    }

    private void drawAvatarReflections(Canvas canvas, float cx, float cy, float radius, float alpha) {
        if (radius <= 0 || alpha <= .01f) return;
        cx += photoMotionX;
        cy += photoMotionY;
        radius = Math.max(0, radius - avatarImage.getVisiblePhotoInset() * avatarContainer.getScaleX()) * photoMotionScale;
        for (Gift gift : gifts) {
            final float distance = (float) Math.hypot(gift.bodyX - cx, gift.bodyY - cy);
            final float proximity = Utilities.clamp01(1f - Math.max(0f, distance - radius - gift.bodyRadius) / dp(80));
            if (proximity <= .01f) continue;
            final float reach = radius * 1.85f + gift.bodyRadius;
            gift.reflectionMatrix.setScale(reach, reach);
            gift.reflectionMatrix.postTranslate(gift.bodyX, gift.bodyY);
            gift.reflectionGradient.setLocalMatrix(gift.reflectionMatrix);
            final float strength = alpha * proximity * (.7f + .3f * gift.depth);
            // The spatial gradient lights only the edge nearest this gift, with a soft outer falloff.
            gift.reflectionPaint.setStrokeWidth(dpf2(6.5f));
            gift.reflectionPaint.setAlpha((int) (42 * strength));
            canvas.drawCircle(cx, cy, radius + dp(.5f), gift.reflectionPaint);
            gift.reflectionPaint.setStrokeWidth(dpf2(1.25f));
            gift.reflectionPaint.setAlpha((int) (210 * strength));
            canvas.drawCircle(cx, cy, radius + dp(.4f), gift.reflectionPaint);
        }
    }

    private static void buildOrganicPath(Path path, float radius, float time, float deformation) {
        path.rewind();
        final int segments = 16;
        final float step = (float) (Math.PI * 2 / segments);
        for (int i = 0; i < segments; i++) {
            final float a = i * step, b = (i + 1) * step;
            final float ra = radius * (1f + deformation * (.65f * (float) Math.sin(3 * a + time)
                    + .35f * (float) Math.cos(2 * a - time * .8f)));
            final float rb = radius * (1f + deformation * (.65f * (float) Math.sin(3 * b + time)
                    + .35f * (float) Math.cos(2 * b - time * .8f)));
            final float da = radius * deformation * (1.95f * (float) Math.cos(3 * a + time)
                    - .7f * (float) Math.sin(2 * a - time * .8f));
            final float db = radius * deformation * (1.95f * (float) Math.cos(3 * b + time)
                    - .7f * (float) Math.sin(2 * b - time * .8f));
            final float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            final float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            if (i == 0) path.moveTo(ra * ca, ra * sa);
            path.cubicTo(ra * ca + (da * ca - ra * sa) * step / 3,
                    ra * sa + (da * sa + ra * ca) * step / 3,
                    rb * cb - (db * cb - rb * sb) * step / 3,
                    rb * sb - (db * sb + rb * cb) * step / 3, rb * cb, rb * sb);
        }
        path.close();
    }

    public Gift getGiftUnder(float x, float y) {
        if (!active || !hostResumed || !isShown() || getAlpha() < .1f || expandProgress >= 1f || collapseProgress < .85f
                || actionBarProgress > .1f) return null;
        for (int i = drawOrder.length - 1; i >= 0; i--) {
            Gift gift = drawOrder[i];
            if ((y <= expandY || gift.contactTime > 0f || gift == pressedGift)
                    && gift.renderAlpha > .1f && gift.bounds.contains(x, y)) return gift;
        }
        return null;
    }

    private boolean avatarSpinCandidate, avatarSpinning;
    private int avatarPointer = -1;
    private float avatarDownX, avatarDownY, avatarLastX;
    private VelocityTracker avatarVelocity;

    public boolean isAvatarSpinning() {
        return avatarSpinning;
    }

    public boolean onAvatarTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAvatarGesture();
            if (!active || !hostResumed || gifts.isEmpty() || expandProgress > .05f || collapseProgress < .85f
                    || actionBarProgress > .1f || getAlpha() < .9f) return false;
            final float w = avatarContainer.getWidth() * avatarContainer.getScaleX();
            final float h = avatarContainer.getHeight() * avatarContainer.getScaleY();
            if (w <= 0 || h <= 0) return false;
            final float nx = (event.getX() - avatarContainer.getX() - w / 2f) / (w / 2f);
            final float ny = (event.getY() - avatarContainer.getY() - h / 2f) / (h / 2f);
            final Gift hit = getGiftUnder(event.getX(), event.getY());
            if (nx * nx + ny * ny > 1f || hit != null && hit.inFront) return false;
            avatarSpinCandidate = true;
            physicsSleeping = false;
            stationaryTime = 0;
            avatarPointer = event.getPointerId(0);
            avatarDownX = avatarLastX = event.getX();
            avatarDownY = event.getY();
            avatarVelocity = VelocityTracker.obtain();
            avatarVelocity.addMovement(event);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        }
        if (!avatarSpinCandidate) return false;
        final boolean consumed = avatarSpinning;
        final int index = event.findPointerIndex(avatarPointer);
        if (action == MotionEvent.ACTION_CANCEL || index < 0
                || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == avatarPointer) {
            cancelAvatarGesture();
            return consumed;
        }
        avatarVelocity.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) {
            final float x = event.getX(index), y = event.getY(index);
            final float dx = x - avatarDownX, dy = y - avatarDownY;
            if (!avatarSpinning && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                cancelAvatarGesture();
                return false;
            }
            if (!avatarSpinning && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                avatarSpinning = true;
                spinVelocity = 0;
            }
            if (avatarSpinning) {
                final float radius = Math.max(dp(32), avatarContainer.getWidth() * avatarContainer.getScaleX() * .65f);
                final float angle = (x - avatarLastX) / radius;
                for (Gift gift : gifts) gift.phase += angle;
                avatarLastX = x;
                invalidateLayers();
                updateAnimationState();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (avatarSpinning) {
                avatarVelocity.computeCurrentVelocity(1000, dp(1800));
                final float radius = Math.max(dp(32), avatarContainer.getWidth() * avatarContainer.getScaleX() * .65f);
                spinVelocity = Math.max(-8f, Math.min(8f, avatarVelocity.getXVelocity(avatarPointer) / radius));
            }
            cancelAvatarGesture();
            updateAnimationState();
            return consumed;
        }
        return consumed;
    }

    private void cancelAvatarGesture() {
        if (!avatarSpinCandidate) return;
        avatarSpinCandidate = avatarSpinning = false;
        avatarPointer = -1;
        if (avatarVelocity != null) {
            avatarVelocity.recycle();
            avatarVelocity = null;
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private Gift pressedGift;
    private boolean dragging;
    private int activePointerId = -1;
    private float downX, downY, dragX, dragY, grabX, grabY;
    private VelocityTracker velocityTracker;
    private long lastDragEventTime;
    private final Runnable startDragging = () -> {
        if (pressedGift == null) return;
        dragging = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidateLayers();
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleTouch(event, false);
    }

    private boolean handleTouch(MotionEvent event, boolean front) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            final Gift hit = getGiftUnder(event.getX(), event.getY());
            if (hit == null || hit.inFront != front) return false;
            cancelGesture();
            pressedGift = hit;
            physicsSleeping = false;
            stationaryTime = 0;
            activePointerId = event.getPointerId(0);
            downX = event.getX();
            downY = event.getY();
            dragX = hit.bounds.centerX();
            dragY = hit.bounds.centerY();
            grabX = dragX - downX;
            grabY = dragY - downY;
            hit.vx = hit.vy = hit.holdVX = hit.holdVY = 0;
            lastDragEventTime = event.getEventTime();
            hit.bounce.setPressed(true);
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            postDelayed(startDragging, ViewConfiguration.getLongPressTimeout());
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (pressedGift == null) return false;
        if (!active || !hostResumed || expandProgress >= 1f || collapseProgress < .85f) {
            cancelGesture();
            return true;
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
        final int pointer = event.findPointerIndex(activePointerId);
        if (action == MotionEvent.ACTION_CANCEL || pointer < 0
                || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == activePointerId) {
            cancelGesture();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            final float x = event.getX(pointer), y = event.getY(pointer);
            if (!dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                removeCallbacks(startDragging);
                dragging = true;
            }
            if (dragging) {
                // Touch capture continues outside the header; use the whole profile viewport for the held bubble.
                final float oldX = dragX, oldY = dragY;
                dragX = Math.max(dp(16), Math.min(getWidth() - dp(16), x + grabX));
                dragY = Math.max(dp(16), Math.min(getHeight() - dp(16), y + grabY));
                final float seconds = Math.max(.008f, (event.getEventTime() - lastDragEventTime) / 1000f);
                pressedGift.holdVX = Math.max(-dp(1800), Math.min(dp(1800), (dragX - oldX) / seconds));
                pressedGift.holdVY = Math.max(-dp(1800), Math.min(dp(1800), (dragY - oldY) / seconds));
                lastDragEventTime = event.getEventTime();
                pressedGift.offsetX = dragX - pressedGift.targetX;
                pressedGift.offsetY = dragY - pressedGift.targetY;
                invalidateLayers();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            final Gift gift = pressedGift;
            final boolean click = !dragging;
            if (dragging && velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, dp(1400));
                gift.vx = velocityTracker.getXVelocity(activePointerId);
                gift.vy = velocityTracker.getYVelocity(activePointerId);
                // Enter the nearest part of this tilted orbit, rather than snapping back to the grab point.
                final float ct = (float) Math.cos(gift.orbitTilt), st = (float) Math.sin(gift.orbitTilt);
                final float rx = Math.max(1f, orbitRadiusX * gift.orbitSize);
                final float ry = Math.max(1f, orbitRadiusY * gift.orbitHeight);
                final float dx = dragX - orbitCenterX, dy = dragY - orbitCenterY;
                gift.phase = (float) Math.atan2((-dx * st + dy * ct) / ry, (dx * ct + dy * st) / rx);
                final float cp = (float) Math.cos(gift.phase), sp = (float) Math.sin(gift.phase);
                gift.offsetX = dragX - (orbitCenterX + rx * cp * ct - ry * sp * st + dp(.6f) * musicHop * sp);
                gift.offsetY = dragY - (orbitCenterY + rx * cp * st + ry * sp * ct
                        + dp(3) * (float) Math.sin(motionTime * 1.3f + gift.phase * 2) - dp(3) * musicHop * (.85f + .15f * cp));
                final float tx = -rx * sp * ct - ry * cp * st;
                final float ty = -rx * sp * st + ry * cp * ct;
                final float measured = (gift.vx * tx + gift.vy * ty) / Math.max(1f, tx * tx + ty * ty);
                final float pulled = (float) Math.hypot(dragX - downX - grabX, dragY - downY - grabY);
                final float boost = pulled > touchSlop ? Math.min(5.5f, 1.5f + pulled / dp(65)) : 0f;
                final float direction = Math.abs(measured) > .25f ? Math.signum(measured) : 1f;
                gift.contactTime = 2.5f;
                gift.extraSpeed = direction * Math.min(7.5f, Math.max(Math.abs(measured), boost));
                gift.vx -= tx * measured;
                gift.vy -= ty * measured;
            }
            cancelGesture();
            if (click) onGiftClick(gift);
        }
        return true;
    }

    private void cancelGesture() {
        cancelAvatarGesture();
        removeCallbacks(startDragging);
        if (pressedGift != null) pressedGift.bounce.setPressed(false);
        pressedGift = null;
        dragging = false;
        activePointerId = -1;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}
