package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.handover.domain.model.HandoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpringDataHandoverRepository extends JpaRepository<HandoverJpaEntity, Long> {

    List<HandoverJpaEntity> findByWriterMemberId(Long writerMemberId);

    List<HandoverJpaEntity> findByWriterMemberIdIn(Collection<Long> writerMemberIds);

    List<HandoverJpaEntity> findByTeamId(Long teamId);

    List<HandoverJpaEntity> findByTeamIdAndStatus(Long teamId, HandoverStatus status);

    boolean existsByWriterMemberIdAndStatusIn(Long writerMemberId, Collection<HandoverStatus> statuses);
}
