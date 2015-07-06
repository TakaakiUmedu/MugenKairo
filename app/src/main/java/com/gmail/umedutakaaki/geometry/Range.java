package com.gmail.umedutakaaki.geometry;



public class Range {
    public float min, max;

    public Range(float minmax) {
        this.min = minmax;
        this.max = minmax;
    }

    public Range(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public float clamp(float f) {
        if (f < min) {
            return min;
        }
        if (f > max) {
            return max;
        }
        return f;
    }

}
    
