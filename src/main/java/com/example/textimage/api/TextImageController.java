package com.example.textimage.api;

import com.example.textimage.service.TextImageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/text-images")
public class TextImageController {
    private final TextImageService service;

    public TextImageController(TextImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TextImageResponse create(@Valid @RequestBody TextImageRequest request) {
        return service.create(request);
    }
}
