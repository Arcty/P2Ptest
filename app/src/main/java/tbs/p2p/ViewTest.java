package tbs.p2p;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Created by Michael on 5/16/2015.
 */
public class ViewTest extends View {
    private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    static int w, h, cx, cy, tS, r;
    static float angle;
    private static final ValueAnimator a = ValueAnimator.ofFloat(0, 1);
    private ValueAnimator.AnimatorUpdateListener listener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            angle = 1096 * ((Float) valueAnimator.getAnimatedValue());
            post(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }
    };

    public ViewTest(Context context) {
        super(context);

    }

    public ViewTest(Context context, AttributeSet attrs) {
        super(context, attrs);
        a.setDuration(40000);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(listener);
        a.start();
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                a.setDuration(40000);
                a.setInterpolator(new LinearInterpolator());
                a.addUpdateListener(listener);
                if (a.isRunning()) a.cancel();
                a.start();
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        w = canvas.getWidth();
        h = canvas.getHeight();
        r = Math.round(0.82f * w) / 2;
        tS = Math.round(0.1f * w);
        cx = w / 2;
        cy = h / 2;
        p.setColor(0xffbb00ff);
        canvas.drawCircle(cx, cy, r, p);
        drawTriangle(canvas, tS, angle, cx, cy, r);
    }

    public void drawTriangle(Canvas canvas, int triangleS, float angle, int circleCX, int circleCY, int circleR) {
        angle %= 360;
        final float cosA = cos(angle);
        final float sinA = sin(angle);
        final int halfT = triangleS / 2;
        final int tx = circleCX + Math.round(cosA * circleR);
        final int ty = circleCY + Math.round(sinA * circleR);

        final int p1X = tx + Math.round(cos(angle + 90) * halfT);
        final int p1Y = ty + Math.round(sin(angle + 90) * halfT);

        final int p2X = tx + Math.round(cosA * triangleS);
        final int p2Y = ty + Math.round(sinA * triangleS);

        final int p3X = tx + Math.round(cos(angle + 270) * halfT);
        final int p3Y = ty + Math.round(sin(angle + 270) * halfT);
        path.reset();
        path.moveTo(tx, ty);
        path.lineTo(p1X, p1Y);
        path.lineTo(p2X, p2Y);
        path.lineTo(p3X, p3Y);
        path.lineTo(tx, ty);
        path.close();

        canvas.drawPath(path, p);
    }

    public static float cos(float angleDeg) {
        return (float) Math.cos(Math.toRadians(angleDeg));
    }

    public static float sin(float angleDeg) {
        return (float) Math.sin(Math.toRadians(angleDeg));
    }

    public static boolean checkCollision(int circleCX, int circleCY, int circleR, int playerR, int playerCX, int playerCY, int triangleS, float angle) {
        angle %= 360;
        final float cosA = cos(angle);
        final float sinA = sin(angle);
        final int halfT = triangleS / 2;
        final int tx = circleCX + Math.round(cosA * circleR);
        final int ty = circleCY + Math.round(sinA * circleR);
        float a = angle + 90;
        if ((sin(a) * getL(tx + Math.round(cos(a) * halfT), ty + Math.round(sin(a) * halfT), playerCX, playerCY)) <= playerR)
            return true;
        a = angle + 270;
        if (sin(a) * getL(tx + Math.round(cos(a) * halfT), ty + Math.round(sin(a) * halfT), playerCX, playerCY) <= playerR)
            return true;
        a = angle;
        if (sin(a) * getL(tx + Math.round(cosA * triangleS), ty + Math.round(sinA * triangleS), playerCX, playerCY) <= playerR)
            return true;


        return false;
    }

    private static int getL(int x1, int y1, int x2, int y2) {
        return (int) Math.round(Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)));
    }
}
