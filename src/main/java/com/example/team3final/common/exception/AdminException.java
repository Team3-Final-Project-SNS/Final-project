package com.example.team3final.common.exception;

public class AdminException extends ServiceException {
    public AdminException(ErrorCode errorCode) {
        super(errorCode);
    }
}
