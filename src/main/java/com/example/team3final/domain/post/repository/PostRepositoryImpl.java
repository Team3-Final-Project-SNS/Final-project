package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    @SuppressWarnings("unused")
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public Page<Post> findAllForAdmin(PostStatus status, Boolean deleted, String keyword, Pageable pageable) {
        return searchPostsForAdmin(null, status, deleted, keyword, pageable);
    }

    @Override
    public Page<Post> findAllForAdminByAuthorIds(
            List<Long> authorIds,
            PostStatus status,
            Boolean deleted,
            String keyword,
            Pageable pageable
    ) {
        return searchPostsForAdmin(authorIds, status, deleted, keyword, pageable);
    }

    private Page<Post> searchPostsForAdmin(
            List<Long> authorIds,
            PostStatus status,
            Boolean deleted,
            String keyword,
            Pageable pageable
    ) {
        if (authorIds != null && authorIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        QueryParts queryParts = buildAdminSearchQuery(authorIds, status, deleted, keyword);

        Query contentQuery = entityManager.createNativeQuery(
                "SELECT * FROM posts " + queryParts.whereClause() + " ORDER BY created_at DESC",
                Post.class
        );
        Query countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM posts " + queryParts.whereClause()
        );

        queryParts.parameters().forEach((name, value) -> {
            contentQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Post> content = contentQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }

    private QueryParts buildAdminSearchQuery(
            List<Long> authorIds,
            PostStatus status,
            Boolean deleted,
            String keyword
    ) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        Map<String, Object> parameters = new HashMap<>();

        if (authorIds != null) {
            where.append(" AND author_id IN (:authorIds)");
            parameters.put("authorIds", authorIds);
        }

        if (status != null) {
            where.append(" AND status = :status");
            parameters.put("status", status.name());
        }

        if (deleted != null) {
            where.append(deleted ? " AND deleted_at IS NOT NULL" : " AND deleted_at IS NULL");
        }

        if (hasText(keyword)) {
            where.append(" AND place_name LIKE :keyword");
            parameters.put("keyword", "%" + keyword + "%");
        }

        return new QueryParts(where.toString(), parameters);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QueryParts(String whereClause, Map<String, Object> parameters) {
    }
}
