package dev.starlit.core;

import java.util.ArrayList;
import java.util.List;

public final class StarCatalog {
    public static final long SEED = 0x5747A11L;
    public static final float MIN_SIZE = 0.21F;

    private static final float[][] CLASSES = {
            {0.66F, 0.76F, 1.0F, 0.04F},
            {0.80F, 0.86F, 1.0F, 0.10F},
            {0.97F, 0.97F, 1.0F, 0.18F},
            {1.0F, 0.95F, 0.84F, 0.22F},
            {1.0F, 0.84F, 0.64F, 0.24F},
            {1.0F, 0.70F, 0.50F, 0.22F}
    };

    private StarCatalog() {
    }

    public static List<Star> generate(int count, boolean colors, long seed) {
        Rng rng = new Rng(seed);
        List<Star> stars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float[] dir;
            if (rng.nextFloat() < 0.38F) {
                float longitude = rng.range(0.0F, (float) (Math.PI * 2.0));
                float latitude = (float) (rng.gaussian() * 0.11);
                dir = Galaxy.inPlane(longitude, latitude);
            } else {
                dir = rng.unitVector();
            }
            float magnitude = rng.nextFloat();
            float brightness = 0.3F + 0.7F * (float) Math.pow(magnitude, 6.0);
            float size = MIN_SIZE + 0.5F * brightness;
            if (brightness > 0.7F) {
                size *= 1.35F;
            }
            float[] tint = colors ? pick(rng.nextFloat()) : new float[]{1.0F, 1.0F, 1.0F};
            float level = brightness * (0.8F + 0.4F * rng.nextFloat());
            float phase = rng.nextFloat() * 0.98F;
            stars.add(new Star(dir[0], dir[1], dir[2], size,
                    Math.min(1.0F, tint[0] * level),
                    Math.min(1.0F, tint[1] * level),
                    Math.min(1.0F, tint[2] * level),
                    phase));
        }
        return stars;
    }

    static float[] pick(float roll) {
        float total = 0.0F;
        for (float[] type : CLASSES) {
            total += type[3];
            if (roll < total) {
                return type;
            }
        }
        return CLASSES[CLASSES.length - 1];
    }
}
