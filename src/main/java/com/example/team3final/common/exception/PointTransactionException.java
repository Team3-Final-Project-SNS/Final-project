package com.example.team3final.common.exception;

public class PointTransactionException extends ServiceException {
    public PointTransactionException(ErrorCode errorCode) {
        super(errorCode);
    }
}