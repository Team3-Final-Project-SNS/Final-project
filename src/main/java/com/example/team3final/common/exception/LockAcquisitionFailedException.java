package com.example.team3final.common.exception;

// ====================================================================
// LockAcquisitionFailedException.java
//
// Redis 분산 락 획득 실패 시 발생하는 예외 (전략 E, F에서 사용)
// → tryLock()이 false를 반환했을 때 던짐
//
// ====================================================================

public class LockAcquisitionFailedException extends RuntimeException {

    private final String errorCode;

    public LockAcquisitionFailedException(String errorCode) {
        super("Redis 분산 락 획득 실패: " + errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
