package com.example.team3final.domain.post.performance;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("performance-data")
class PostListPerformanceBulkInsert {

    private static final String UNIVERSITY_NAME = "성능테스트대학교";
    private static final String UNIVERSITY_DOMAIN = "perf.local";
    private static final String EMAIL_PREFIX = "perf-user-";
    private static final String EMAIL_SUFFIX = "@perf.local";
    private static final String RAW_PASSWORD = "password123!";

    private static final int USER_COUNT = 1_000;
    private static final int NORMAL_AUTHOR_COUNT = 240;
    private static final int ACTIVE_AUTHOR_COUNT = 50;
    private static final int HEAVY_AUTHOR_COUNT = 10;

    private static final int NORMAL_AUTHOR_POSTS = 10;
    private static final int ACTIVE_AUTHOR_POSTS = 80;
    private static final int HEAVY_AUTHOR_POSTS = 360;

    private static final int BATCH_SIZE = 500;
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();
    private static final String DB_URL = readSettingOrDefault(
            "jdbc:mysql://localhost:3306/hankipot?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true",
            "PERF_DB_URL",
            "TEST_DB_URL"
    );
    private static final String DB_USERNAME = readSettingOrDefault("root", "PERF_DB_USERNAME", "TEST_DB_USERNAME");
    private static final String DB_PASSWORD = readSettingOrDefault("12345678", "PERF_DB_PASSWORD", "TEST_DB_PASSWORD");

    private final JdbcTemplate jdbcTemplate = createJdbcTemplate();

    @Test
    void setupPerformanceData() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("perf.bulk-insert.enabled"),
                "성능 테스트 데이터 적재는 -Dperf.bulk-insert.enabled=true 설정 시에만 실행합니다."
        );
        validateLocalDatabaseUrl();

        deletePreviousPerformanceData();

        Long universityId = getOrCreatePerformanceUniversity();
        insertUsers(universityId);

        List<Long> authorIds = findAuthorIds();
        insertPosts(authorIds);

        printSummary();
    }

    private void deletePreviousPerformanceData() {
        jdbcTemplate.update("""
                DELETE FROM posts
                WHERE author_id IN (
                    SELECT user_id
                    FROM users
                    WHERE email LIKE ?
                )
                """, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);

        jdbcTemplate.update(
                "DELETE FROM users WHERE email LIKE ?",
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX
        );
    }

    private Long getOrCreatePerformanceUniversity() {
        List<Long> universityIds = jdbcTemplate.query(
                "SELECT id FROM universities WHERE e_domain = ?",
                (rs, rowNum) -> rs.getLong("id"),
                UNIVERSITY_DOMAIN
        );

        if (!universityIds.isEmpty()) {
            return universityIds.get(0);
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO universities (
                    university_name,
                    e_domain,
                    is_active,
                    deactivated_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                UNIVERSITY_NAME,
                UNIVERSITY_DOMAIN,
                true,
                null,
                Timestamp.valueOf(now)
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM universities WHERE e_domain = ?",
                Long.class,
                UNIVERSITY_DOMAIN
        );
    }

    private void insertUsers(Long universityId) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = passwordEncoder.encode(RAW_PASSWORD);
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> batchArgs = new ArrayList<>(USER_COUNT);
        for (int i = 1; i <= USER_COUNT; i++) {
            batchArgs.add(new Object[]{
                    email(i),
                    encodedPassword,
                    "성능테스트유저" + i,
                    "perfUser" + i,
                    universityId,
                    "컴퓨터공학과",
                    String.format("%02d", 20 + (i % 6)),
                    Date.valueOf(LocalDate.of(2000 + (i % 7), 1 + (i % 12), 1 + (i % 27))),
                    i % 2 == 0 ? "MALE" : "FEMALE",
                    10_000,
                    0,
                    "ACTIVE",
                    null,
                    null,
                    "36.5",
                    null,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            });
        }

        batchUpdateInChunks("""
                INSERT INTO users (
                    email,
                    password,
                    name,
                    nickname,
                    university_id,
                    major,
                    student_id_number,
                    birth_date,
                    gender,
                    free_point,
                    paid_point,
                    status,
                    suspended_until,
                    report_banned_until,
                    manner_temperature,
                    deleted_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batchArgs);
    }

    private List<Long> findAuthorIds() {
        return jdbcTemplate.query(
                """
                SELECT user_id
                FROM users
                WHERE email LIKE ?
                ORDER BY user_id
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getLong("user_id"),
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX,
                NORMAL_AUTHOR_COUNT + ACTIVE_AUTHOR_COUNT + HEAVY_AUTHOR_COUNT
        );
    }

    private void insertPosts(List<Long> authorIds) {
        if (authorIds.size() != NORMAL_AUTHOR_COUNT + ACTIVE_AUTHOR_COUNT + HEAVY_AUTHOR_COUNT) {
            throw new IllegalStateException("작성자 수가 부족합니다. actual=" + authorIds.size());
        }

        List<Object[]> batchArgs = new ArrayList<>(10_000);
        AtomicInteger sequence = new AtomicInteger(0);

        int authorOffset = 0;
        authorOffset = appendPosts(batchArgs, authorIds, authorOffset, NORMAL_AUTHOR_COUNT, NORMAL_AUTHOR_POSTS, sequence);
        authorOffset = appendPosts(batchArgs, authorIds, authorOffset, ACTIVE_AUTHOR_COUNT, ACTIVE_AUTHOR_POSTS, sequence);
        appendPosts(batchArgs, authorIds, authorOffset, HEAVY_AUTHOR_COUNT, HEAVY_AUTHOR_POSTS, sequence);

        batchUpdateInChunks("""
                INSERT INTO posts (
                    author_id,
                    meet_at,
                    place_name,
                    place_lat,
                    place_lng,
                    content,
                    author_deposit,
                    status,
                    max_applicants,
                    current_applicants,
                    delete_reason,
                    version,
                    deleted_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batchArgs);
    }

    private int appendPosts(
            List<Object[]> batchArgs,
            List<Long> authorIds,
            int authorOffset,
            int authorCount,
            int postsPerAuthor,
            AtomicInteger sequence
    ) {
        for (int authorIndex = 0; authorIndex < authorCount; authorIndex++) {
            Long authorId = authorIds.get(authorOffset + authorIndex);
            for (int postIndex = 0; postIndex < postsPerAuthor; postIndex++) {
                int postNumber = sequence.incrementAndGet();
                batchArgs.add(postArgs(authorId, postNumber));
            }
        }
        return authorOffset + authorCount;
    }

    private Object[] postArgs(Long authorId, int postNumber) {
        LocalDateTime now = LocalDateTime.now();
        String status = statusFor(postNumber);
        LocalDateTime meetAt = meetAtFor(status, postNumber);
        int authorDeposit = 100 + ((postNumber % 50) * 100);

        return new Object[]{
                authorId,
                Timestamp.valueOf(meetAt),
                "성능테스트 식당 " + (postNumber % 30),
                "37.3745300",
                "126.6322100",
                "성능 테스트용 게시글 " + postNumber,
                authorDeposit,
                status,
                2 + (postNumber % 4),
                currentApplicantsFor(status),
                null,
                0,
                null,
                Timestamp.valueOf(now.minusDays(postNumber % 120)),
                Timestamp.valueOf(now.minusDays(postNumber % 120))
        };
    }

    private String statusFor(int postNumber) {
        if (postNumber <= 1_500) {
            return "OPEN";
        }
        if (postNumber <= 3_000) {
            return "MATCHED";
        }
        if (postNumber <= 7_000) {
            return "COMPLETED";
        }
        if (postNumber <= 8_500) {
            return "CANCELLED";
        }
        return "EXPIRED";
    }

    private LocalDateTime meetAtFor(String status, int postNumber) {
        LocalDateTime now = LocalDateTime.now();
        if ("OPEN".equals(status) || "MATCHED".equals(status) || "CANCELLED".equals(status)) {
            return now.plusMinutes(30L + (postNumber % 1_440));
        }
        return now.minusMinutes(30L + (postNumber % 10_080));
    }

    private int currentApplicantsFor(String status) {
        if ("MATCHED".equals(status) || "COMPLETED".equals(status)) {
            return 2;
        }
        return 1;
    }

    private String email(int userNumber) {
        return EMAIL_PREFIX + String.format("%04d", userNumber) + EMAIL_SUFFIX;
    }

    private void batchUpdateInChunks(String sql, List<Object[]> batchArgs) {
        for (int start = 0; start < batchArgs.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, batchArgs.size());
            jdbcTemplate.batchUpdate(sql, batchArgs.subList(start, end));
        }
    }

    private void printSummary() {
        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email LIKE ?",
                Long.class,
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX
        );
        Long postCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM posts
                WHERE author_id IN (
                    SELECT user_id
                    FROM users
                    WHERE email LIKE ?
                )
                """,
                Long.class,
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX
        );

        System.out.println("=== 게시글 목록 성능 테스트 데이터 생성 완료 ===");
        System.out.println("users=" + userCount);
        System.out.println("posts=" + postCount);
        System.out.println("loginEmail=" + email(1));
        System.out.println("loginPassword=" + RAW_PASSWORD);
    }

    private static JdbcTemplate createJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(DB_URL);
        dataSource.setUsername(DB_USERNAME);
        dataSource.setPassword(DB_PASSWORD);
        return new JdbcTemplate(dataSource);
    }

    private static void validateLocalDatabaseUrl() {
        String lowerCaseUrl = DB_URL.toLowerCase();
        boolean isLocalDatabase = lowerCaseUrl.contains("localhost") || lowerCaseUrl.contains("127.0.0.1");
        boolean looksLikeRemoteDatabase = lowerCaseUrl.contains("amazonaws.com")
                || lowerCaseUrl.contains("rds")
                || lowerCaseUrl.contains("prod");

        if (!isLocalDatabase || looksLikeRemoteDatabase) {
            throw new IllegalStateException("성능 테스트 데이터는 로컬 Docker Compose/MySQL DB에만 적재할 수 있습니다. DB_URL=" + DB_URL);
        }
    }

    private static String readSettingOrDefault(String defaultValue, String... keys) {
        for (String key : keys) {
            String value = readSetting(key);
            if (value != null) {
                return value;
            }
        }

        return defaultValue;
    }

    private static String readRequiredSetting(String... keys) {
        for (String key : keys) {
            String value = readSetting(key);
            if (value != null) {
                return value;
            }
        }

        throw new IllegalStateException(String.join(" or ", keys) + " 환경변수 또는 시스템 프로퍼티가 필요합니다.");
    }

    private static String readSetting(String key) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }

        String environmentVariable = System.getenv(key);
        if (environmentVariable != null && !environmentVariable.isBlank()) {
            return environmentVariable;
        }

        String dotenvValue = DOTENV.get(key);
        if (dotenvValue != null && !dotenvValue.isBlank()) {
            return dotenvValue;
        }

        return null;
    }
}
