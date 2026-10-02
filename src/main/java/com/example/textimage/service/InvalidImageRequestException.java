package com.example.textimage.service;

public class InvalidImageRequestException extends RuntimeException {
    public InvalidImageRequestException(String message) {
        super(message);
    }
}
