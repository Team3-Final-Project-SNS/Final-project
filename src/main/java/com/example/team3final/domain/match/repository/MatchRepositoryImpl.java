package com.example.team3final.domain.match.repository;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.entity.QMatch;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.post.entity.QPost;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class MatchRepositoryImpl implements MatchRepositoryCustom {

    // QueryDSL 쿼리를 만드는 팩토리 - EntityManager를 내부적으로 사용
    private final JPAQueryFactory queryFactory;

    private static final QMatch match = QMatch.match;
    private static final QPost post = QPost.post;

    // 내 매칭 목록 전체 조회 (상태 필터 없음)
    @Override
    public Page<Match> findAllByUserId(Long userId, Pageable pageable) {

        // ===== 데이터 조회 쿼리 =====
        List<Match> content = queryFactory
                .selectFrom(match)
                .join(post)
                .on(
                        match.postId.eq(post.id),
                        post.deletedAt.isNull()
                )
                .where(isParticipant(userId))
                .orderBy(match.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // ===== 카운트 쿼리 (페이징 total 계산용) =====
        Long total = queryFactory
                .select(match.count())
                .from(match)
                .join(post)
                .on(
                        match.postId.eq(post.id),
                        post.deletedAt.isNull()
                )
                .where(isParticipant(userId))
                .fetchOne();

        // total이 null인 경우(결과 없음) 0으로 처리
        long totalCount = (total != null) ? total : 0L;

        // PageImpl: content(데이터) + pageable(페이지 정보) + total(전체 건수) 조합
        return new PageImpl<>(content, pageable, totalCount);
    }

    // 내 매칭 목록 상태 필터 조회
    @Override
    public Page<Match> findAllByUserIdAndStatus(Long userId, MatchStatus status, Pageable pageable) {

        // ===== 데이터 조회 쿼리 =====
        List<Match> content = queryFactory
                .selectFrom(match)
                .join(post)
                .on(
                        match.postId.eq(post.id),
                        post.deletedAt.isNull()
                )
                .where(
                        isParticipant(userId),
                        hasStatus(status)
                )
                .orderBy(match.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // ===== 카운터 쿼리 =====
        Long total = queryFactory
                .select(match.count())
                .from(match)
                .join(post)
                .on(
                        match.postId.eq(post.id),
                        post.deletedAt.isNull()
                )
                .where(
                        isParticipant(userId),
                        hasStatus(status)
                )
                .fetchOne();

        long totalCount = (total != null) ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    /**
     * 내가 등록자이거나 신청자인 매칭 조건
     * SQL: (p.author_id = :userId OR m.applicant_id = :userId)
     */
    private BooleanExpression isParticipant(Long userId) {
        return post.authorId.eq(userId)
                .or(match.applicantId.eq(userId));
    }

    /**
     * 매칭 상태 필터 조건
     * SQL: m.status = :status
     *
     * [null 처리]
     *   status가 null이면 null을 반환 → QueryDSL이 where절에서 해당 조건을 자동으로 무시함
     *   → 만약 전체 조회(status=null) 케이스를 이 메서드로 처리하고 싶다면 그대로 활용 가능
     */
    private BooleanExpression hasStatus(MatchStatus status) {
        // status가 null이면 null 반환 (조건 무시)
        return (status != null) ? match.status.eq(status) : null;
    }
}
