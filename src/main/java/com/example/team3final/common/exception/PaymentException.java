package com.example.team3final.common.exception;

public class PaymentException extends ServiceException {
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
