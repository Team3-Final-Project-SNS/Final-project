package com.example.team3final.common.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain // 다음 필터로 넘기는 체인
    ) throws ServletException, IOException {

        // 1. Authorization 헤더에서 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 존재하고 유효하면 인증 처리
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {

            // 3. 토큰 타입이 ACCESS인지 확인 (REFRESH토큰으로 API 호출 방지)
            String tokenType = jwtProvider.getTokenType(token);
            if (!"ACCESS".equals(tokenType)) {
                // 타입이 access가 아니면 인증하지 않고 다음 필터로 넘어감
                filterChain.doFilter(request, response);
                return;
            }

            // 4. 토큰에서 이메일 추출
            String email = jwtProvider.getEmailFromToken(token);

            // 5. 이메일로 UserDetails 조회 (DB에서 실제 유저 정보 가져옴)
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // 6. 인증 객체 생성 - Spring Security가 이 객체로 "인증된 사용자"를 인식
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            // 7. 요청 정보를 인증 객체에 추가 (IP, 세션 등 추가 정보)
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 8. SecurityContext에 인증 정보 저장 — 이후 컨트롤러에서 @AuthenticationPrincipal로 꺼낼 수 있음
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 9. 다음 필터로 요청 전달 (인증 여부와 관계없이 항상 실행)
        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 "Bearer {token}" 형식의 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // "Bearer "로 시작하면 "Bearer " 이후의 실제 토큰 값만 잘라서 반환
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " => 7글자
        }
        return null;

    }
}
