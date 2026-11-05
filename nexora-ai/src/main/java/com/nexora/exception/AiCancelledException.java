package com.nexora.exception;

/** Thrown inside a provider when the client stopped the stream. */
public class AiCancelledException extends RuntimeException {
    public AiCancelledException() { super("cancelled", null, false, false); }
}
