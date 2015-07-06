package com.gmail.umedutakaaki.mugenkairo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import com.gmail.umedutakaaki.geometry.Vector2D;
import com.gmail.umedutakaaki.geometry.Point2D;
import com.gmail.umedutakaaki.geometry.Range;

/**
 * Created by umedu on 2015/07/03.
 */
public class MainView extends View implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
    Application application;

    public MainView(Context context, AttributeSet attrs) {
        super(context, attrs);

        back_shader = create_transparent_shader();

        setOnTouchListener(this);
        scale_gesture_detector = new ScaleGestureDetector(context, this);
        gesture_detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                scroll_view(e1, e2, distanceX, distanceY);
                return true;
            }
        });

        maximum_image_pixels = (long)((float)Runtime.getRuntime().maxMemory() * MAXIMUM_IMAGE_MEMORY_RATIO / 4.0f);
//        maximum_image_pixels = (long)(((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() * 1024 * 1024 / (MAXIMUM_IMAGE_MEMORY_RATIO * 4));



    }
    private static float MAXIMUM_IMAGE_MEMORY_RATIO = 0.4f;
    private long maximum_image_pixels;


    private ScaleGestureDetector scale_gesture_detector;
    private GestureDetector gesture_detector;

    static final int TRANSPARENT_CHECKER_SIZE = 16;
    static final int TRANSPARENT_CHECKER_COLOR = 0xFF909090;

    private static Shader create_transparent_shader() {
        Bitmap bmp = Bitmap.createBitmap(TRANSPARENT_CHECKER_SIZE * 2, TRANSPARENT_CHECKER_SIZE * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        canvas.drawColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TRANSPARENT_CHECKER_COLOR);
        canvas.drawRect(0, 0, TRANSPARENT_CHECKER_SIZE, TRANSPARENT_CHECKER_SIZE, paint);
        canvas.drawRect(TRANSPARENT_CHECKER_SIZE, TRANSPARENT_CHECKER_SIZE, TRANSPARENT_CHECKER_SIZE * 2, TRANSPARENT_CHECKER_SIZE * 2, paint);
        return new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    Shader back_shader = null;

    final int DEFALUT_ARROWS = 2;
    final float ARROW_END = 0.4f;
    final float ARROW_START = 0.7f;
    final float ARROW_OFFSET_RADIUS = (DEFALUT_ARROWS == 2) ? 4.0f / 3.0f : 0.5f;
    final float ARROW_DELTA_RADIUS = (DEFALUT_ARROWS == 2) ? 0.33f : 2.0f / DEFALUT_ARROWS;

    final int VIEW_MODE_COMPOSED = R.id.action_composed;
    final int VIEW_MODE_MASKED = R.id.action_masked;
    final int VIEW_MODE_MASK = R.id.action_mask;

    private void initialize_arrows() {
        if (source_rect != null) {
            arrows = new ArrayList<>();
            float cx = (source_rect.left + source_rect.right) / 2.0f;
            float cy = (source_rect.top + source_rect.bottom) / 2.0f;
            float len = (source_rect.width() < source_rect.height() ? source_rect.width() : source_rect.height()) / 2.0f;

            for (int i = 0; i < DEFALUT_ARROWS; i++) {
                double r = Math.PI * (ARROW_OFFSET_RADIUS + i * ARROW_DELTA_RADIUS);
                arrows.add(make_default_arrow(cx, cy, len, r));
            }
        }
    }

    private Arrow make_default_arrow(float cx, float cy, float len, double r) {
        float sin = (float) Math.sin(r);
        float cos = (float) Math.cos(r);
        return new Arrow(cx + ARROW_START * len * cos, cy - ARROW_START * len * sin, cx + ARROW_END * len * cos, cy - ARROW_END * len * sin);
    }

    final int CIRCLE_SIZE = 50;

    final int PLUS_BUTTON_X = CIRCLE_SIZE * 2;
    final int PLUS_BUTTON_Y = CIRCLE_SIZE * 2;
    final int MINUS_BUTTON_X = CIRCLE_SIZE * 4;
    final int MINUS_BUTTON_Y = CIRCLE_SIZE * 2;
    final int PLUS_BUTTON_LINE_LENGTH = (int) (CIRCLE_SIZE * 0.8);
    final int MINUS_BUTTON_LINE_LENGTH = (int) (CIRCLE_SIZE * 0.8);
    final float LINE_WIDTH = 10.0f;
    final float LINE_WIDTH_BORDER = 18.0f;


    private static class Arrow {
        Point2D s, e;

        public Arrow(Point2D s, Point2D e) {
            this.s = s;
            this.e = e;
        }

        public Arrow(float sx, float sy, float ex, float ey) {
            s = new Point2D(sx, sy);
            e = new Point2D(ex, ey);
        }

        public float angle() {
            Vector2D v = e.sub(s);
            return (float) Math.atan2(v.y, v.x);
        }

        public float[] calc_abc(Point2D s, Point2D e) {
            float a, b, c;
            a = e.y - s.y;
            b = -e.x + s.x;
            c = e.x * s.y - s.x * e.y;
            return new float[]{a, b, c};
        }

        public Point2D calc_cross_point_with(Arrow a) {
            Point2D s1 = s, s2 = a.s;
            Point2D e1 = e, e2 = a.e;
            float[] abc1 = calc_abc(s1, e1);
            float[] abc2 = calc_abc(s2, e2);
            float a1 = abc1[0], b1 = abc1[1], c1 = abc1[2], a2 = abc2[0], b2 = abc2[1], c2 = abc2[2];

            float t1 = a1 * e1.x + b1 * e1.y + c1;
            float t2 = a1 * s1.x + b1 * s1.y + c1;
            float t3 = a2 * e2.x + b2 * e2.y + c2;
            float t4 = a2 * s2.x + b2 * s2.y + c2;

            float denom = a1 * b2 - a2 * b1;
            if (Math.abs(denom) > 0.01f) {
                return new Point2D((c2 * b1 - c1 * b2) / denom, (a2 * c1 - a1 * c2) / denom);
            } else {
                return null;
            }
        }

        public Arrow scale(float scale) {
            return new Arrow(s.scale(scale), e.scale(scale));
        }
    }

    private List<Arrow> arrows;


    private void offset_draw_arrow(Point2D s, Point2D e, Canvas canvas, Paint paint) {
        s = calc_offset_pos(s);
        e = calc_offset_pos(e);
        Vector2D v = e.sub(s);
        float len = v.length();
        v = v.div(2.0f);
        Point2D center = s.add(v);
        Point2D ls = center.sub(v.mul((len - CIRCLE_SIZE * 2) / len));
        Point2D le = center.add(v.mul((len - CIRCLE_SIZE * 2) / len));

        if (len > CIRCLE_SIZE * 2) {
            canvas.drawLine(ls.x, ls.y, le.x, le.y, paint);
        }
        Vector2D u = v.unit();
        Vector2D ur = u.rotate90();
        Point2D t1 = le.add(u.mul(CIRCLE_SIZE * 2));
        Point2D t2 = le.add(ur.mul(CIRCLE_SIZE));
        Point2D t3 = le.sub(ur.mul(CIRCLE_SIZE));
        draw_polygon(new Point2D[]{t1, t2, t3}, canvas, paint);

        canvas.drawCircle(s.x, s.y, CIRCLE_SIZE, paint);
    }


    static class DrawData {
        Point2D p;
        Vector2D v1, v2, v3;

        public DrawData(Point2D p, Vector2D v1, Vector2D v2, Vector2D v3) {
            this.p = p;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }
    }

    final static float draw_angle = (float) (Math.PI * 2 / 32);


    private static Vector2D[] calc_corner_shifted_position(Point2D p0, Point2D p1, Point2D p2) {
        Vector2D v1, v1r, v1s;
        v1 = p1.sub(p0).unit();
        Vector2D v2 = p2.sub(p1).unit();
        v1r = v1.rotate270();
        Vector2D v2r = v2.rotate270();
        v1s = v2r.add(v1r).unit();
        return new Vector2D[]{v1, v1r, v1s};
    }


    private static void draw_grad_polygon(float grad_width, Point2D center, float ratio, List<DrawData> draw_data, int col_center, int col_surrounding, Canvas canvas, Paint paint) {
        if (draw_data.size() >= 4) {
            int[] colors = new int[]{col_surrounding, col_center};
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < draw_data.size(); i++) {
                DrawData d1 = draw_data.get(i);
                DrawData d2 = draw_data.get(i < draw_data.size() - 1 ? i + 1 : 0);
                Point2D p1 = d1.p.scale(ratio, center);
                Point2D p2 = d2.p.scale(ratio, center);
                Vector2D d1s = d1.v1;
                Vector2D d2s = d2.v1;
                Vector2D v2 = d2.v2;
                Vector2D v2r = d2.v3;
                Point2D p1s = p1.add(d1s.mul(ratio));
                Point2D p2s = p2.add(d2s.mul(ratio));

                Point2D pe = p1.add(v2r.mul(grad_width * ratio));
                LinearGradient brush = new LinearGradient(p1.x, p1.y, pe.x, pe.y, colors, null, Shader.TileMode.CLAMP);
                paint.setShader(brush);
                Vector2D v2r_margin = v2r.mul(MARGIN1);
                Vector2D v2_margin = v2.mul(MARGIN2);
                draw_polygon(new Point2D[]{p1.sub(v2r_margin), p2.sub(v2r_margin).add(v2_margin), p2s.add(v2_margin), p1s}, canvas, paint);

/*
                paint.setShader(null);
                paint.setColor(0xFFFF0000);
                paint.setStyle(Paint.Style.STROKE);
                draw_polygon(new Point2D[]{p1.sub(v2r), p2.sub(v2r).add(v2), p2s.add(v2), p1s}, canvas);
*/
            }
            paint.setShader(null);
        }
    }

    final static float MARGIN1 = 1.0f;
    final static float MARGIN2 = 0.5f;

    Bitmap source_bmp = null;
    Rect source_rect = null;
    Rect canvas_rect = null;
    Rect working_rect = null;

    boolean source_rotated = false;


    public void set_target_image(Bitmap bmp) {
        if (bmp != null) {
            int source_w = bmp.getWidth(), source_h = bmp.getHeight();
            int w = getWidth(), h = getHeight();
            canvas_rect = new Rect(0, 0, w, h);
            source_rect = new Rect(0, 0, source_w, source_h);
            source_rotated = ((w > h && source_w < source_h || w < h && source_w > source_h));
            view_scale_range.min = Math.min((float) w / (float) source_w, (float) h / (float) source_h);
            view_scale_range.max = Math.max(view_scale_range.min, Math.min((float) w / MINIMUM_DOTS_IN_DISPLAY, (float) h / MINIMUM_DOTS_IN_DISPLAY));

            source_bmp = bmp;
            view_offset = new Vector2D(0, 0);

            set_view_scale(view_scale_range.min);
            initialize_arrows();
            update_draw_targets();
            control_visible = true;
            update_geometry_data();
        }
    }

    final float MINIMUM_DOTS_IN_DISPLAY = 32;

    private void update_draw_targets() {
        if (source_rect != null) {
            int w;
            int h;
            if(view_scale < 1.0f) {
                w = (int) (source_rect.width() * view_scale);
                h = (int) (source_rect.height() * view_scale);
            }else{
                w = source_rect.width();
                h = source_rect.height();
            }
            if(w * h * 2 + source_rect.width() * source_rect.height() > maximum_image_pixels){
                double scale = Math.sqrt((double)(maximum_image_pixels - source_rect.width() * source_rect.height())  / (double)(2 * w * h));
                w = (int)(w * scale);
                h = (int)(h * scale);
                Toast.makeText(this.getContext(), "resize buffer to " + w + "x" +  h + " pixels to avoid out of memory", Toast.LENGTH_SHORT).show();
            }

            working_rect = new Rect(0, 0, w, h);
            if (draw_targets != null && draw_targets[0].w == w && draw_targets[1].h == h) {
                return;
            }
            if(draw_targets != null) {
                if(draw_targets[0] != null) {
                    draw_targets[0].bmp.recycle();
                }
                if(draw_targets[1] != null) {
                    draw_targets[1].bmp.recycle();
                }
                draw_targets = null;
            }
            cur_composed = null;
            java.lang.System.gc();
            try {
                draw_targets = new DrawTarget[]{new DrawTarget(w, h), new DrawTarget(w, h)};
            }catch(RuntimeException e) {
                Log.e("ERROR", e.toString());
                Toast.makeText(this.getContext(), e.toString(), Toast.LENGTH_SHORT).show();
                draw_targets = null;
            }
        }

    }


    static class DrawTarget {
        int w, h;
        Bitmap bmp;
        Canvas canvas;

        public DrawTarget(int w, int h) {
            this.w = w;
            this.h = h;
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bmp);
            canvas.clipRect(0, 0, w, h);
        }
    }

    DrawTarget[] draw_targets;

    private static void draw_polygon(Point2D[] points, Canvas canvas, Paint paint) {
        if (points.length >= 3) {
            Path path = new Path();
            path.moveTo(points[points.length - 1].x, points[points.length - 1].y);
            for (Point2D p : points) {
                path.lineTo(p.x, p.y);
            }
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    float cur_scale;
    Point2D cur_center, cur_scaled_center;
    List<DrawData> cur_draw_data = null;
    float cur_grad_width;

    Bitmap cur_composed = null;

    private void update_geometry_data() {
        cur_center = null;
        cur_draw_data = null;
        cur_scale = 1.0f;
        float calc_scale = (float) working_rect.width() / (float) source_rect.width();
        ArrayList<ArrowWithAngle> sorted_arrows = sort_arrows(arrows);
        if (sorted_arrows == null) {
            return;
        }
        cur_center = calc_center(sorted_arrows);
        if (cur_center == null) {
            return;
        }
        cur_scale = calc_scale(cur_center, arrows);
        cur_scaled_center = cur_center.scale(calc_scale);
        calc_draw_data(calc_scale, sorted_arrows);

        cur_composed = null;
        invalidate();
    }

    private static ArrayList<ArrowWithAngle> sort_arrows(List<Arrow> arrows) {
        if (arrows == null || arrows.size() < 2) {
            return null;
        }

        ArrayList<ArrowWithAngle> sorted_arrows = new ArrayList<ArrowWithAngle>();
        for (Arrow a : arrows) {
            sorted_arrows.add(new ArrowWithAngle(a, a.angle() - (float) Math.PI));
        }
        java.util.Collections.sort(sorted_arrows, new java.util.Comparator<ArrowWithAngle>() {
            public int compare(ArrowWithAngle o1, ArrowWithAngle o2) {
                if (o1.t > o2.t) {
                    return 1;
                } else if (o1.t < o2.t) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        return sorted_arrows;
    }

    private static Point2D calc_center(ArrayList<ArrowWithAngle> sorted_arrows) {
        if (sorted_arrows == null) {
            return null;
        }
        List<Point2D> center_cands = new ArrayList<Point2D>();
        Arrow arrow_s = sorted_arrows.get(sorted_arrows.size() - 1).a;
        for (ArrowWithAngle item : sorted_arrows) {
            Point2D p = item.a.calc_cross_point_with(arrow_s);
            if (p != null) {
                center_cands.add(p);
            }
            arrow_s = item.a;
        }
        if (center_cands.size() > 0) {
            float sum_x = 0, sum_y = 0;
            for (Point2D item : center_cands) {
                sum_x += item.x;
                sum_y += item.y;
            }
            return new Point2D(sum_x / center_cands.size(), sum_y / center_cands.size());
        } else {
            return null;
        }

    }


    private void calc_draw_data(float calc_scale, ArrayList<ArrowWithAngle> sorted_arrows) {
        float default_grad_width = calc_grad_width();
        ArrayList<Point2D> points = new ArrayList<Point2D>();
        Arrow arrow_s = sorted_arrows.get(sorted_arrows.size() - 1).a;
        float angle_s = sorted_arrows.get(sorted_arrows.size() - 1).t - (float) (Math.PI * 2);
        float grad_width;
        for (ArrowWithAngle item : sorted_arrows) {
            Arrow arrow_e = item.a;
            float angle_e = item.t;
            float angle_s_tmp = angle_s;
            float len_s = (arrow_s.e.sub(cur_center)).length();
            float len_e = (arrow_e.e.sub(cur_center)).length();
            do {
                float len = len_s + (len_e - len_s) * (angle_s_tmp - angle_s) / (angle_e - angle_s);
                points.add(Point2D.point_on_circle(cur_center, len, angle_s_tmp).scale(calc_scale));
                angle_s_tmp += draw_angle;
            } while (angle_s_tmp < angle_e);
            arrow_s = arrow_e;
            angle_s = angle_e;
        }
        boolean modifying = false;
        ArrayList<DrawData> draw_data = null;
        do {
            float len_min = -1;
            for (Point2D p : points) {
                float len = p.sub(cur_scaled_center).length();
                if (len_min < 0 || len_min > len) {
                    len_min = len;
                }
            }
            grad_width = len_min < default_grad_width ? len_min : default_grad_width;

            modifying = false;
            if (points.size() <= 4) {
                break;
            }
            for (int i = 0; i < points.size(); i++) {
                Point2D p0 = points.get(i);
                Point2D p1 = points.get(i < points.size() - 1 ? i + 1 : 0);
                if ((p1.sub(p0)).length2() == 0.0) {
                    modifying = true;
                    points.remove(i);
                    break;
                }
            }
            if (!modifying && points.size() > 4) {
                draw_data = new ArrayList<DrawData>();
                Point2D p0 = points.get(points.size() - 3);
                Point2D p1 = points.get(points.size() - 2);
                Point2D p2 = points.get(points.size() - 1);

                Vector2D[] tmp = calc_corner_shifted_position(p0, p1, p2);
                Vector2D v1 = tmp[0], v1r = tmp[1], d1s = tmp[2];
                Point2D p1s = p1.add(d1s.mul(grad_width));

                for (int i = 0; i < points.size(); i++) {
                    Point2D p3 = points.get(i);
                    tmp = calc_corner_shifted_position(p1, p2, p3);
                    Vector2D v2 = tmp[0], v2r = tmp[1], v2s = tmp[2];
                    Point2D p2s = p2.add(v2s.mul(grad_width));

                    if (p2s.sub(p1s).multiply(v2) < 0) {
                        points.set((i + points.size() - 2) % points.size(), p1.add(p2.sub(p1).div(2.0f)));
                        points.remove((i + points.size() - 1) % points.size());
                        modifying = true;
                        break;
                    }
                    draw_data.add(new DrawData(p2, v2s.mul(grad_width), v2, v2r));
                    p0 = p1;
                    p1 = p2;
                    p2 = p3;
                    v1 = v2;
                    v1r = v2r;
                    p1s = p2s;
                }
            }
        } while (modifying);
        if (draw_data != null && draw_data.size() > 3) {
            cur_draw_data = draw_data;
            cur_grad_width = grad_width;
        } else {
            cur_draw_data = null;
        }
    }


    private static float calc_scale(Point2D center, List<Arrow> arrows) {
        if (arrows.size() >= 2) {
            float range_far = 0.0f;
            float range_near = 0.0f;
            for (Arrow arrow : arrows) {
                range_far += (arrow.e.sub(center)).length();
                range_near += (arrow.s.sub(center)).length();
            }
            range_far /= arrows.size();
            range_near /= arrows.size();
            if (range_near > 0.001) {
                return range_far / range_near;
            }
        }
        return -1.0f;
    }

    final float default_grad_width_ratio = 0.1f;

    final static int REPEAT_COUNT = 5;

    public Bitmap compose_result(boolean for_save) {
        if (cur_center != null) {
            Bitmap composed = compose_image(for_save);
            if (source_rotated) {
                Matrix m = new Matrix();
                m.setRotate(-90);
                return Bitmap.createBitmap(composed, 0, 0, composed.getWidth(), composed.getHeight(), m, false);
            } else {
                return composed;
            }
        } else {
            return null;
        }
    }

    private float calc_grad_width() {
        return default_grad_width_ratio * Math.min(working_rect.width(), working_rect.height());
    }

    private Bitmap compose_image(boolean for_save) {
        if (cur_center != null && source_bmp != null && draw_targets != null) {
            paint.reset();
            DrawTarget mask = draw_targets[0];
            DrawTarget working = draw_targets[1];
            if (cur_draw_data != null) {
                draw_mask(mask, working, for_save);
            } else {
                mask.canvas.drawColor(0xFFFFFFFF);
            }
            float border_grad_width = calc_grad_width();
            Rect cut_rect = new Rect((int) border_grad_width, (int) border_grad_width, (int) (working_rect.right - border_grad_width), (int) (working_rect.bottom - border_grad_width));
            draw_cut_border_mask(working_rect, cut_rect, working.canvas);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mask.canvas.drawBitmap(working.bmp, 0, 0, paint);
            paint.setXfermode(null);

            if (view_mode == VIEW_MODE_MASK) {
                return mask.bmp;
            } else {
                Matrix copy_matrix = new Matrix();
                float calc_scale = (float) working_rect.width() / (float) source_rect.width();
                copy_matrix.setScale(calc_scale, calc_scale);
                working.canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
                working.canvas.drawBitmap(source_bmp, copy_matrix, paint);


                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
                working.canvas.drawBitmap(mask.bmp, 0, 0, paint);
                paint.setXfermode(null);

                if (view_mode != VIEW_MODE_MASKED) {
                    paint.setStyle(Paint.Style.FILL);
                    Matrix matrix = new Matrix();

                    float tmp_scale = cur_scale;
                    DrawTarget src = null;
                    DrawTarget dst = null;
                    for (int i = 0; i < REPEAT_COUNT; i++) {
                        src = draw_targets[(i + 1) % 2];
                        dst = draw_targets[i % 2];
                        dst.canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
                        matrix.setScale(tmp_scale, tmp_scale, cur_scaled_center.x, cur_scaled_center.y);
                        dst.canvas.drawBitmap(src.bmp, matrix, paint);
                        dst.canvas.drawBitmap(src.bmp, 0, 0, paint);
                        tmp_scale *= tmp_scale;
                        if (tmp_scale == 0.0f) {
                            break;
                        }
                    }
                    src.canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
                    src.canvas.drawBitmap(source_bmp, copy_matrix, paint);
                    src.canvas.drawBitmap(dst.bmp, 0, 0, paint);
                    return src.bmp;
                } else {
                    return working.bmp;
                }
            }
        }

        return null;
    }

    Paint paint = new Paint();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (arrows == null) {
            initialize_arrows();
        }

        paint.reset();
        if (canvas_rect != null) {
            paint.setShader(back_shader);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(canvas_rect, paint);
            paint.setShader(null);
        } else {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(32);
            canvas.drawText(this.getContext().getString(R.string.initial_message), canvas.getWidth() / 2, canvas.getHeight() / 2, paint);
            return;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFF0000);
        paint.setStrokeWidth(LINE_WIDTH);

        if (arrows != null) {
            if (cur_center != null) {

                if (cur_composed == null) {
                    cur_composed = compose_image(false);
                }
                if (cur_composed != null) {
                    Matrix offset = new Matrix();
                    float draw_scale = source_rect.width() * view_scale / working_rect.width();
                    offset.setScale(draw_scale, draw_scale);
                    offset.postTranslate(view_offset.x, view_offset.y);
                    canvas.drawBitmap(cur_composed, offset, paint);
                }

                if (control_visible) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFFFFFF00);
                    Point2D lt = cur_center.sub(new Vector2D(1, 1).mul(CIRCLE_SIZE));
                    Point2D rb = cur_center.add(new Vector2D(1, 1).mul(CIRCLE_SIZE));
//                    canvas.drawCircle(center.x + offset.x, center.y + offset.y, CIRCLE_SIZE, paint);

                    paint.setStrokeWidth(LINE_WIDTH_BORDER);
                    paint.setColor(0x80000000);
                    float line_offset = (LINE_WIDTH_BORDER - LINE_WIDTH) / 2.0f;
                    offset_draw_line(lt.x - line_offset, lt.y - line_offset, rb.x + line_offset, rb.y + line_offset, canvas, paint);
                    offset_draw_line(lt.x - line_offset, rb.y + line_offset, rb.x + line_offset, lt.y - line_offset, canvas, paint);
                    paint.setStrokeWidth(LINE_WIDTH);
                    paint.setColor(0xFFFFFF00);
                    offset_draw_line(lt.x, lt.y, rb.x, rb.y, canvas, paint);
                    offset_draw_line(lt.x, rb.y, rb.x, lt.y, canvas, paint);
                }
            }

            if (control_visible) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setAntiAlias(true);
                for (Arrow a : arrows) {

                    paint.setStrokeWidth(LINE_WIDTH_BORDER);
                    paint.setColor(0x80000000);
                    offset_draw_arrow(a.s, a.e, canvas, paint);

                    paint.setStrokeWidth(LINE_WIDTH);
                    if (a == selected) {
                        paint.setColor(0x80FF0000);
                    } else {
                        paint.setColor(0x8000FF00);
                    }
                    offset_draw_arrow(a.s, a.e, canvas, paint);
                }
            }
        }
/*
        canvas.drawCircle(PLUS_BUTTON_X, PLUS_BUTTON_Y, CIRCLE_SIZE, paint);
        canvas.drawLine(PLUS_BUTTON_X - PLUS_BUTTON_LINE_LENGTH / 2, PLUS_BUTTON_Y, PLUS_BUTTON_X + PLUS_BUTTON_LINE_LENGTH / 2, PLUS_BUTTON_Y, paint);
        canvas.drawLine(PLUS_BUTTON_X, PLUS_BUTTON_Y - PLUS_BUTTON_LINE_LENGTH / 2, PLUS_BUTTON_X, PLUS_BUTTON_Y + PLUS_BUTTON_LINE_LENGTH / 2, paint);

        canvas.drawCircle(MINUS_BUTTON_X, MINUS_BUTTON_Y, CIRCLE_SIZE, paint);
        canvas.drawLine(MINUS_BUTTON_X - MINUS_BUTTON_LINE_LENGTH / 2, MINUS_BUTTON_Y, MINUS_BUTTON_X + MINUS_BUTTON_LINE_LENGTH / 2, MINUS_BUTTON_Y, paint);
*/

    }

    private Point2D calc_offset_pos(Point2D pos) {
        if (view_offset != null) {
            return pos.scale(view_scale).add(view_offset);
        } else {
            return pos.scale(view_scale);
        }
    }

    private void offset_draw_line(float p1x, float p1y, float p2x, float p2y, Canvas canvas, Paint paint) {
        offset_draw_line(new Point2D(p1x, p1y), new Point2D(p2x, p2y), canvas, paint);
    }

    private void offset_draw_line(Point2D p1, Point2D p2, Canvas canvas, Paint paint) {
        p1 = calc_offset_pos(p1);
        p2 = calc_offset_pos(p2);
        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
    }

    private Point2D[] rect_to_points(Rect rect) {
        return new Point2D[]{
                new Point2D(rect.left, rect.top),
                new Point2D(rect.right, rect.top),
                new Point2D(rect.right, rect.bottom),
                new Point2D(rect.left, rect.bottom),
        };
    }

    private void draw_cut_border_mask(Rect image_rect, Rect cut_rect, Canvas canvas) {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        Point2D[] points = rect_to_points(image_rect);
        Point2D[] cut_points = rect_to_points(cut_rect);
        Vector2D[] ds = new Vector2D[]{new Vector2D(1, 0), new Vector2D(0, 1), new Vector2D(-1, 0), new Vector2D(0, -1)};
        float[] ls = new float[]{cut_rect.left, cut_rect.top, image_rect.width() - cut_rect.right, image_rect.height() - cut_rect.bottom};
        Point2D p0 = points[3];
        Point2D c0 = cut_points[3];
        Vector2D d0 = ds[3];
        int[] colors = {0x00FFFFFF, 0xFFFFFFFF};
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            Point2D p1 = points[i];
            Point2D c1 = cut_points[i];
            Vector2D d1 = ds[i];
//            Point2D brush = new LinearGradientBrush(Colors.Black, Colors.White, p0, p0 + d1 *  ls[i]);
            Point2D pe = p0.add(d1.mul(ls[i]));
            LinearGradient brush = new LinearGradient(p0.x, p0.y, pe.x, pe.y, colors, null, Shader.TileMode.CLAMP);
//            brush.MappingMode = BrushMappingMode.Absolute;
//            context.DrawGeometry(brush, null, points_to_geometry(false, p0 - d0, p1, c1, c0));
            paint.setShader(brush);
            draw_polygon(new Point2D[]{p0.sub(d0), p1, c1, c0}, canvas, paint);
            p0 = p1;
            c0 = c1;
            d0 = d1;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(null);
        draw_polygon(cut_points, canvas, paint);
//        draw_polygon(new Point2D[]{new Point2D(0, 0), new Point2D(100, 100), new Point2D(0, 300)}, canvas, paint);
//        draw_polygon(new Point2D[]{cut_points[0], cut_points[1], cut_points[2]}, canvas, paint);
    }

    int view_mode = VIEW_MODE_COMPOSED;

    public void set_view_mode(int mode) {
        view_mode = mode;
        cur_composed = null;
        this.invalidate();
    }

    public int get_view_mode() {
        return view_mode;
    }

    public int arrow_count() {
        if (arrows != null) {
            return arrows.size();
        } else {
            return 0;
        }
    }

    public void add_arrow() {
        if (source_rect == null || arrows == null || arrows.size() < 2) {
            return;
        }
        Point2D center = cur_center;
        if (center == null) {
            center = new Point2D(source_rect.width() / 2, source_rect.height() / 2);
        }
        Arrow last_arrow = arrows.get(arrows.size() - 1);
        float r = last_arrow.angle() + (float) (Math.PI * 2 / (arrows.size() + 1));
        float len = (source_rect.width() < source_rect.height() ? source_rect.width() : source_rect.height()) / 2.0f;
        arrows.add(make_default_arrow(center.x, center.y, len, r));


        update_geometry_data();
    }

    public void del_arrow() {
        if (arrows.size() > 2) {
            arrows.remove(arrows.size() - 1);
            update_geometry_data();
        }
    }

    private void draw_mask(DrawTarget mask, DrawTarget working, boolean for_save) {
        float ratio = 1.0f;
        boolean modifying;
        mask.canvas.drawColor(0xFFFFFFFF);

        Point2D[] center_polygon = new Point2D[cur_draw_data.size()];
        for (int i = 0; i < cur_draw_data.size(); i++) {
            DrawData data = cur_draw_data.get(i);
            center_polygon[i] = data.p.add(data.v2);
        }
        paint.setColor(0x00000000);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        paint.setStyle(Paint.Style.FILL);
        draw_polygon(center_polygon, mask.canvas, paint);
        paint.setXfermode(null);

        draw_grad_polygon(cur_grad_width, cur_scaled_center, 1, cur_draw_data, 0x00FFFFFF, 0xFFFFFFFF, mask.canvas, paint);

        if (view_mode == VIEW_MODE_MASKED && for_save == false) {
            working.canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            for (int i = 0; i < cur_draw_data.size(); i++) {
                DrawData data = cur_draw_data.get(i);
                center_polygon[i] = data.p.add(data.v1).scale(1.0f / cur_scale, cur_scaled_center);
            }
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            draw_polygon(center_polygon, working.canvas, paint);

            draw_grad_polygon(cur_grad_width, cur_scaled_center, 1.0f / cur_scale, cur_draw_data, 0xFFFFFFFF, 0x00FFFFFF, working.canvas, paint);

            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));

            mask.canvas.drawBitmap(working.bmp, 0, 0, paint);
            paint.setXfermode(null);
        }

    }


    private float is_touching_circle(Point2D p1, Point2D p2) {
        Vector2D d = p1.sub(p2);
        float len2 = d.length2();
        if (len2 < CIRCLE_SIZE * CIRCLE_SIZE * TOUCH_MARGIN * TOUCH_MARGIN) {
            return len2;
        }
        return -1;
    }

    private static final float TOUCH_MARGIN = 1.5f;

    private boolean get_touching_arrow(Point2D pos) {
        float min = CIRCLE_SIZE * CIRCLE_SIZE * 2;
        boolean found = false;
        for (Arrow a : arrows) {
            float len2;
            len2 = is_touching_circle(a.s, pos);
            if (len2 >= 0.0 && len2 < min) {
                selected = a;
                selected_start_point = true;
                selected_original_position = a.s;
                min = len2;
                found = true;
            }
            len2 = is_touching_circle(a.e, pos);
            if (len2 >= 0.0 && len2 < min) {
                selected = a;
                selected_start_point = false;
                selected_original_position = a.e;
                min = len2;
                found = true;
            }
        }
        return found;
    }

    Arrow selected = null;
    Point2D selected_original_position = null;
    boolean selected_start_point = false;
    Vector2D selected_offset;

    public boolean onTouch(View view, MotionEvent event) {
        scale_gesture_detector.onTouchEvent(event);
        gesture_detector.onTouchEvent(event);
        if (view_offset != null) {
            Point2D pos = new Point2D(event.getX(), event.getY()).sub(view_offset).scale(1.0f / view_scale);

            if (!view_scaling) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (control_visible) {
                            if (get_touching_arrow(pos)) {
                                Point2D p = selected_start_point ? selected.s : selected.e;
                                selected_offset = p.sub(pos);
                                invalidate();
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (selected != null) {
                            selected = null;
                            invalidate();
                        } else if (event.getEventTime() - event.getDownTime() < TAP_TIMEOUT) {
                            control_visible = !control_visible;
                            invalidate();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (selected != null) {
                            if (selected_start_point) {
                                selected.s = pos.add(selected_offset);
                            } else {
                                selected.e = pos.add(selected_offset);
                            }
                            update_geometry_data();
                        }
                        break;
                }
            }
        }
        return true;
    }

    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private boolean control_visible = true;

    private static class ArrowWithAngle {
        Arrow a;
        float t;

        public ArrowWithAngle(Arrow a, float t) {
            this.a = a;
            this.t = t;
        }
    }

    float view_scale;
    Range view_scale_range = new Range(1.0f);
    Vector2D view_offset;
    Range view_offset_range_x = new Range(0.0f);
    Range view_offset_range_y = new Range(0.0f);

    public float get_view_scale() {
        return view_scale;
    }

    public void scroll_view(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if(selected != null) {
            return;
        }
//        Vector2D d = new Vector2D(-distanceX, -distanceY).mul(view_scale);
        Vector2D d = new Vector2D(-distanceX, -distanceY);
        view_offset = new Vector2D(view_offset_range_x.clamp(view_offset.x + d.x), view_offset_range_y.clamp(view_offset.y + d.y));

        this.invalidate();
    }

    public void change_view_scale(float scale, float center_x, float center_y) {
        float prev_view_scale = view_scale;
        set_view_scale(view_scale * scale);
        Point2D center = new Point2D(center_x, center_y);
        float view_offset_x = (view_offset.x - center.x) * (view_scale / prev_view_scale) + center.x;
        float view_offset_y = (view_offset.y - center.y) * (view_scale / prev_view_scale) + center.y;
        view_offset = new Vector2D(view_offset_range_x.clamp(view_offset_x), view_offset_range_y.clamp(view_offset_y));

        this.invalidate();
    }

    private void set_view_scale(float scale) {
        if (source_rect == null || canvas_rect == null) {
            return;
        }
        scale = view_scale_range.clamp(scale);
        if (scale != view_scale) {
            view_scale = scale;
            float margin_x = source_rect.width() * view_scale - canvas_rect.width();
            float margin_y = source_rect.height() * view_scale - canvas_rect.height();
            if (margin_x < 0) {
                view_offset_range_x.min = view_offset_range_x.max = -margin_x / 2.0f;
            } else {
                view_offset_range_x.min = -margin_x;
                view_offset_range_x.max = 0;
            }
            if (margin_y < 0) {
                view_offset_range_y.min = view_offset_range_y.max = -margin_y / 2.0f;
            } else {
                view_offset_range_y.min = -margin_y;
                view_offset_range_y.max = 0;
            }
            view_offset = new Vector2D(view_offset_range_x.clamp(view_offset.x), view_offset_range_y.clamp(view_offset.y));

        }
    }

    boolean view_scaling = false;
    float view_scaling_initial_scale = 0;

    private void cansel_arrow_move() {
        if (selected != null) {
            if (selected_start_point) {
                selected.s = selected_original_position;
            } else {
                selected.e = selected_original_position;
            }
            selected = null;
        }
    }

    public boolean onScaleBegin(ScaleGestureDetector detector) {
        cansel_arrow_move();
        view_scaling = true;
        view_scaling_initial_scale = view_scale;
        return true;
    }

    public boolean onScale(ScaleGestureDetector detector) {
        cansel_arrow_move();
        if (view_scaling) {
            change_view_scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
        }
        return true;
    }

    public void onScaleEnd(ScaleGestureDetector detector) {
        cansel_arrow_move();
        if (view_scaling) {
            view_scaling = false;
            cur_composed = null;
            update_draw_targets();
            update_geometry_data();
        }

    }
}
