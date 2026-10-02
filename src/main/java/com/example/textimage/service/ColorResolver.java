package com.example.textimage.service;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ColorResolver {
    public Color resolve(String requested, Color background) {
        if (requested != null) {
            if (!requested.matches("#[0-9a-fA-F]{6}")) {
                throw new InvalidImageRequestException("color debe tener formato #RRGGBB");
            }
            return Color.decode(requested);
        }
        Color candidate;
        do {
            candidate = new Color(ThreadLocalRandom.current().nextInt(0x1000000));
        } while (contrast(candidate, background) < 4.5);
        return candidate;
    }

    public static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
    }

    static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
