package com.example.team3final.common.exception;

public class MatchException extends ServiceException {
    public MatchException(ErrorCode errorCode) {
        super(errorCode);
    }
}
