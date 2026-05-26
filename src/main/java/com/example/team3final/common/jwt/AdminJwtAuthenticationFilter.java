package com.example.team3final.common.jwt;

import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.admin.security.AdminDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AdminDetailsService adminDetailsService;

    // /api/v1/admin/** 경로가 아닌 요청은 이 필터를 완전히 건너뜀
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Authorization 헤더에서 토큰 추출
        String token = resolveToken(request);

        // 토큰이 존재하고 유효하면 인증 처리
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {

            // 토큰 타입이 "ADMIN_ACCESS"인지 확인
            // 일반 유저 ACCESS 토큰으로 Admin API 호출하는 것을 차단
            String tokenType = jwtProvider.getTokenType(token);
            if (!"ADMIN_ACCESS".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰에서 이메일 추출
            String email = jwtProvider.getEmailFromToken(token);

            // Admin DB에서 관리자 정보 조회 -> AdminDetailsImpl 반환
            AdminDetailsImpl adminDetails = adminDetailsService.loadAdminByEmail(email);

            // 인증 객체 생성 및 SecurityContext에 저장
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    adminDetails, null, adminDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터로 전달
        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 "Bearer " 제거 후 실제 토큰만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
