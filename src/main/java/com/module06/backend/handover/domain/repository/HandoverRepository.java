package com.module06.backend.handover.domain.repository;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HandoverRepository {

    Handover save(Handover handover);

    Optional<Handover> findById(Long id);

    List<Handover> findByWriterMemberId(Long writerMemberId);

    /** 목록 스코프 A안(오너·어드민 회사 전체): 회사 멤버 id 집합이 작성한 인수인계 조회. */
    List<Handover> findByWriterMemberIdIn(Collection<Long> writerMemberIds);

    List<Handover> findByTeamId(Long teamId);

    List<Handover> findByTeamIdAndStatus(Long teamId, HandoverStatus status);

    /** 갭2: 작성자에게 활성(SUBMITTED/REASSIGNED) handover가 이미 있는지. */
    boolean existsActiveByWriter(Long writerMemberId);
}
