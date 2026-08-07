package com.hospital.appointment.exception;

public class ApiExceptions {

    public static class SlotConflictException extends RuntimeException {
        public SlotConflictException(String message) {
            super(message);
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
