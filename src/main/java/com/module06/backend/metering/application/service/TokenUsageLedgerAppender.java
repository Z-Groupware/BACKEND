package com.module06.backend.metering.application.service;

import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 청구 원장(token_usage_record)에 사용량을 <b>멱등하게</b> 반영하는 단일 지점.
 *
 * 동기 기록 경로(TokenMeteringService.record)와 비동기 복구 경로(TokenUsageRelay)가
 * 모두 이 한 메서드를 통과한다 — 멱등 규칙이 두 곳에서 갈리지 않게 하기 위함이다.
 *
 * <h2>@Transactional 을 두지 않는다</h2>
 * append 는 단일 INSERT 뿐이고 멱등 보장은 job_id UNIQUE 제약이 한다. @Transactional 이면
 * 동시 중복(같은 job_id)에서 제약 위반이 트랜잭션을 rollback-only 로 마킹해, 예외를 잡아도
 * 커밋 시점에 UnexpectedRollbackException 이 터진다. 트랜잭션 없이 두면 save 는 자기
 * 트랜잭션에서 돌고, 위반 시 그 트랜잭션만 롤백하며 예외를 여기로 던지므로 깔끔히 처리된다.
 */
@Component
public class TokenUsageLedgerAppender {

    private final TokenUsageRecordRepository tokenUsageRecordRepository;

    public TokenUsageLedgerAppender(TokenUsageRecordRepository tokenUsageRecordRepository) {
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
    }

    /**
     * 원장에 반영한다.
     *
     * @return 새로 기록했으면 true, 이미 같은 job_id 가 있어 아무것도 하지 않았으면 false.
     * @throws DataIntegrityViolationException job_id 중복이 아닌 무결성 위반(NOT NULL 등) — 조용히
     *         삼키면 원장이 누락되므로 그대로 던진다. 호출부가 재시도(outbox)로 받는다.
     */
    public boolean append(TokenUsageRecord record) {
        if (tokenUsageRecordRepository.existsByJobId(record.getJobId())) {
            return false;
        }
        try {
            tokenUsageRecordRepository.save(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            // job_id UNIQUE 동시 중복이면 이미 기록됐다는 뜻 → 멱등 no-op.
            if (tokenUsageRecordRepository.existsByJobId(record.getJobId())) {
                return false;
            }
            throw e;
        }
    }
}
