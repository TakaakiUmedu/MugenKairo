package com.gmail.umedutakaaki.mugenkairo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
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
    MainActivity main_activity;
    Composer composer;

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

        if (context instanceof MainActivity) {
            main_activity = (MainActivity) context;
        }


        composer = new Composer();


    }
    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (restored_state != null) {
            restore_state(restored_state);
            restored_state = null;
            main_activity.enable_menu_items();
        }
    }

    private static float MAXIMUM_IMAGE_MEMORY_RATIO = 0.8f;
    private static int MAXIMUM_IMAGE_COUNT_TO_BE_LOADED = 3;


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

    public static final int VIEW_MODE_ORIGINAL = R.id.action_original;
    public static final int VIEW_MODE_COMPOSED = R.id.action_composed;
    public static final int VIEW_MODE_MASKED = R.id.action_masked;
    public static final int VIEW_MODE_MASK = R.id.action_mask;

    private void initialize_arrows() {
        Rect source_rect = composer.source_rect();
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

    private Composer.Arrow make_default_arrow(float cx, float cy, float len, double r) {
        float sin = (float) Math.sin(r);
        float cos = (float) Math.cos(r);
        return new Composer.Arrow(cx + ARROW_START * len * cos, cy - ARROW_START * len * sin, cx + ARROW_END * len * cos, cy - ARROW_END * len * sin);
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


    private List<Composer.Arrow> arrows;


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
        Composer.draw_polygon(new Point2D[]{t1, t2, t3}, canvas, paint);

        canvas.drawCircle(s.x, s.y, CIRCLE_SIZE, paint);
    }


    boolean source_rotated = false;
    Rect canvas_rect = null;

    Uri image_uri;


    public boolean load_target_image(Uri image_uri) {
        this.image_uri = image_uri;

        restored_state = null;
        Bitmap bmp = null;
        composer.release_images();
        try {
            InputStream input = main_activity.getContentResolver().openInputStream(image_uri);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);

            long maximum_image_pixels = (long) ((float) Runtime.getRuntime().maxMemory() * MAXIMUM_IMAGE_MEMORY_RATIO / (4.0f * MAXIMUM_IMAGE_COUNT_TO_BE_LOADED));

            int in_sample_size = 1;
            while (options.outHeight * options.outWidth / in_sample_size > maximum_image_pixels) {
                in_sample_size *= 2;
            }

            input = main_activity.getContentResolver().openInputStream(image_uri);

            options.inJustDecodeBounds = false;
            options.inSampleSize = in_sample_size;
            int image_size = Math.min(options.outHeight, options.outWidth);
            while(true) {
                if (image_size / options.inSampleSize < IMAGE_SIZE_MIN) {
                    main_activity.show_message(String.format(main_activity.getString(R.string.out_of_memory_error_message), options.outWidth, options.outHeight));
                    return false;
                }
                try {
                    bmp = BitmapFactory.decodeStream(input, null, options);
                } catch (OutOfMemoryError e) {
                }
                if (bmp == null || composer.set_source_image(bmp) == false) {
                    options.inSampleSize *= 2;
                    if(bmp != null) {
                        bmp.recycle();
                        bmp = null;
                    }
                    Runtime.getRuntime().gc();
                    continue;
                }
                break;
            }
            if (in_sample_size > 1) {
                main_activity.show_message(String.format(main_activity.getString(R.string.image_resized), options.outWidth, options.outHeight));
            }
        } catch (IOException e) {
            Log.e("Error", e.toString());
            main_activity.show_message(String.format(main_activity.getString(R.string.load_error_message), image_uri.toString()) + e.toString());
            composer.release_images();
        }

        if (bmp != null) {
            initialize_view();

            initialize_arrows();

            control_visible = true;
            composer.update_geometry_data(arrows);

            invalidate();
            return true;
        } else {
            return false;
        }
    }
    static final private int IMAGE_SIZE_MIN = 64;

    private void initialize_view() {
        int w = getWidth(), h = getHeight();
        int source_w = composer.source_width();
        int source_h = composer.source_height();
        if (source_w <= 0 || source_h <= 0){
            return ;
        }
        canvas_rect = new Rect(0, 0, w, h);

        view_scale_range.min = Math.min((float) w / (float) source_w, (float) h / (float) source_h);
        view_scale_range.max = Math.max(view_scale_range.min, Math.min((float) w / MINIMUM_DOTS_IN_DISPLAY, (float) h / MINIMUM_DOTS_IN_DISPLAY));
        view_offset = new Vector2D(0, 0);
        set_view_scale(view_scale_range.min);

    }

    private File find_and_create_save_dir() {
        String save_dir = main_activity.getString(R.string.name_save_dir);
        File root = Environment.getExternalStorageDirectory();
        File pictures = new File(root.getPath() + "/Pictures");
        if (pictures.isDirectory()) {
            root = new File(pictures.getPath() + "/" + save_dir);
        } else if (!pictures.exists()) {
            if (!pictures.mkdir()) {
                return null;
            }
            root = new File(pictures.getPath() + "/" + save_dir);
        } else {
            root = new File(root.getPath() + "/" + save_dir);
        }

        if (root.isDirectory()) {
            return root;
        }
        if (root.exists()) {
            int i = 0;
            File tmp_root;
            while (true) {
                tmp_root = new File(root.getPath() + "_" + i);
                if (!tmp_root.exists()) {
                    root = tmp_root;
                    break;
                }
                if (tmp_root.isDirectory()) {
                    return tmp_root;
                }

                i++;
            }
        }
        if (root.mkdir()) {
            return root;
        } else {
            return null;
        }
    }


    public void save_image() {
        Bitmap bmp = composer.compose_result(view_mode, paint);
        String ext, mime_type;
        if (get_view_mode() == R.id.action_composed) {
            ext = ".jpg";
            mime_type = "image/jpeg";
        } else {
            ext = ".png";
            mime_type = "image/png";
        }
        if (bmp != null) {
            File root = find_and_create_save_dir();
            if (root != null) {
                SimpleDateFormat filename_format = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String filename_base = filename_format.format(new Date());
                String filename = filename_base + ext;
                File file_to_save = new File(root, filename);
                int i = 0;
                while (file_to_save.exists()) {
                    filename = filename_base + "_" + i + ext;
                    file_to_save = new File(root, filename);
                    i++;
                }
                String filepath = file_to_save.toString();
                try {
                    FileOutputStream output = new FileOutputStream(filepath);
                    if (ext == ".jpg") {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, output);
                    } else {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, output);
                    }
                    output.close();


                    ContentValues values = new ContentValues();
                    ContentResolver contentResolver = main_activity.getContentResolver();
                    values.put(MediaStore.Images.Media.MIME_TYPE, mime_type);
                    values.put(MediaStore.Images.Media.TITLE, filename);
                    values.put("_data", filepath);
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                    main_activity.show_message(String.format(main_activity.getString(R.string.saved_message), filepath));
                } catch (IOException e) {
                    Log.e("Error", e.toString());
                    main_activity.show_message(String.format(main_activity.getString(R.string.save_error_message), filepath) + e.toString());
                }
            } else {
                main_activity.show_message(main_activity.getString(R.string.prepare_save_error_message));
            }
        }
    }


    final float MINIMUM_DOTS_IN_DISPLAY = 32;

    private final Paint paint = new Paint();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (arrows == null) {
            initialize_arrows();
        }
        if(view_scale <= 0) {
            initialize_view();
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
            canvas.drawText(main_activity.getString(R.string.initial_message), canvas.getWidth() / 2, canvas.getHeight() / 2, paint);
            return;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFF0000);
        paint.setStrokeWidth(LINE_WIDTH);

        Rect source_rect = composer.source_rect();

        Bitmap bmp = composer.compose(view_scale, view_mode, false, paint);
        if (bmp != null) {
            float draw_scale = source_rect.width() * view_scale / composer.compose_rect().width();
            int border_w = view_offset.x > 0 ? Math.round(view_offset.x) : 0;
            int border_h = view_offset.y > 0 ? Math.round(view_offset.y) : 0;

            Rect dst_rect = new Rect(border_w, border_h, canvas_rect.right - border_w, canvas_rect.bottom - border_h);
            float l = view_offset.x > 0 ? 0 : -view_offset.x / draw_scale;
            float t = view_offset.y > 0 ? 0 : -view_offset.y / draw_scale;
            Rect src_rect = new Rect(
                    Math.round(l),
                    Math.round(t),
                    Math.round(l + dst_rect.width() / draw_scale),
                    Math.round(t + dst_rect.height() / draw_scale)
            );

            canvas.drawBitmap(bmp, src_rect, dst_rect, paint);
        }

        if (control_visible) {
            paint.setAntiAlias(true);
            Point2D cur_center = composer.center();
            if (cur_center != null) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFFFFFF00);
                Point2D lt = calc_offset_pos(cur_center).sub(new Vector2D(1, 1).mul(CIRCLE_SIZE));
                Point2D rb = calc_offset_pos(cur_center).add(new Vector2D(1, 1).mul(CIRCLE_SIZE));
//                    canvas.drawCircle(center.x + offset.x, center.y + offset.y, CIRCLE_SIZE, paint);

                paint.setStrokeWidth(LINE_WIDTH_BORDER);
                paint.setColor(0x80000000);
                float line_offset = (LINE_WIDTH_BORDER - LINE_WIDTH) / 2.0f;
                canvas.drawLine(lt.x - line_offset, lt.y - line_offset, rb.x + line_offset, rb.y + line_offset, paint);
                canvas.drawLine(lt.x - line_offset, rb.y + line_offset, rb.x + line_offset, lt.y - line_offset,  paint);
                paint.setStrokeWidth(LINE_WIDTH);
                paint.setColor(0xFFFFFF00);
                canvas.drawLine(lt.x, lt.y, rb.x, rb.y, paint);
                canvas.drawLine(lt.x, rb.y, rb.x, lt.y, paint);
            }

            if(arrows != null) {
                paint.setStyle(Paint.Style.STROKE);
                for (Composer.Arrow a : arrows) {

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

    int view_mode = VIEW_MODE_COMPOSED;

    public void set_view_mode(int mode) {
        view_mode = mode;
        composer.recompose();
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
        Rect source_rect = composer.source_rect();
        if (source_rect == null || arrows == null) {
            return;
        }
        if(arrows.size() > 1) {
            Vector2D vs = Vector2D.ZERO;
            Vector2D ve = Vector2D.ZERO;
            for (Composer.Arrow a : arrows) {
                vs = vs.add(a.s.sub(Point2D.ZERO));
                ve = ve.add(a.e.sub(Point2D.ZERO));
            }
            vs = vs.mul(1.0f / arrows.size());
            ve = ve.mul(1.0f / arrows.size());
            arrows.add(new Composer.Arrow(Point2D.ZERO.add(vs), Point2D.ZERO.add(ve)));
        }else{
            initialize_arrows();
        }

        composer.update_geometry_data(arrows);
        invalidate();
    }

    public void del_arrow() {
        if (arrows.size() > 2) {
            arrows.remove(arrows.size() - 1);
            composer.update_geometry_data(arrows);
            invalidate();
        }
    }


    private float is_touching_circle(Point2D p1, Point2D p2) {
        Vector2D d = p1.sub(p2);
        float len2 = d.length2() * (view_scale  * view_scale);
        if (len2 < CIRCLE_SIZE * CIRCLE_SIZE * TOUCH_MARGIN * TOUCH_MARGIN) {
            return len2;
        }
        return -1;
    }

    private static final float TOUCH_MARGIN = 2f;

    private boolean get_touching_arrow(Point2D pos) {
        float min = CIRCLE_SIZE * CIRCLE_SIZE * TOUCH_MARGIN * TOUCH_MARGIN * 2;
        boolean found = false;
        for (Composer.Arrow a : arrows) {
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

    Composer.Arrow selected = null;
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
                            composer.update_geometry_data(arrows);
                            invalidate();
                        }
                        break;
                }
            }
        }
        return true;
    }

    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private boolean control_visible = true;

    float view_scale;
    Range view_scale_range = new Range(1.0f);
    Vector2D view_offset;
    Range view_offset_range_x = new Range(0.0f);
    Range view_offset_range_y = new Range(0.0f);

    public float get_view_scale() {
        return view_scale;
    }

    private void scroll_view(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (selected != null) {
            return;
        }
        set_view_offset(view_offset_range_x.clamp(view_offset.x - distanceX), view_offset_range_y.clamp(view_offset.y - distanceY));

    }

    private void set_view_offset(float x, float y) {
        view_offset = new Vector2D(view_offset_range_x.clamp(x), view_offset_range_y.clamp(y));
        this.invalidate();
    }

    private void change_view_scale(float scale, float center_x, float center_y) {
        float prev_view_scale = view_scale;
        set_view_scale(view_scale * scale);
        Point2D center = new Point2D(center_x, center_y);
        float view_offset_x = (view_offset.x - center.x) * (view_scale / prev_view_scale) + center.x;
        float view_offset_y = (view_offset.y - center.y) * (view_scale / prev_view_scale) + center.y;
        view_offset = new Vector2D(view_offset_range_x.clamp(view_offset_x), view_offset_range_y.clamp(view_offset_y));

        this.invalidate();
    }

    private void set_view_scale(float scale) {
        Rect source_rect = composer.source_rect();
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
            composer.recompose();
            this.invalidate();
        }
    }

    private static final String STATE_NAME_URI = "URI";
    private static final String STATE_NAME_VIEW_SCALE = "SCALE";
    private static final String STATE_NAME_VIEW_OFFSET_X = "OFFSET_X";
    private static final String STATE_NAME_VIEW_OFFSET_Y = "OFFSET_Y";
    private static final String STATE_NAME_ARROW_S_X = "ARROW_S_X";
    private static final String STATE_NAME_ARROW_S_Y = "ARROW_S_Y";
    private static final String STATE_NAME_ARROW_E_X = "ARROW_E_X";
    private static final String STATE_NAME_ARROW_E_Y = "ARROW_E_Y";
    private static final String STATE_NAME_VIEW_MODE = "MODE";


    public void save_state(Bundle state) {
        if (image_uri == null) {
            return;
        }
        state.putString(STATE_NAME_URI, image_uri.toString());

        state.putInt(STATE_NAME_VIEW_MODE, view_mode);
        state.putFloat(STATE_NAME_VIEW_SCALE, view_scale);
        state.putFloat(STATE_NAME_VIEW_OFFSET_X, view_offset.x);
        state.putFloat(STATE_NAME_VIEW_OFFSET_Y, view_offset.y);

        if (arrows != null && arrows.size() > 0) {
            float[] s_x = new float[arrows.size()];
            float[] s_y = new float[arrows.size()];
            float[] e_x = new float[arrows.size()];
            float[] e_y = new float[arrows.size()];
            for (int i = 0; i < arrows.size(); i++) {
                Composer.Arrow a = arrows.get(i);
                s_x[i] = a.s.x;
                s_y[i] = a.s.y;
                e_x[i] = a.e.x;
                e_y[i] = a.e.y;
            }
            state.putFloatArray(STATE_NAME_ARROW_S_X, s_x);
            state.putFloatArray(STATE_NAME_ARROW_S_Y, s_y);
            state.putFloatArray(STATE_NAME_ARROW_E_X, e_x);
            state.putFloatArray(STATE_NAME_ARROW_E_Y, e_y);
        }
    }



    static class RestoredState {
        Uri uri = null;
        int view_mode;
        float view_scale;
        float view_offset_x;
        float view_offset_y;
        ArrayList<Composer.Arrow> arrows;
    }
    RestoredState restored_state = null;

    public void restore_state(Bundle state) {
        String uri_str = state.getString(STATE_NAME_URI);
        if (uri_str == null) {
            return;
        }
        Uri uri = Uri.parse(uri_str);
        if (uri == null) {
            return;
        }

        restored_state = new RestoredState();

        restored_state.uri = uri;
        restored_state.view_mode = state.getInt(STATE_NAME_VIEW_MODE, VIEW_MODE_COMPOSED);
        restored_state.view_scale = state.getFloat(STATE_NAME_VIEW_SCALE, view_scale_range.min);
        restored_state.view_offset_x = state.getFloat(STATE_NAME_VIEW_OFFSET_X, 0);
        restored_state.view_offset_y = state.getFloat(STATE_NAME_VIEW_OFFSET_Y, 0);

        float[] s_x = state.getFloatArray(STATE_NAME_ARROW_S_X);
        float[] s_y = state.getFloatArray(STATE_NAME_ARROW_S_Y);
        float[] e_x = state.getFloatArray(STATE_NAME_ARROW_E_X);
        float[] e_y = state.getFloatArray(STATE_NAME_ARROW_E_Y);
        if(s_x != null && s_y != null && e_x != null && e_y != null && s_x.length == s_y.length && s_x.length == e_x.length && s_x.length == e_y.length){
            restored_state.arrows = new ArrayList<>();
            for(int i = 0; i < s_x.length; i ++){
                restored_state.arrows.add(new Composer.Arrow(new Point2D(s_x[i], s_y[i]), new Point2D(e_x[i], e_y[i])));
            }
        }

    }
    private boolean restore_state(RestoredState restored_state){

        if (!load_target_image(restored_state.uri)) {
            return false;
        }
        view_mode = restored_state.view_mode;
        set_view_scale(restored_state.view_scale);
        set_view_offset(restored_state.view_offset_x, restored_state.view_offset_y);

    arrows = restored_state.arrows;
        composer.update_geometry_data(arrows);
        invalidate();
        return true;
    }
    public void destroy() {
        composer.release_images();
        composer = null;
        Runtime.getRuntime().gc();
    }


}