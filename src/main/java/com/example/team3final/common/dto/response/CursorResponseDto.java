package com.example.team3final.common.dto.response;

import java.util.List;

/**
 * 커서 기반 무한 스크롤 공통 응답 래퍼
 *
 * @param content    현재 페이지 데이터 목록
 * @param hasNext    다음 페이지 존재 여부
 * @param nextCursor 다음 요청 시 사용할 커서값 (hasNext=false면 null)
 */
public record CursorResponseDto<T>(
        List<T> content,
        boolean hasNext,
        Long nextCursor
) {
    /**
     * 서비스에서 size+1개를 조회한 결과로 CursorResponse를 만드는 정적 팩토리 메서드
     *
     * @param rawContent size+1개 조회 결과
     * @param size       실제 반환할 개수
     * @param getId      마지막 항목의 id를 꺼내는 함수
     */
    public static <T> CursorResponseDto<T> of(List<T> rawContent, int size,
                                           java.util.function.Function<T, Long> getId) {
        boolean hasNext = rawContent.size() > size;

        // size+1개 중 실제 반환분만 자름
        List<T> content = hasNext ? rawContent.subList(0, size) : rawContent;

        // 다음 커서 = 현재 목록 마지막 항목의 id (없으면 null)
        Long nextCursor = hasNext ? getId.apply(content.get(content.size() - 1)) : null;

        return new CursorResponseDto<>(content, hasNext, nextCursor);
    }
}
