-- 운영 환경은 ddl-auto=validate이므로 애플리케이션 배포 전에 이 SQL을 먼저 실행해야 한다.
-- 기존 거래는 출처가 모호할 수 있어 위험한 일괄 backfill을 하지 않고, 신규 최종 정산부터 강제한다.

ALTER TABLE point_transactions
    ADD COLUMN reference_type VARCHAR(20) NULL
        COMMENT 'MATCH | POST | PAYMENT' AFTER match_id,
    ADD COLUMN reference_id BIGINT NULL
        COMMENT '참조 도메인 객체 ID' AFTER reference_type,
    ADD COLUMN settlement_reason VARCHAR(30) NULL
        COMMENT 'APPLICANT_DEPOSIT | AUTHOR_DEPOSIT (최종 정산만)' AFTER reference_id,
    ADD INDEX idx_point_transactions_reference (reference_type, reference_id);

-- 과거 JPA 자동 생성 DB에만 존재할 수 있는 기존 UNIQUE 인덱스를 조건부로 제거한다.
SET @drop_legacy_index = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE point_transactions DROP INDEX uk_point_tx_user_match_type',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'point_transactions'
      AND index_name = 'uk_point_tx_user_match_type'
);
PREPARE drop_legacy_index_stmt FROM @drop_legacy_index;
EXECUTE drop_legacy_index_stmt;
DEALLOCATE PREPARE drop_legacy_index_stmt;

-- transaction_type을 키에서 제외해 같은 책임비에 환급과 패널티가 동시에 기록되는 것도 막는다.
ALTER TABLE point_transactions
    ADD CONSTRAINT uk_point_tx_settlement
        UNIQUE (user_id, reference_type, reference_id, settlement_reason);
