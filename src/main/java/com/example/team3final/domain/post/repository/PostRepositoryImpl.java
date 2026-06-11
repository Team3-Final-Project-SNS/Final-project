package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.entity.QPost;
import com.example.team3final.domain.post.enums.PostStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom{

    // QueryDSL 쿼리 생성을 담당하는 객체,
    // QueryDslConfig에서 Bean으로 등록된 JPAQueryFactory 주입
    private final JPAQueryFactory queryFactory;

    // QueryDSL Q클래스
    private static final QPost post = QPost.post;


    // 관리자 게시글 전체 조회
    // PostRepository의 findAllForAdmin 메서드명과 반환 타입을 유지
    // 서비스 계층 코드를 수정하지 않기 위해 외부 시그니처는 그대로 두고,
    // 실제 QueryDSL 조회 로직은 searchPostsForAdmin() 공통 메서드에 위임
    @Override
    public Page<Post> findAllForAdmin(PostStatus status, String keyword, Pageable pageable) {
        return searchPostsForAdmin(null, status, keyword, pageable);
    }

    // 관리자 게시글 조회 - 작성자 ID 목록 필터 포함
    // universityId 또는 authorNickname 조건은 서비스 계층에서 authorIds로 변환된 뒤 전달되는데,
    // 이 메서드도 외부 시그니처는 그대로 유지하고, 내부 공통 조회 메서드에 위임
    @Override
    public Page<Post> findAllForAdminByAuthorIds(
            List<Long> authorIds,
            PostStatus status,
            String keyword,
            Pageable pageable
    ) {
        return searchPostsForAdmin(authorIds, status, keyword, pageable);
    }

    // 관리자 게시글 조회 공통 QueryDSL 메서드
    // findAllForAdmin()과 findAllForAdminByAuthorIds()의 실제 조회 로직을 하나로 묶은 것,
    // 두 메서드는 authorIds 조건 유무만 다르고 나머지 조건, 정렬, 페이징, 카운트 로직은 동일
    private Page<Post> searchPostsForAdmin(
            List<Long> authorIds,
            PostStatus status,
            String keyword,
            Pageable pageable
    ) {

        // authorIds가 null이면 작성자 필터를 적용하지 않음
        // authorIds가 빈 리스트라면 조회 대상이 없다는 의미이므로 DB 쿼리를 실행하지 않고 빈 Page를 반환
        if (authorIds != null && authorIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 동적 조건을 하나의 Predicate로 조립
        // null인 조건은 buildPredicate() 내부에서 제외
        Predicate predicate = buildPredicate(
                hasAuthorIds(authorIds),
                hasStatus(status),
                containsPlaceName(keyword)
        );

        // 실제 데이터 조회 쿼리
        // 기존 JPQL과 동일하게 createdAt DESC 정렬을 유지
        List<Post> content = queryFactory
                .selectFrom(post)
                .where(predicate)
                .orderBy(post.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 개수 계산용 count 쿼리
        // 여기서는 쿼리 모양만 만들어두고, 아직 DB에 count 쿼리를 보내지는 않음,
        // 실제 count 조회는 PageableExecutionUtils가 "전체 개수를 알아야 한다"고 판단할 때만 실행
        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(predicate);

        // Page 타입을 유지해서 기존 서비스의 PageResponseDto.from(Page) 흐름을 깨지 않기 위함
        return PageableExecutionUtils.getPage(
                content,
                pageable,
                () -> {
                    // PageableExecutionUtils가 count 조회가 필요하다고 판단한 경우에만 이 코드가 실행됨,
                    // 예를 들어 조회 결과만으로 마지막 페이지인지 판단할 수 없는 경우 count 쿼리를 실행
                    Long total = countQuery.fetchOne();
                    return total != null ? total : 0;
                }
        );
    }

    // 여러 BooleanExpression 조건을 하나의 Predicate로 묶기
    // BooleanExpression... expressions -> Java의 가변 인자(varargs) 문법
    // 이렇게 선언하면 메서드를 호출할 때 BooleanExpression을 여러 개 넘길 수 있음
    // 컴파일러 입장에서는 이게 내부적으로 배열처럼 처리됨
    // ex) BooleanExpression[] expressions
    private Predicate buildPredicate(BooleanExpression... expressions) {
        BooleanBuilder builder = new BooleanBuilder();

        for (BooleanExpression expression : expressions) {

            if (expression != null) {
                builder.and(expression);
            }
        }

        return builder;
    }

    // 작성자 ID 목록 조건
    // authorIds가 null이면 필터를 적용하지 않음
    // authorIds가 존재하면 p.authorId IN (:authorIds) 조건을 적용
    private BooleanExpression hasAuthorIds(List<Long> authorIds) {
        return authorIds != null ? post.authorId.in(authorIds) : null;
    }

    // 게시글 상태 조건
    // status가 null이면 전체 상태 조회이므로 조건을 적용하지 않음
    private BooleanExpression hasStatus(PostStatus status) {
        return status != null ? post.status.eq(status) : null;
    }

    // 장소명 키워드 검색 조건
    // keyword가 null 또는 blank면 검색 조건을 적용하지 않음,
    // 기존 JPQL의 p.placeName LIKE %:keyword% 와 같은 역할
    private BooleanExpression containsPlaceName(String keyword) {
        return hasText(keyword) ? post.placeName.contains(keyword) : null;
    }

    // 문자열 검색 조건 적용 여부 판단
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

// 기존에는 findAllForAdmin과 findAllForAdminByAuthorIds가 JPQL로 따로 존재했는데,
// 두 쿼리는 사실상 authorIds 조건이 있느냐 없느냐만 다르고, status, keyword, 정렬, 페이징, count 처리는 거의 같았음
// 그래서 외부 메서드명은 그대로 유지하고, 내부에서 searchPostsForAdmin() 하나로 모은 구조
