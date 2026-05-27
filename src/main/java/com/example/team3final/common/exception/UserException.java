package com.example.team3final.common.exception;

public class UserException extends ServiceException {
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}