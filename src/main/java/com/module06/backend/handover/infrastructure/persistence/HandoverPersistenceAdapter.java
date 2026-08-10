package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class HandoverPersistenceAdapter implements HandoverRepository {

    private final SpringDataHandoverRepository springDataHandoverRepository;

    public HandoverPersistenceAdapter(SpringDataHandoverRepository springDataHandoverRepository) {
        this.springDataHandoverRepository = springDataHandoverRepository;
    }

    @Override
    public Handover save(Handover handover) {
        try {
            // saveAndFlush로 @Version 충돌을 이 try 안에서 즉시 유발한다. save()만 쓰면 flush가
            // 서비스 트랜잭션 커밋(어댑터 바깥) 시점으로 밀려 아래 catch를 못 잡고 500으로 샌다.
            HandoverJpaEntity saved = springDataHandoverRepository.saveAndFlush(HandoverJpaEntity.fromDomain(handover));
            return saved.toDomain();
        } catch (ObjectOptimisticLockingFailureException e) {
            // 갭14/15: 낙관적 락 충돌 → 409로 매핑.
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
    }

    @Override
    public boolean existsActiveByWriter(Long writerMemberId) {
        return springDataHandoverRepository.existsByWriterMemberIdAndStatusIn(
                writerMemberId, List.of(HandoverStatus.SUBMITTED, HandoverStatus.PENDING_ATTRIBUTION,
                        HandoverStatus.REASSIGNED));
    }

    @Override
    public Optional<Handover> findById(Long id) {
        return springDataHandoverRepository.findById(id).map(HandoverJpaEntity::toDomain);
    }

    @Override
    public List<Handover> findByWriterMemberId(Long writerMemberId) {
        return springDataHandoverRepository.findByWriterMemberId(writerMemberId).stream()
                .map(HandoverJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Handover> findByTeamId(Long teamId) {
        return springDataHandoverRepository.findByTeamId(teamId).stream()
                .map(HandoverJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Handover> findByTeamIdAndStatus(Long teamId, HandoverStatus status) {
        return springDataHandoverRepository.findByTeamIdAndStatus(teamId, status).stream()
                .map(HandoverJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Handover> findByWriterMemberIdInAndStatus(Collection<Long> writerMemberIds, HandoverStatus status) {
        if (writerMemberIds == null || writerMemberIds.isEmpty()) {
            return List.of();
        }
        return springDataHandoverRepository.findByWriterMemberIdInAndStatus(writerMemberIds, status).stream()
                .map(HandoverJpaEntity::toDomain)
                .toList();
    }
}
