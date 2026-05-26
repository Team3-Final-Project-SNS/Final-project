package com.example.team3final.common.exception;

public class ReportException extends ServiceException {

    public ReportException(ErrorCode errorCode) {
        super(errorCode);
    }
}