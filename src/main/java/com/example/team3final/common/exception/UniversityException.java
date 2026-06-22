package com.example.team3final.common.exception;

public class UniversityException extends ServiceException {
    public UniversityException(ErrorCode errorCode) {
        super(errorCode);
    }
}