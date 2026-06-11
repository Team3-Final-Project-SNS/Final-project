package com.example.team3final.domain.user.repository;

import com.example.team3final.domain.user.entity.QUser;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    // QueryDSL 쿼리 생성을 담당하는 객체
    private final JPAQueryFactory queryFactory;

    // QueryDSL Q클래스
    private static final QUser user = QUser.user;

    // 관리자 사용자 목록 조회
    // 기존 UserRepository의 findAllByForAdmin 메서드명과 반환 타입을 유지
    // 서비스 계층에서는 기존처럼 Page<User>를 받아 PageResponseDto로 감싸므로,
    // QueryDSL로 변경해도 서비스 코드는 수정하지 않는다.
    @Override
    public Page<User> findAllByForAdmin(UserStatus status, String keyword, Pageable pageable) {
        // 동적 조건을 하나의 Predicate로 조립
        // status 또는 keyword가 null이면 해당 조건은 buildPredicate() 내부에서 제외
        Predicate predicate = buildPredicate(
                hasStatus(status),
                containsKeyword(keyword)
        );

        // 실제 사용자 목록 조회 쿼리
        // 기존 JPQL과 동일하게 status 필터와 keyword 검색 조건을 적용
        List<User> content = queryFactory
                .selectFrom(user)
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 개수 계산용 count 쿼리
        // 여기서는 쿼리 모양만 만들어두고, 아직 DB에 count 쿼리를 보내지는 않음,
        // 실제 count 조회는 PageableExecutionUtils가 "전체 개수를 알아야 한다"고 판단할 때만 실행
        JPAQuery<Long> countQuery = queryFactory
                .select(user.count())
                .from(user)
                .where(predicate);

        // Page 타입을 유지해서 기존 서비스의 PageResponseDto.from(Page) 흐름을 깨지 않기 위함
        return PageableExecutionUtils.getPage(
                content,
                pageable,
                () -> {
                    // PageableExecutionUtils가 count 조회가 필요하다고 판단한 경우에만 이 코드가 실행됨,
                    // 예를 들어 조회 결과만으로 마지막 페이지인지 판단할 수 없는 경우 count 쿼리를 실행
                    Long total = countQuery.fetchOne();
                    return total != null ? total : 0L;
                }
        );
    }

    // 여러 BooleanExpression 조건을 하나의 Predicate로 묶기
    // 각 조건 메서드는 조건이 필요 없을 때 null을 반환
    // BooleanBuilder는 null이 아닌 조건만 and 조건으로 연결
    private Predicate buildPredicate(BooleanExpression... expressions) {
        BooleanBuilder builder = new BooleanBuilder();

        for (BooleanExpression expression : expressions) {
            if (expression != null) {
                builder.and(expression);
            }
        }

        return builder;
    }

    // 사용자 상태 조건
    // status가 null이면 전체 상태 조회이므로 조건을 적용하지 않음,
    // status가 존재하면 u.status = :status 조건을 적용
    private BooleanExpression hasStatus(UserStatus status) {
        return status != null ? user.status.eq(status) : null;
    }

    // 관리자 사용자 검색 키워드 조건
    // keyword가 null 또는 blank면 검색 조건을 적용하지 않음,
    // keyword가 존재하면 이름, 닉네임, 이메일 중 하나라도 포함되는 사용자를 조회
    private BooleanExpression containsKeyword(String keyword) {
        if (!hasText(keyword)) {
            return null;
        }

        return user.name.contains(keyword)
                .or(user.nickname.contains(keyword))
                .or(user.email.contains(keyword));
    }

    // 문자열 검색 조건 적용 여부 판단
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
