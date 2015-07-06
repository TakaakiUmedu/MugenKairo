package com.gmail.umedutakaaki.geometry;

public class Point2D {
    public final float x, y;

    public Point2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Point2D(Point2D p) {
        this.x = p.x;
        this.y = p.y;
    }

    public Vector2D sub(Point2D p) {
        return new Vector2D(x - p.x, y - p.y);
    }

    public Point2D add(Vector2D v) {
        return new Point2D(x + v.x, y + v.y);
    }

    public Point2D sub(Vector2D v) {
        return new Point2D(x - v.x, y - v.y);
    }


    public static Point2D point_on_circle(Point2D center, float radius, float angle) {
        return new Point2D((float) (center.x + radius * Math.cos(angle)), (float) (center.y + radius * Math.sin(angle)));
    }

    public Point2D scale(float r) {
        return new Point2D(x * r, y * r);
    }

    public Point2D scale(float r, Point2D center) {
        return center.add(sub(center).mul(r));
    }

}


