package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.model.ProjectStorageSummary;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class MeetingStorageUsagePersistenceAdapter implements MeetingStorageUsageRepository {

    private final SpringDataMeetingStorageUsageRepository repository;
    private final MeetingStorageUsageWriter writer;
    private final Clock clock;

    public MeetingStorageUsagePersistenceAdapter(SpringDataMeetingStorageUsageRepository repository,
                                                 MeetingStorageUsageWriter writer,
                                                 @Qualifier("meetingClock") Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
    }

    /*
     * 이 메서드 자체는 @Transactional이 아니다 — 실제 잠금·쓰기는 writer.applyWithLock(별도 빈,
     * 자체 트랜잭션)이 담당한다. 첫 report 두 개가 동시에 경합해 삽입이 충돌하면
     * (DataIntegrityViolationException) writer의 트랜잭션은 이미 깨끗하게 롤백된 상태로 예외가
     * 여기(트랜잭션 밖)까지 전파된다 — 여기서 다시 writer를 호출하면(새 트랜잭션) 이번엔 방금
     * 생긴 행을 잠그는 경로를 타 안전하게 재시도된다.
     */
    @Override
    public void reportIfNewer(MeetingStorageUsage usage) {
        try {
            writer.applyWithLock(usage);
        } catch (DataIntegrityViolationException e) {
            writer.applyWithLock(usage);
        }
    }

    @Override
    public void clearByCompanyIdAndProjectId(Long companyId, Long projectId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MeetingStorageUsageJpaEntity> cleared = repository.findByCompanyId(companyId).stream()
                .map(MeetingStorageUsageJpaEntity::toDomain)
                .filter(usage -> usage.getProjectId().equals(projectId))
                .map(usage -> MeetingStorageUsage.restore(usage.getMeetingId(), usage.getCompanyId(),
                        usage.getProjectId(), 0L, usage.getRevision() + 1, now))
                .map(MeetingStorageUsageJpaEntity::from)
                .toList();
        repository.saveAll(cleared);
    }

    // 회의 수만큼만 행이 있어서(TokenUsageRecord와 달리 이벤트마다 안 늘어남) Java 쪽에서 합산해도
    // TokenUsageRecordPersistenceAdapter.sumTotalTokens와 같은 비용 구조다.
    @Override
    public long sumUsedBytesByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
                .mapToLong(entity -> entity.toDomain().getUsedBytes())
                .sum();
    }

    /*
     * QUERY_002(CI Gate 1, 신규 @Query 금지)에 걸려 recording을 project로 조인하는 GROUP BY를 못
     * 쓴다 — findByCompanyId로 이미 회사 전체를 한 번에 읽어 오는 기존 경로를 그대로 재사용해
     * Java에서 프로젝트별로 묶는다(ActionPersistenceAdapter의 "프로젝션+자바 집계" 패턴과 동일).
     */
    @Override
    public List<ProjectStorageSummary> summarizeByProjectId(Long companyId) {
        List<MeetingStorageUsage> usages = repository.findByCompanyId(companyId).stream()
                .map(MeetingStorageUsageJpaEntity::toDomain)
                .toList();

        Map<Long, Long> bytesByProject = usages.stream()
                .collect(Collectors.groupingBy(MeetingStorageUsage::getProjectId,
                        Collectors.summingLong(MeetingStorageUsage::getUsedBytes)));

        // meetingCount·lastRecordedAt은 "지금 녹음이 남아있는" 회의만 센다(usedBytes=0은 삭제된
        // 회의) — ProjectStorageSummary 클래스 주석 참고.
        List<MeetingStorageUsage> withRecording = usages.stream()
                .filter(usage -> usage.getUsedBytes() > 0)
                .toList();
        Map<Long, Long> meetingCountByProject = withRecording.stream()
                .collect(Collectors.groupingBy(MeetingStorageUsage::getProjectId, Collectors.counting()));
        Map<Long, LocalDateTime> lastRecordedAtByProject = withRecording.stream()
                .collect(Collectors.groupingBy(MeetingStorageUsage::getProjectId,
                        Collectors.mapping(MeetingStorageUsage::getUpdatedAt,
                                Collectors.maxBy(Comparator.naturalOrder()))))
                .entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));

        return bytesByProject.keySet().stream()
                .map(projectId -> new ProjectStorageSummary(projectId, bytesByProject.get(projectId),
                        meetingCountByProject.getOrDefault(projectId, 0L),
                        lastRecordedAtByProject.get(projectId)))
                .toList();
    }
}
