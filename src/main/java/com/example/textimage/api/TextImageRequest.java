package com.example.textimage.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TextImageRequest(
        @NotBlank(message = "text es obligatorio y no puede estar vacío") String text,
        @Size(min = 1, max = 200, message = "font debe tener entre 1 y 200 caracteres") String font,
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "color debe tener formato #RRGGBB") String color) {
}
