package com.workshop.domain.exception;

import java.util.UUID;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(Class<?> type, UUID id) {
        return new ResourceNotFoundException(type.getSimpleName() + " not found: " + id);
    }
}
