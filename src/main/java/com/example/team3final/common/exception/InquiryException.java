package com.example.team3final.common.exception;

public class InquiryException extends ServiceException {
    public InquiryException(ErrorCode errorCode) {
        super(errorCode);
    }
}
