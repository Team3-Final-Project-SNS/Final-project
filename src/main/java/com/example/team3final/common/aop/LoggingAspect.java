package com.example.team3final.common.aop;

import com.example.team3final.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around(
            "execution(* com.example.team3final.domain..controller..*(..)) || " +
                    "execution(* com.example.team3final.domain..service..*(..))"
    )
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.info("[{}] {}.{}() 호출",
                className.contains("Controller") ? "CTRL" : "SVC",
                className,
                methodName
        );

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            log.info("[{}] {}.{}() 완료 | {}ms",
                    className.contains("Controller") ? "CTRL" : "SVC",
                    className,
                    methodName,
                    System.currentTimeMillis() - startTime
            );
            return result;
        } catch (Exception e) {
            String layer = className.contains("Controller") ? "CTRL" : "SVC";

            if (e instanceof ServiceException serviceException
                    && serviceException.getErrorCode().getHttpStatus().is4xxClientError()) {
                log.warn("[{}] {}.{}() 클라이언트 예외 | code: {}, message: {}",
                        layer,
                        className,
                        methodName,
                        serviceException.getErrorCode().getCode(),
                        serviceException.getErrorCode().getMessage()
                );
            } else {
                log.error("[{}] {}.{}() 서버 예외 | message: {}",
                        layer,
                        className,
                        methodName,
                        e.getMessage(),
                        e
                );
            }
            throw e;
        }
    }
}
