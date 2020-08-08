package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * Blur Controller that handles all blur logic for the attached View.
 * It honors View size changes, View animation and Visibility changes.
 * <p>
 * The basic idea is to draw the view hierarchy on a bitmap, excluding the attached View,
 * then blur and draw it on the system Canvas.
 * <p>
 * It uses {@link ViewTreeObserver.OnPreDrawListener} to detect when
 * blur should be updated.
 * <p>
 * Blur is done on the main thread.
 */
final class BlockingBlurController implements BlurController {

    @ColorInt
    static final int TRANSPARENT = 0;

    private BlurAlgorithm blurAlgorithm;
    private Canvas internalCanvas;
    private Bitmap internalBitmap;

    @SuppressWarnings("WeakerAccess")
    final View blurView;
    private float blurRadius;
    private int overlayColor;
    private final ViewGroup rootView;
    private final int[] rootLocation = new int[2];
    private final int[] blurViewLocation = new int[2];
    private final SizeScaler sizeScaler = new SizeScaler();

    private final ViewTreeObserver.OnPreDrawListener drawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            // Not invalidating a View here, just updating the Bitmap.
            // This relies on the HW accelerated bitmap drawing behavior in Android
            // If the bitmap was drawn on HW accelerated canvas, it holds a reference to it and on next
            // drawing pass the updated content of the bitmap will be rendered on the screen

            updateBlur();
            return true;
        }
    };

    private boolean blurEnabled = true;
    private boolean initialized;

    @Nullable
    private Drawable frameClearDrawable;
    private boolean hasFixedTransformationMatrix;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /**
     * @param blurView View which will draw it's blurred underlying content
     * @param rootView Root View where blurView's underlying content starts drawing.
     *                 Can be Activity's root content layout (android.R.id.content)
     *                 or some of your custom root layouts.
     */
    BlockingBlurController(@NonNull View blurView, @NonNull ViewGroup rootView, @ColorInt int overlayColor,
                           float blurRadius) {
        this.rootView = rootView;
        this.blurView = blurView;
        this.blurRadius = blurRadius;
        this.overlayColor = overlayColor;
        this.blurAlgorithm = new NoOpBlurAlgorithm();

        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            deferBitmapCreation();
            return;
        }

        init(measuredWidth, measuredHeight);
    }

    @SuppressWarnings("WeakerAccess")
    void init(int measuredWidth, int measuredHeight) {
        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            blurView.setWillNotDraw(true);
            return;
        }

        blurView.setWillNotDraw(false);
        allocateBitmap(measuredWidth, measuredHeight);
        internalCanvas = new Canvas(internalBitmap);
        initialized = true;

        if (hasFixedTransformationMatrix) {
            setupInternalCanvasMatrix();
        }
    }

    @SuppressWarnings("WeakerAccess")
    void updateBlur() {
        if (!blurEnabled || !initialized) {
            return;
        }

        if (frameClearDrawable == null) {
            internalBitmap.eraseColor(Color.TRANSPARENT);
        } else {
            frameClearDrawable.draw(internalCanvas);
        }

        if (hasFixedTransformationMatrix) {
            rootView.draw(internalCanvas);
        } else {
            internalCanvas.save();
            setupInternalCanvasMatrix();
            rootView.draw(internalCanvas);
            internalCanvas.restore();
        }

        blurAndSave();
    }

    /**
     * Deferring initialization until view is laid out
     */
    private void deferBitmapCreation() {
        blurView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    blurView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    legacyRemoveOnGlobalLayoutListener();
                }

                int measuredWidth = blurView.getMeasuredWidth();
                int measuredHeight = blurView.getMeasuredHeight();

                init(measuredWidth, measuredHeight);
            }

            @SuppressWarnings("deprecation")
            void legacyRemoveOnGlobalLayoutListener() {
                blurView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });
    }

    private void allocateBitmap(int measuredWidth, int measuredHeight) {
        SizeScaler.Size bitmapSize = sizeScaler.roundSize(measuredWidth, measuredHeight);
        internalBitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, blurAlgorithm.getSupportedBitmapConfig());
    }

    /**
     * Set up matrix to draw starting from blurView's position
     */
    private void setupInternalCanvasMatrix() {
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);

        int left = blurViewLocation[0] - rootLocation[0];
        int top = blurViewLocation[1] - rootLocation[1];

        float scaleFactor = sizeScaler.scaleFactor();

        float scaledLeftPosition = -left / scaleFactor;
        float scaledTopPosition = -top / scaleFactor;

        internalCanvas.translate(scaledLeftPosition, scaledTopPosition);
        internalCanvas.scale(1 / scaleFactor, 1 / scaleFactor);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!blurEnabled || !initialized) {
            return true;
        }
        // Not blurring own children
        if (canvas == internalCanvas) {
            return false;
        }

        updateBlur();

        canvas.save();
        canvas.scale(sizeScaler.scaleFactor(), sizeScaler.scaleFactor());
        canvas.drawBitmap(internalBitmap, 0, 0, paint);
        canvas.restore();

        if (overlayColor != TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        return true;
    }

    private void blurAndSave() {
        internalBitmap = blurAlgorithm.blur(internalBitmap, blurRadius);
        if (!blurAlgorithm.canModifyBitmap()) {
            internalCanvas.setBitmap(internalBitmap);
        }
    }

    @Override
    public void updateBlurViewSize() {
        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        init(measuredWidth, measuredHeight);
    }

    @Override
    public void destroy() {
        setBlurAutoUpdate(false);
        blurAlgorithm.destroy();
        initialized = false;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        return this;
    }

    @Override
    public BlurViewFacade setBlurAlgorithm(BlurAlgorithm algorithm) {
        this.blurAlgorithm = algorithm;
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        setBlurAutoUpdate(enabled);
        blurView.invalidate();
        return this;
    }

    public BlurViewFacade setBlurAutoUpdate(final boolean enabled) {
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
        }
        return this;
    }

    @Override
    public BlurViewFacade setHasFixedTransformationMatrix(boolean hasFixedTransformationMatrix) {
        this.hasFixedTransformationMatrix = hasFixedTransformationMatrix;
        return this;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }
}
