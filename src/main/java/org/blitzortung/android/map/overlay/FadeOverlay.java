package org.blitzortung.android.map.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.inject.Inject;
import org.blitzortung.android.map.color.ColorHandler;

public class FadeOverlay extends Overlay {

    private final ColorHandler colorHandler;

    private int alphaValue = 0;

    @Inject
    public FadeOverlay(ColorHandler colorHandler) {
        this.colorHandler = colorHandler;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (!shadow) {
            Rect rect = canvas.getClipBounds();
            Paint paint = new Paint();
            paint.setColor(colorHandler.getBackgroundColor());
            paint.setAlpha(alphaValue);
            canvas.drawRect(rect, paint);
        }
    }

    public void setAlpha(int alphaValue) {
        this.alphaValue = alphaValue;
    }
}
