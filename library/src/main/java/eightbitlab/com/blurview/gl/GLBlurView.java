package eightbitlab.com.blurview.gl;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class GLBlurView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final int DEFAULT_BLUR_RADIUS = 8;

    private int blurRadius;
    private SizeProvider sizeProvider;
    private ViewGroup rootView;
    private BlurViewRenderer renderer;
    private GLTextureView glTextureView;
    private Drawable windowBackground;

    public GLBlurView(Context context) {
        super(context);
        init(null, 0);
    }

    public GLBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public GLBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }

    {
        glTextureView = new GLTextureView(getContext(), null, 0);
        glTextureView.setOnSurfaceTextureAvailableListener(this);
        addView(glTextureView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void init(AttributeSet attrs, int defStyleAttr) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BlurView, defStyleAttr, 0);
        blurRadius = a.getInteger(R.styleable.BlurView_blurRadius, DEFAULT_BLUR_RADIUS);
        a.recycle();
    }

    public void setRootView(@NonNull ViewGroup rootView) {
        this.rootView = rootView;
        //TODO set here to renderer too, but avoid setting it 2 times here and in onSurfaceTextureAvailable
    }

    //TODO texture not used
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        sizeProvider = new SizeProvider(width, height);
        renderer = new BlurViewRenderer(rootView, this, sizeProvider, blurRadius);
        glTextureView.setRenderer(renderer);
        renderer.setWindowBackground(this.windowBackground);
        renderer.setRootView(rootView);
        renderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    public void setWindowBackground(@Nullable Drawable windowBackground) {
        this.windowBackground = windowBackground;
    }

    public void onStart() {
        glTextureView.onStart();
    }

    public void onStop() {
        glTextureView.onStop();
    }
}
