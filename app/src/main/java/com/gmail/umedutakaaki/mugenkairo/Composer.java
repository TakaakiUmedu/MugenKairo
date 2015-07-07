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
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
public class Composer {

    public Composer() {
    }

    public static final int VIEW_MODE_ORIGINAL = R.id.action_original;
    public static final int VIEW_MODE_COMPOSED = R.id.action_composed;
    public static final int VIEW_MODE_MASKED = R.id.action_masked;
    public static final int VIEW_MODE_MASK = R.id.action_mask;


    static class Arrow {
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

    private static class ArrowWithAngle {
        Arrow a;
        float t;

        public ArrowWithAngle(Arrow a, float t) {
            this.a = a;
            this.t = t;
        }
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

    private static Vector2D[] calc_corner_shifted_position(Point2D p0, Point2D p1, Point2D p2) {
        Vector2D v1, v1r, v1s;
        v1 = p1.sub(p0).unit();
        Vector2D v2 = p2.sub(p1).unit();
        v1r = v1.rotate270();
        Vector2D v2r = v2.rotate270();
        v1s = v2r.add(v1r).unit();
        return new Vector2D[]{v1, v1r, v1s};
    }


    private static void draw_grad_polygons(float grad_width, Vector2D offset, float scale, List<DrawData> draw_data, int col_center, int col_surrounding, Canvas canvas, Paint paint) {
        if (draw_data.size() >= 4) {
            int[] colors = new int[]{col_surrounding, col_center};
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < draw_data.size(); i++) {
                DrawData d1 = draw_data.get(i);
                DrawData d2 = draw_data.get(i < draw_data.size() - 1 ? i + 1 : 0);
                Point2D p1 = offset != null ? d1.p.scale(scale).add(offset) : d1.p.scale(scale);
                Point2D p2 = offset != null ? d2.p.scale(scale).add(offset) : d2.p.scale(scale);
                Vector2D d1s = d1.v1;
                Vector2D d2s = d2.v1;
                Vector2D v2 = d2.v2;
                Vector2D v2r = d2.v3;
                Point2D p1s = p1.add(d1s.mul(scale));
                Point2D p2s = p2.add(d2s.mul(scale));

                Point2D pe = p1.add(v2r.mul(grad_width * scale));
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

    public Rect source_rect() { return source_rect;}
    public Point2D center(){ return cur_center; }

    public int source_width() {
        return source_rect.width();
    }

    public int source_height() {
        return source_rect.height();
    }


    Uri image_uri;

    public void set_source_image(Bitmap bmp) {
        int source_w = bmp.getWidth(), source_h = bmp.getHeight();
        source_bmp = bmp;
        source_rect = new Rect(0, 0, source_w, source_h);
//            source_rotated = ((w > h && source_w < source_h || w < h && source_w > source_h));

        working_buffers = new DrawBuffer[]{new DrawBuffer(source_w, source_h), new DrawBuffer(source_w, source_h)};
        cur_center = null;
    }


    private Rect calc_working_rect(float scale) {
        int w;
        int h;
        if (scale < 1.0f) {
            w = Math.round(source_rect.width() * scale);
            h = Math.round(source_rect.height() * scale);
        } else {
            w = source_rect.width();
            h = source_rect.height();
        }
        return new Rect(0, 0, w, h);
    }


    static class DrawBuffer {
        int w, h;
        Bitmap bmp;
        Canvas canvas;

        public DrawBuffer(int w, int h) {
            this.w = w;
            this.h = h;
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bmp);
        }
    }

    DrawBuffer[] working_buffers;


    float cur_scale;
    Point2D cur_center;
    List<DrawData> cur_draw_data = null;
    float cur_draw_data_grad_width;
    float cur_grad_width;

    Bitmap cur_composed = null;

    public void update_geometry_data(List<Arrow> arrows) {
        cur_center = null;
        cur_draw_data = null;
        cur_scale = 1.0f;

        ArrayList<ArrowWithAngle> sorted_arrows = sort_arrows(arrows);
        if (sorted_arrows == null) {
            return;
        }
        if (!calc_center(sorted_arrows)) {
            return;
        }
        if (!calc_scale(cur_center, arrows)) {
            return;
        }
        if (!calc_draw_data(sorted_arrows)) {
            return;
        }

        recompose();
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

    private boolean calc_center(ArrayList<ArrowWithAngle> sorted_arrows) {
        if (sorted_arrows == null) {
            cur_center = null;
            return false;
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
            cur_center = new Point2D(sum_x / center_cands.size(), sum_y / center_cands.size());
            return true;
        } else {
            cur_center = null;
            return false;
        }
    }

    final static float draw_angle = (float) (Math.PI * 2 / 32);


    private boolean calc_draw_data(ArrayList<ArrowWithAngle> sorted_arrows) {
        cur_grad_width = default_grad_width_ratio * Math.min(source_rect.width(), source_rect.height());
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
                points.add(Point2D.point_on_circle(cur_center, len, angle_s_tmp));
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
                float len = Point2D.ZERO.sub(p).length();
                if (len_min < 0 || len_min > len) {
                    len_min = len;
                }
            }
            grad_width = len_min < cur_grad_width ? len_min : cur_grad_width;

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
            cur_draw_data_grad_width = grad_width;
            return true;
        } else {
            cur_draw_data = null;
            return false;
        }
    }


    private boolean calc_scale(Point2D center, List<Arrow> arrows) {
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
                cur_scale = range_far / range_near;
                return true;
            }
        }
        return false;
    }

    final float default_grad_width_ratio = 0.1f;

    final static int REPEAT_COUNT = 5;

    public Bitmap compose_result(int view_mode, Paint paint) {
        if (cur_center != null) {
            Bitmap composed = compose_image(1.0f, view_mode, true, paint);
            return composed;
        } else {
            return null;
        }
    }

    public void recompose() {
        cur_composed = null;

    }

    public Rect compose_rect(){
        return compose_rect;
    }

    public Bitmap compose(float scale, int view_mode, boolean for_save, Paint paint) {
        if (cur_composed == null) {
            cur_composed = compose_image(scale, view_mode, for_save, paint);
        }
        return cur_composed;
    }
    Rect compose_rect;

    public Bitmap compose_image(float scale, int view_mode, boolean for_save, Paint paint) {
        if (cur_center != null && source_bmp != null && working_buffers != null) {
            compose_rect = calc_working_rect(scale);
            working_buffers[0].canvas.clipRect(compose_rect, Region.Op.REPLACE);
            working_buffers[1].canvas.clipRect(compose_rect, Region.Op.REPLACE);
            float working_scale = (float) compose_rect.width() / (float) source_rect.width();

            paint.reset();
            DrawBuffer mask = working_buffers[0];
            DrawBuffer working = working_buffers[1];
            if (cur_draw_data != null) {
                draw_mask(mask, working_scale, view_mode, working, for_save, paint);
            } else {
                mask.canvas.drawColor(0xFFFFFFFF);
            }
            float border_grad_width = cur_grad_width * working_scale;
            Rect cut_rect = new Rect((int) border_grad_width, (int) border_grad_width, (int) (compose_rect.right - border_grad_width), (int) (compose_rect.bottom - border_grad_width));
            draw_cut_border_mask(compose_rect, cut_rect, working.canvas, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mask.canvas.drawBitmap(working.bmp, 0, 0, paint);
            paint.setXfermode(null);

            if (view_mode == VIEW_MODE_MASK) {
                return mask.bmp;
            } else {
                Matrix copy_matrix = new Matrix();
                copy_matrix.setScale(working_scale, working_scale);
                working.canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
                working.canvas.drawBitmap(source_bmp, copy_matrix, paint);


                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
                working.canvas.drawBitmap(mask.bmp, 0, 0, paint);
                paint.setXfermode(null);

                if (view_mode != VIEW_MODE_MASKED) {
                    paint.setStyle(Paint.Style.FILL);
                    Matrix matrix = new Matrix();

                    float tmp_scale = cur_scale;
                    DrawBuffer src = null;
                    DrawBuffer dst = null;
                    Point2D cur_scaled_center = cur_center.scale(scale);
                    for (int i = 0; i < REPEAT_COUNT; i++) {
                        src = working_buffers[(i + 1) % 2];
                        dst = working_buffers[i % 2];
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


    private Point2D[] rect_to_points(Rect rect) {
        return new Point2D[]{
                new Point2D(rect.left, rect.top),
                new Point2D(rect.right, rect.top),
                new Point2D(rect.right, rect.bottom),
                new Point2D(rect.left, rect.bottom),
        };
    }

    private void draw_cut_border_mask(Rect image_rect, Rect cut_rect, Canvas canvas, Paint paint) {
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
            Point2D pe = p0.add(d1.mul(ls[i]));
            LinearGradient brush = new LinearGradient(p0.x, p0.y, pe.x, pe.y, colors, null, Shader.TileMode.CLAMP);
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
    }

    private void draw_mask(DrawBuffer mask, float scale, int view_mode, DrawBuffer working, boolean for_save, Paint paint) {
        float ratio = 1.0f;
        boolean modifying;
        mask.canvas.drawColor(0xFFFFFFFF);

        Point2D[] center_polygon = new Point2D[cur_draw_data.size()];
        for (int i = 0; i < cur_draw_data.size(); i++) {
            DrawData data = cur_draw_data.get(i);
            center_polygon[i] = data.p.add(data.v2).scale(scale);
        }
        paint.setColor(0x00000000);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        paint.setStyle(Paint.Style.FILL);
        draw_polygon(center_polygon, mask.canvas, paint);
        paint.setXfermode(null);

        draw_grad_polygons(cur_draw_data_grad_width, null, scale, cur_draw_data, 0x00FFFFFF, 0xFFFFFFFF, mask.canvas, paint);

        if (view_mode == VIEW_MODE_MASKED && for_save == false) {
            Point2D cur_scaled_center = cur_center.scale(scale);
            working.canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            for (int i = 0; i < cur_draw_data.size(); i++) {
                DrawData data = cur_draw_data.get(i);
                center_polygon[i] = data.p.add(data.v1).scale(scale).scale(1.0f / cur_scale, cur_scaled_center);
            }
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            draw_polygon(center_polygon, working.canvas, paint);

            draw_grad_polygons(cur_draw_data_grad_width, cur_scaled_center.sub(cur_scaled_center.scale(1.0f / cur_scale)), scale / cur_scale, cur_draw_data, 0xFFFFFFFF, 0x00FFFFFF, working.canvas, paint);

            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));

            mask.canvas.drawBitmap(working.bmp, 0, 0, paint);
            paint.setXfermode(null);
        }

    }

    public static void draw_polygon(Point2D[] points, Canvas canvas, Paint paint) {
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

}
