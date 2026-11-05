package com.nexora.exception;

/** Problem talking to an AI provider or search provider (unreachable, bad key, missing model...). */
public class AiException extends RuntimeException {
    public AiException(String message) { super(message); }
}
