package com.finsight.exception;

/**
 * Custom runtime exception thrown when a requested trade entity cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
