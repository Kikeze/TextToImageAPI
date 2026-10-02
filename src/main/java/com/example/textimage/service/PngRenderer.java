package com.example.textimage.service;

import com.example.textimage.config.RenderingProperties;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

@Component
public class PngRenderer {
    private final RenderingProperties properties;
    private final FontRenderContext context = new FontRenderContext(null, true, true);

    public PngRenderer(RenderingProperties properties) {
        this.properties = properties;
    }

    public RenderedPng render(String text, Font font, Color color, Color background) {
        int availableWidth = properties.maxWidth() - 2 * properties.margin();
        // Reserve space for italic overhangs and other visual bounds beyond glyph advances.
        List<Line> lines = layout(text, font, availableWidth);
        double contentWidth = lines.stream().mapToDouble(Line::width).max().orElse(1);
        double contentHeight = lines.stream().mapToDouble(Line::height).sum();
        long width = Math.max(1, (long) Math.ceil(contentWidth)) + 2L * properties.margin();
        long height = Math.max(1, (long) Math.ceil(contentHeight)) + 2L * properties.margin();
        if (width > properties.maxWidth() || height > Integer.MAX_VALUE
                || width * height > properties.maxPixels()) {
            throw new InvalidImageRequestException("El texto supera los límites de dimensiones o píxeles de la imagen");
        }
        BufferedImage image = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(color);
            double y = properties.margin();
            for (Line line : lines) {
                if (line.layout() != null) {
                    line.layout().draw(graphics, (float) (properties.margin() - line.left()),
                            (float) (y + line.above()));
                }
                y += line.height();
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG encoder unavailable");
            }
            return new RenderedPng(output.toByteArray(), image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw new ImageGenerationException("No se pudo generar la imagen PNG", exception);
        }
    }

    private List<Line> layout(String text, Font font, int availableWidth) {
        List<Line> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                var metrics = font.getLineMetrics(" ", context);
                lines.add(new Line(null, 0, 1, metrics.getAscent(), metrics.getHeight()));
                continue;
            }
            AttributedString attributed = new AttributedString(paragraph);
            attributed.addAttribute(TextAttribute.FONT, font);
            LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), context);
            while (measurer.getPosition() < paragraph.length()) {
                int start = measurer.getPosition();
                float wrapWidth = availableWidth;
                TextLayout shaped;
                Line line;
                do {
                    measurer.setPosition(start);
                    shaped = measurer.nextLayout(wrapWidth);
                    Rectangle2D bounds = shaped.getBounds();
                    double left = Math.min(0, bounds.getMinX());
                    double right = Math.max(shaped.getAdvance(), bounds.getMaxX());
                    double above = Math.max(shaped.getAscent(), -bounds.getMinY());
                    double below = Math.max(shaped.getDescent() + shaped.getLeading(), bounds.getMaxY());
                    line = new Line(shaped, left, Math.ceil(right - left), above, Math.ceil(above + below));
                    if (line.width() <= availableWidth) {
                        break;
                    }
                    if (measurer.getPosition() - start <= Character.charCount(paragraph.codePointAt(start))) {
                        throw new InvalidImageRequestException("Un carácter no cabe en el ancho máximo configurado");
                    }
                    wrapWidth -= Math.max(1, (float) (line.width() - availableWidth));
                    if (wrapWidth <= 0) {
                        throw new InvalidImageRequestException("El texto no cabe en el ancho máximo configurado");
                    }
                } while (true);
                lines.add(line);
            }
        }
        return lines;
    }

    private record Line(TextLayout layout, double left, double width, double above, double height) { }

    public record RenderedPng(byte[] bytes, int width, int height) { }
}
