package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();

    private final RenderNode renderNode;
    private final RenderNode renderNodeFill;

    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean renderNodeInvalidated;
    private boolean fillInvalidated;

    public BlurredBackgroundDrawableRenderNode(BlurredBackgroundSource source) {
        this.renderNode = new RenderNode("BlurredNode");
        this.renderNodeFill = new RenderNode("BlurredFill");
        this.renderNode.setClipToOutline(true);
        this.renderNode.setClipToBounds(true);

        this.source = source;

        this.paintShadow.setColor(0);
        this.paintStrokeTop.setStyle(Paint.Style.STROKE);
        this.paintStrokeBottom.setStyle(Paint.Style.STROKE);
    }

    @Override
    public BlurredBackgroundDrawable setClipToOutline(boolean clipToOutline) {
        renderNode.setClipToOutline(clipToOutline);
        return super.setClipToOutline(clipToOutline);
    }

    private LiquidGlassEffect liquidGlassEffect;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void setLiquidGlassEffectAllowed() {
        if (liquidGlassEffect == null) {
            liquidGlassEffect = new LiquidGlassEffect(renderNodeFill);
            renderNodeInvalidated = true;
        }
    }


    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }

    @Override
    protected void onBoundPropsChanged() {
        super.onBoundPropsChanged();

        paintStrokeTop.setStrokeWidth(boundProps.strokeWidthTop);
        paintStrokeBottom.setStrokeWidth(boundProps.strokeWidthBottom);

        outlineRect.set(0, 0,
            boundProps.boundsWithPadding.width(),
            boundProps.boundsWithPadding.height()
        );
        getOutline(outline, outlineRect, boundProps.radii);
        outline.setAlpha(1);

        if (!boundProps.boundsWithPadding.isEmpty()) {
            renderNodeFill.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setOutline(outline);

            renderNodeInvalidated = true;
        }
    }

    @Override
    protected void onSourceOffsetChange(float sourceOffsetX, float sourceOffsetY) {
        super.onSourceOffsetChange(sourceOffsetX, sourceOffsetY);
        fillInvalidated = true;
    }

    @Override
    public boolean hasDisplayList() {
        return renderNode.hasDisplayList();
    }

    @Override
    public void updateDisplayList() {
        final boolean updateDecoration = renderNodeInvalidated || !renderNode.hasDisplayList();
        final boolean opaque = Color.alpha(backgroundColor) == 255;

        // The source changes during scrolling; the outline and strokes usually do not.
        // A recorded drawRenderNode keeps referencing the live child display list.
        if (!opaque && (fillInvalidated || updateDecoration || !renderNodeFill.hasDisplayList())) {
            final float sL = boundProps.boundsWithPadding.left + sourceOffsetX;
            final float sT = boundProps.boundsWithPadding.top + sourceOffsetY;
            final float sR = boundProps.boundsWithPadding.right + sourceOffsetX;
            final float sB = boundProps.boundsWithPadding.bottom + sourceOffsetY;

            Canvas c = renderNodeFill.beginRecording();
            try {
                c.save();
                c.translate(-sL, -sT);
                if (liquidGlassEffect != null && Build.VERSION.SDK_INT >= 33) {
                    final int thickness = Math.max(Math.min(
                        boundProps.liquidThickness <= 0 ? dp(11) : boundProps.liquidThickness,
                        Math.min(boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height()) / 5), 1);

                    liquidGlassEffect.update(
                        0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height(),
                        boundProps.shaderRadii[0], boundProps.shaderRadii[2], boundProps.shaderRadii[4], boundProps.shaderRadii[6],
                        thickness,
                        boundProps.liquidIntensity,
                        boundProps.liquidIndex,
                        backgroundColor
                    );
                }
                source.draw(c, sL, sT, sR, sB);
                c.restore();
            } finally {
                renderNodeFill.endRecording();
            }
        }

        if (updateDecoration) {
            Canvas c = renderNode.beginRecording();
            try {
                if (opaque) {
                    c.drawColor(backgroundColor);
                } else {
                    c.drawRenderNode(renderNodeFill);
                    if (liquidGlassEffect == null && Color.alpha(backgroundColor) != 0) {
                        c.drawColor(backgroundColor);
                    }
                }
                if (strokeColorTop != 0) {
                    drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                            boundProps.boundsWithPadding.height(), boundProps.radii,
                            boundProps.strokeWidthTop, true, paintStrokeTop);
                }
                if (strokeColorBottom != 0) {
                    drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                            boundProps.boundsWithPadding.height(), boundProps.radii,
                            boundProps.strokeWidthBottom, false, paintStrokeBottom);
                }
            } finally {
                renderNode.endRecording();
            }
        }
        renderNodeInvalidated = false;
        fillInvalidated = false;
    }

    @Override
    public void updateColors() {
        final int oldBackgroundColor = backgroundColor;
        final int oldStrokeColorTop = strokeColorTop;
        final int oldStrokeColorBottom = strokeColorBottom;
        super.updateColors();

        paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, shadowColor);
        paintStrokeTop.setColor(strokeColorTop);
        paintStrokeBottom.setColor(strokeColorBottom);

        renderNodeInvalidated |= oldBackgroundColor != backgroundColor
                || oldStrokeColorTop != strokeColorTop || oldStrokeColorBottom != strokeColorBottom;
        // A provider update can also accompany a new wallpaper with the same tint colors.
        fillInvalidated = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundProps.boundsWithPadding.isEmpty()) {
            return;
        }

        if (!canvas.isHardwareAccelerated()) {
            drawSource(canvas, source);
            return;
        }

        if (!renderNode.hasDisplayList()) {
            source.dispatchOnDrawablesRelativePositionChange();
            updateDisplayList();
        } else if (renderNodeInvalidated || fillInvalidated
                || (Color.alpha(backgroundColor) != 255 && !renderNodeFill.hasDisplayList())) {
            updateDisplayList();
        }

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha() * shadowAlpha);
        if (Color.alpha(color) != 0) {
            paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, color);
            boundProps.drawShadows(canvas, paintShadow, inAppKeyboardOptimization);
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public void invalidateDisplayList() {
        fillInvalidated = true;
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = getAlpha();
        if (oldAlpha == alpha) {
            return;
        }

        super.setAlpha(alpha);
        // Alpha is a RenderNode property, so fading does not require re-recording its content.
        renderNode.setAlpha(alpha / 255f);

        if (oldAlpha == 0 && alpha > 0) {
            source.dispatchOnDrawablesRelativePositionChange();
        }
    }

    @Override
    protected void onSourceRelativePositionChanged(RectF position) {
        super.onSourceRelativePositionChanged(position);
        source.dispatchOnDrawablesRelativePositionChange();
    }
}
