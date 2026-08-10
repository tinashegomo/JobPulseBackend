package com.TinasheGomo.JobPulse.exception;

import com.TinasheGomo.JobPulse.exception.exceptions.DuplicateException;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                "Invalid email or password",
                request.getRequestURI(),
                HttpStatus.UNAUTHORIZED.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateException(
            DuplicateException duplicateException,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                duplicateException.getMessage(),
                request.getRequestURI(),
                HttpStatus.CONFLICT.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NotFoundException notFoundException,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                notFoundException.getMessage(),
                request.getRequestURI(),
                HttpStatus.NOT_FOUND.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException validationException,
            HttpServletRequest request) {
        String message = validationException.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorResponse error = new ErrorResponse(
                message,
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                exception.getMessage(),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unhandled exception at {}: ", request.getRequestURI(), exception);
        ErrorResponse error = new ErrorResponse(
                "An unexpected error occurred",
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.toString()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}