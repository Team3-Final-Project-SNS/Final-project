package com.example.team3final.common.exception;

// ====================================================================
// OptimisticLockConflictException.java
//
// 낙관적 락 버전 충돌 시 발생하는 예외 (전략 C, D에서 사용)
// → ObjectOptimisticLockingFailureException을 catch한 후 던짐
//
// ====================================================================

public class OptimisticLockConflictException extends RuntimeException {

    private final String errorCode;

    public OptimisticLockConflictException(String errorCode) {
        super("낙관적 락 버전 충돌: " + errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
