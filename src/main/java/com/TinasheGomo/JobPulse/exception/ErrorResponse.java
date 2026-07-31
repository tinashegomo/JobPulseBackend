package com.TinasheGomo.JobPulse.exception;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErrorResponse {

    LocalDateTime timestamp;
    String errorMessage;
    String errorDetails;
    String errorCode;

    public ErrorResponse(String errorMessage, String errorDetails, String errorCode) {
        this.timestamp = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
        this.errorCode = errorCode;
    }
}