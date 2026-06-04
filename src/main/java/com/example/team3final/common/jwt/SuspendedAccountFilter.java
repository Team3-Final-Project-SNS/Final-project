package com.example.team3final.common.jwt;

import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SuspendedAccountFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // AntPathMatcher: /api/v1/inquiries/{inquiryId} 같은 패턴을 실제 URL과 매칭
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // SUSPENDED 계정에게 허용할 경로 화이트리스트
    // 이 목록에 없는 모든 경로는 자동으로 차단됨
    // → 새 API가 추가돼도 명시적으로 여기에 넣지 않으면 정지 계정은 접근 불가 (안전한 기본값)
    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/v1/users/me",              // 내 정보 조회
            "/api/v1/inquiries",             // 문의 접수 (POST — 카테고리 제한은 서비스에서)
            "/api/v1/inquiries/me",          // 내 문의 목록 조회
            "/api/v1/inquiries/{inquiryId}"  // 내 문의 상세 조회
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 현재 요청의 인증 정보 꺼내기
        // JwtAuthenticationFilter가 먼저 실행되어 SecurityContext에 저장한 값
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. 인증되지 않은 요청이거나 principal이 UserDetailsImpl이 아니면 필터 통과
        // (비로그인 요청, Admin 요청 등은 이 필터가 건드리지 않음)
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. SUSPENDED 계정인지 확인
        // ACTIVE 계정은 이 필터를 완전히 통과
        if (userDetails.getStatus() != UserStatus.SUSPENDED) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. SUSPENDED 계정 — 현재 요청 경로가 화이트리스트에 있는지 확인
        String requestUri = request.getRequestURI();

        boolean isAllowed = ALLOWED_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));

        if (isAllowed) {
            // 화이트리스트에 있는 경로 → 통과 (카테고리 제한은 InquiryServiceImpl에서 처리)
            filterChain.doFilter(request, response);
            return;
        }

        // 5. 화이트리스트에 없는 경로 → 403 응답
        // GlobalExceptionHandler를 거치지 않으므로 여기서 직접 JSON 응답 작성
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // 기존 ApiResponseDto 형식에 맞춰 응답 (프론트와 형식 통일)
        Map<String, Object> body = Map.of(
                "success", false,
                "message", "정지된 계정은 해당 기능을 이용할 수 없습니다.",
                "code", "SUSPENDED_001"
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}