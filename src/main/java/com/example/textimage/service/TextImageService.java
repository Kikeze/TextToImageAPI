package com.example.textimage.service;

import com.example.textimage.api.TextImageRequest;
import com.example.textimage.api.TextImageResponse;
import com.example.textimage.config.RenderingProperties;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Font;
import java.util.Base64;

@Service
public class TextImageService {
    private final FontCatalog fonts;
    private final ColorResolver colors;
    private final PngRenderer renderer;
    private final RenderingProperties properties;

    public TextImageService(FontCatalog fonts, ColorResolver colors, PngRenderer renderer,
                            RenderingProperties properties) {
        this.fonts = fonts;
        this.colors = colors;
        this.renderer = renderer;
        this.properties = properties;
    }

    public TextImageResponse create(TextImageRequest request) {
        String text = request.text();
        if (text == null || text.isBlank()) {
            throw new InvalidImageRequestException("text es obligatorio y no puede estar vacío");
        }
        if (text.codePointCount(0, text.length()) > properties.maxCodePoints()) {
            throw new InvalidImageRequestException("text supera el máximo de " + properties.maxCodePoints() + " puntos de código Unicode");
        }
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if ((Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')
                    || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
                throw new InvalidImageRequestException("text contiene caracteres de control o Unicode inválido");
            }
            i += Character.charCount(codePoint);
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        Font font = fonts.resolve(request.font(), normalized, properties.fontSize());
        Color background = Color.decode(properties.background());
        Color color = colors.resolve(request.color(), background);
        PngRenderer.RenderedPng png = renderer.render(normalized, font, color, background);
        return new TextImageResponse(Base64.getEncoder().encodeToString(png.bytes()), "image/png",
                font.getFamily(java.util.Locale.ROOT), ColorResolver.hex(color), png.width(), png.height());
    }
}
