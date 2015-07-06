package com.gmail.umedutakaaki.geometry;


public class Vector2D {
    public final float x, y;

    public Vector2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D rotate90() {
        return new Vector2D(y, -x);
    }

    public Vector2D rotate270() {
        return new Vector2D(-y, x);
    }

    public float length2() {
        return x * x + y * y;
    }

    public float length() {
        return (float) Math.sqrt(length2());
    }

    public Vector2D unit() {
        float length = length();
        if (length > 0.0) {
            return new Vector2D(x / length, y / length);
        } else {
            return new Vector2D(1.0f, 0.0f);
        }
    }

    public Vector2D add(Vector2D v) {
        return new Vector2D(x + v.x, y + v.y);
    }

    public Vector2D sub(Vector2D v) {
        return new Vector2D(x - v.x, y - v.y);
    }

    public Vector2D mul(float f) {
        return new Vector2D(x * f, y * f);
    }

    public Vector2D div(float f) {
        return new Vector2D(x / f, y / f);
    }

    public float multiply(Vector2D v) {
        return (x * v.x) + (y * v.y);
    }


    public static Point2D point_on_circle(Point2D center, float radius, float angle) {
        return new Point2D((float) (center.x + radius * Math.cos(angle)), (float) (center.y + radius * Math.sin(angle)));
    }

    public static Point2D scale(Point2D p, float r) {
        return new Point2D(p.x * r, p.y * r);
    }

    public static Point2D scale(Point2D p, float r, Point2D center) {
        return center.add(p.sub(center).mul(r));
    }

}
