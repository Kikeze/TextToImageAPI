package com.example.textimage.api;

public record TextImageResponse(String imageBase64, String mimeType, String font,
                                String color, int width, int height) {
}
