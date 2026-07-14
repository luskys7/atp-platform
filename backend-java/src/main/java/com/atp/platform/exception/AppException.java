package com.atp.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public AppException(String errorCode, HttpStatus httpStatus) {
        super(ErrorCodes.message(errorCode));
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public AppException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
