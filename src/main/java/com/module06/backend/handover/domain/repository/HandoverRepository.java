package com.module06.backend.handover.domain.repository;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;

import java.util.List;
import java.util.Optional;

public interface HandoverRepository {

    Handover save(Handover handover);

    Optional<Handover> findById(Long id);

    List<Handover> findByWriterMemberId(Long writerMemberId);

    List<Handover> findByTeamIdAndStatus(Long teamId, HandoverStatus status);

    /** 갭2: 작성자에게 활성(SUBMITTED/REASSIGNED) handover가 이미 있는지. */
    boolean existsActiveByWriter(Long writerMemberId);
}
