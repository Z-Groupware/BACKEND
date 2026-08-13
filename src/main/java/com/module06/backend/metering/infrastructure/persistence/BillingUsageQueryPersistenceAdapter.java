package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.repository.BillingUsageQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class BillingUsageQueryPersistenceAdapter implements BillingUsageQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long sumRecordingBytes(Long companyId) {
        String sql = """
                SELECT COALESCE(SUM(r.file_size), 0)
                FROM recording r
                JOIN meeting m ON m.id = r.meeting_id
                WHERE m.company_id = :companyId
                """;
        return numberResult(sql, companyId);
    }

    @Override
    public long sumCaptionAndSummaryBytes(Long companyId) {
        String sql = """
                SELECT
                    COALESCE((
                        SELECT SUM(OCTET_LENGTH(cc.text))
                        FROM caption_chunk cc
                        JOIN meeting m ON m.id = cc.meeting_id
                        WHERE m.company_id = :companyId
                    ), 0)
                    +
                    COALESCE((
                        SELECT SUM(OCTET_LENGTH(ms.overview))
                        FROM meeting_summary ms
                        WHERE ms.company_id = :companyId
                    ), 0)
                """;
        return numberResult(sql, companyId);
    }

    @Override
    public long countMeetings(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        String sql = """
                SELECT COUNT(*)
                FROM meeting m
                WHERE m.company_id = :companyId
                  AND m.start_at >= :startInclusive
                  AND m.start_at < :endExclusive
                """;
        Object value = entityManager.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    private long numberResult(String sql, Long companyId) {
        Object value = entityManager.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .getSingleResult();
        return ((Number) value).longValue();
    }
}
