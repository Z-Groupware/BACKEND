package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TokenUsageRecordPersistenceAdapter implements TokenUsageRecordRepository {

    private final SpringDataTokenUsageRecordRepository repository;

    public TokenUsageRecordPersistenceAdapter(SpringDataTokenUsageRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public TokenUsageRecord save(TokenUsageRecord record) {
        return repository.saveAndFlush(TokenUsageRecordJpaEntity.from(record)).toDomain();
    }

    @Override
    public boolean existsByJobId(String jobId) {
        return repository.existsByJobId(jobId);
    }

    @Override
    public long sumTotalTokens(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return repository
                .findByCompanyIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                        companyId, startInclusive, endExclusive)
                .stream()
                .mapToLong(e -> e.toDomain().getTotalTokens())
                .sum();
    }

    @Override
    public long sumTotalTokensByTeam(Long companyId, Long teamId, LocalDateTime startInclusive,
                                     LocalDateTime endExclusive) {
        return repository
                .findByCompanyIdAndTeamIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                        companyId, teamId, startInclusive, endExclusive)
                .stream()
                .mapToLong(e -> e.toDomain().getTotalTokens())
                .sum();
    }

    @Override
    public DirectionUsageAggregate sumDirectionTokens(Long companyId, LocalDateTime startInclusive,
                                                      LocalDateTime endExclusive) {
        long input = 0L;
        long output = 0L;
        for (TokenUsageRecordJpaEntity entity : repository
                .findByCompanyIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                        companyId, startInclusive, endExclusive)) {
            TokenUsageRecord record = entity.toDomain();
            input += record.getInputTokens();
            output += record.getOutputTokens();
        }
        return new DirectionUsageAggregate(input, output);
    }

    @Override
    public List<DepartmentUsageAggregate> sumTotalTokensByDepartment(Long companyId, LocalDateTime startInclusive,
                                                                    LocalDateTime endExclusive) {
        // teamId 는 nullable 이므로 Collectors.groupingBy(null 키 불가) 대신 수동 누적한다.
        // 방향별(입력·출력) 합계도 함께 모아 부서 단위 방향 단가 과금을 지원한다.
        Map<Long, long[]> byTeam = new LinkedHashMap<>();
        for (TokenUsageRecordJpaEntity entity : repository
                .findByCompanyIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                        companyId, startInclusive, endExclusive)) {
            TokenUsageRecord record = entity.toDomain();
            // [total, input, output]
            long[] acc = byTeam.computeIfAbsent(record.getTeamId(), k -> new long[3]);
            acc[0] += record.getTotalTokens();
            acc[1] += record.getInputTokens();
            acc[2] += record.getOutputTokens();
        }

        List<DepartmentUsageAggregate> aggregates = new ArrayList<>();
        byTeam.forEach((teamId, acc) ->
                aggregates.add(new DepartmentUsageAggregate(teamId, acc[0], acc[1], acc[2])));
        // teamId 오름차순 정렬(null 은 마지막) — 기존 JPQL order by teamId 계약 유지.
        aggregates.sort(Comparator.comparing(DepartmentUsageAggregate::teamId,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return aggregates;
    }
}
