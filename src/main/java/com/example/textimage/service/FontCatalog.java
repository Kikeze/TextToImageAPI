package com.example.textimage.service;

import org.springframework.stereotype.Component;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FontCatalog {
    private final List<String> families;

    public FontCatalog() {
        this(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT)));
    }

    FontCatalog(List<String> families) {
        this.families = families.stream().distinct().sorted().toList();
        if (this.families.isEmpty()) {
            throw new IllegalStateException("No hay fuentes disponibles para renderizar texto");
        }
    }

    public Font resolve(String requested, String text, int size) {
        // Line separators are layout instructions, not glyphs.
        String glyphs = text.replace("\n", "");
        if (requested != null) {
            String family = families.stream().filter(name -> name.equalsIgnoreCase(requested))
                    .findFirst().orElseThrow(() -> new InvalidImageRequestException("Fuente no disponible: " + requested));
            Font font = new Font(family, Font.PLAIN, size);
            if (font.canDisplayUpTo(glyphs) != -1) {
                throw new InvalidImageRequestException("La fuente solicitada no puede representar todos los caracteres");
            }
            return font;
        }
        List<Font> candidates = families.stream().map(name -> new Font(name, Font.PLAIN, size))
                .filter(font -> font.canDisplayUpTo(glyphs) == -1).toList();
        if (candidates.isEmpty()) {
            throw new InvalidImageRequestException("Ninguna fuente disponible puede representar todos los caracteres");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
