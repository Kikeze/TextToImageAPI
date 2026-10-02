package com.example.textimage.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rendering")
public record RenderingProperties(
        @Min(1) @Max(512) int fontSize,
        @Min(0) @Max(1000) int margin,
        @Min(1) @Max(10000) int maxWidth,
        @Min(1) @Max(100000) int maxCodePoints,
        @Min(1) @Max(100000000) long maxPixels,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String background) {
    public RenderingProperties {
        if (maxWidth <= 2 * margin) {
            throw new IllegalArgumentException("rendering.max-width must exceed twice rendering.margin");
        }
    }
}
