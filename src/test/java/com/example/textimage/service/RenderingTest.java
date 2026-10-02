package com.example.textimage.service;

import com.example.textimage.api.TextImageRequest;
import com.example.textimage.config.RenderingProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderingTest {
    private final RenderingProperties properties = new RenderingProperties(32, 20, 1200, 2000, 4000000, "#FFFFFF");
    private final PngRenderer renderer = new PngRenderer(properties);
    private final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 32);

    @Test
    void pngContainsTextOnAnOpaqueWhiteBackground() throws Exception {
        var png = renderer.render("Hola, mundo", font, Color.BLUE, Color.WHITE);
        assertThat(Arrays.copyOf(png.bytes(), 8)).containsExactly((byte) 137, (byte) 80, (byte) 78,
                (byte) 71, (byte) 13, (byte) 10, (byte) 26, (byte) 10);
        var image = ImageIO.read(new ByteArrayInputStream(png.bytes()));
        assertThat(image.getWidth()).isEqualTo(png.width()).isLessThanOrEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(png.height());
        assertThat(image.getColorModel().hasAlpha()).isFalse();
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
        long coloredPixels = IntStream.range(0, image.getHeight()).mapToLong(y ->
                IntStream.range(0, image.getWidth()).filter(x -> image.getRGB(x, y) != Color.WHITE.getRGB()).count()).sum();
        assertThat(coloredPixels).isPositive();
        for (int x = 0; x < image.getWidth(); x++) {
            assertThat(image.getRGB(x, 19)).isEqualTo(Color.WHITE.getRGB());
            assertThat(image.getRGB(x, image.getHeight() - 20)).isEqualTo(Color.WHITE.getRGB());
        }
    }

    @Test
    void preservesEmptyLinesAndWrapsUnbrokenWords() {
        var single = renderer.render("Hola", font, Color.BLACK, Color.WHITE);
        var multiple = renderer.render("Hola\n\nHola\n", font, Color.BLACK, Color.WHITE);
        assertThat(multiple.height()).isGreaterThan(single.height() * 2);
        var wrapped = renderer.render("W".repeat(300), font, Color.BLACK, Color.WHITE);
        assertThat(wrapped.width()).isLessThanOrEqualTo(1200);
        assertThat(wrapped.height()).isGreaterThan(single.height());
    }

    @Test
    void rejectsPixelOverflowAndGlyphThatCannotFit() {
        var limited = new PngRenderer(new RenderingProperties(32, 20, 1200, 2000, 100, "#FFFFFF"));
        assertThatThrownBy(() -> limited.render("Hola", font, Color.BLACK, Color.WHITE))
                .isInstanceOf(InvalidImageRequestException.class);
        var narrow = new PngRenderer(new RenderingProperties(32, 20, 41, 2000, 4000000, "#FFFFFF"));
        assertThatThrownBy(() -> narrow.render("W", font, Color.BLACK, Color.WHITE))
                .isInstanceOf(InvalidImageRequestException.class);
    }

    @Test
    void randomColorAlwaysHasContrastAndExplicitColorIsPreserved() {
        var resolver = new ColorResolver();
        assertThat(ColorResolver.hex(resolver.resolve("#abcdef", Color.WHITE))).isEqualTo("#ABCDEF");
        assertThat(resolver.resolve("#FFFFFF", Color.WHITE)).isEqualTo(Color.WHITE);
        for (int i = 0; i < 200; i++) {
            assertThat(ColorResolver.contrast(resolver.resolve(null, Color.WHITE), Color.WHITE)).isGreaterThanOrEqualTo(4.5);
        }
        assertThatThrownBy(() -> resolver.resolve("blue", Color.WHITE)).isInstanceOf(InvalidImageRequestException.class);
    }

    @Test
    void fontsAreRealAvailableFamiliesAndSupportTheText() {
        var catalog = new FontCatalog();
        var available = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        for (int i = 0; i < 20; i++) {
            Font selected = catalog.resolve(null, "Hola, áéíóú ñ", 32);
            assertThat(available).contains(selected.getFamily(Locale.ROOT));
            assertThat(selected.canDisplayUpTo("Hola, áéíóú ñ")).isEqualTo(-1);
        }
        var logical = new FontCatalog(List.of(Font.SANS_SERIF));
        assertThat(logical.resolve("sansserif", "Hola", 32).getFamily(Locale.ROOT)).isEqualTo(Font.SANS_SERIF);
        assertThatThrownBy(() -> logical.resolve("missing-font", "Hola", 32)).isInstanceOf(InvalidImageRequestException.class);
        String unsupported = new String(Character.toChars(0x10FFFF));
        assertThatThrownBy(() -> logical.resolve(null, unsupported, 32)).isInstanceOf(InvalidImageRequestException.class);
        assertThatThrownBy(() -> logical.resolve(Font.SANS_SERIF, unsupported, 32)).isInstanceOf(InvalidImageRequestException.class);
        assertThatThrownBy(() -> new FontCatalog(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void countsUnicodeCodePointsAndNormalizesWindowsLineBreaksAndTabs() throws Exception {
        // The supplementary mathematical bold A occupies two UTF-16 code units, but one code point.
        String supplementary = new String(Character.toChars(0x1D400));
        // Isolate code-point counting from whichever fonts happen to be installed.
        FontCatalog catalog = mock(FontCatalog.class);
        when(catalog.resolve(null, supplementary, 32)).thenReturn(font);
        var small = new RenderingProperties(32, 20, 1200, 1, 4000000, "#FFFFFF");
        var service = new TextImageService(catalog, new ColorResolver(), new PngRenderer(small), small);
        assertThat(service.create(new TextImageRequest(supplementary, null, null)).imageBase64()).isNotEmpty();
        assertThatThrownBy(() -> service.create(new TextImageRequest(supplementary.repeat(2), null, null)))
                .isInstanceOf(InvalidImageRequestException.class).hasMessageContaining("puntos de código");
        var normal = new TextImageService(new FontCatalog(), new ColorResolver(), renderer, properties);
        var windows = normal.create(new TextImageRequest("Hola\r\n\tMundo", Font.SANS_SERIF, "#000000"));
        var unix = normal.create(new TextImageRequest("Hola\n    Mundo", Font.SANS_SERIF, "#000000"));
        assertThat(windows.imageBase64()).isEqualTo(unix.imageBase64());
        assertThat(ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(windows.imageBase64())))).isNotNull();
        assertThatThrownBy(() -> normal.create(new TextImageRequest("Hola\u0000", null, null))).isInstanceOf(InvalidImageRequestException.class);
        assertThatThrownBy(() -> normal.create(new TextImageRequest("Hola\uD800", null, null))).isInstanceOf(InvalidImageRequestException.class);
    }

    @Test
    void concurrentRequestsHaveIndependentRenderingState() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<PngRenderer.RenderedPng>> tasks = IntStream.range(0, 16)
                    .mapToObj(i -> (Callable<PngRenderer.RenderedPng>) () -> renderer.render("Texto " + i, font,
                            i % 2 == 0 ? Color.BLUE : Color.RED, Color.WHITE)).toList();
            for (var result : executor.invokeAll(tasks)) {
                assertThat(ImageIO.read(new ByteArrayInputStream(result.get().bytes()))).isNotNull();
            }
        }
    }
}
