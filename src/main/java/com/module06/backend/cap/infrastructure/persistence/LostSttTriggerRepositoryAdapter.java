package com.module06.backend.cap.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.LostSttTriggerRepository;

/* comment.
    LostSttTriggerRepository 를 JPA 로 구현한다(#574).

    두 조건을 두 번의 조회로 나눠 본다 — 시간 범위로 후보를 좁힌 뒤, 그 후보에 대해서만
    stt_block 유무를 확인한다. 조인 한 방으로 줄이려면 @Query 가 필요한데(QUERY_002),
    후보 수가 limit 으로 이미 묶여 있어 그럴 값어치가 없다. 유예·상한 사이에 등록된 녹음은
    많아야 하루치이고, 그중 대부분은 정상이라 첫 조회에서 걸러진다.

    ⚠ existsByMeetingId 를 후보마다 부르므로 후보 수만큼 쿼리가 나간다. limit 을 크게 잡으면
    그만큼 늘어난다 — 서비스가 한 주기 상한을 작게 유지하는 이유 중 하나다.
*/
@Repository
public class LostSttTriggerRepositoryAdapter implements LostSttTriggerRepository {

    private final SpringDataCapRecordingRepository recordingRepository;
    private final SpringDataCapSttBlockReferenceRepository sttBlockRepository;

    public LostSttTriggerRepositoryAdapter(SpringDataCapRecordingRepository recordingRepository,
                                           SpringDataCapSttBlockReferenceRepository sttBlockRepository) {
        this.recordingRepository = recordingRepository;
        this.sttBlockRepository = sttBlockRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recording> findSttTriggeredWithoutBlocks(LocalDateTime createdFrom, LocalDateTime createdUntil,
                                                        int limit) {
        if (createdFrom == null || createdUntil == null || limit <= 0 || !createdFrom.isBefore(createdUntil)) {
            /*
             * 범위가 뒤집혔거나 비어 있으면 조회하지 않는다. 그대로 넘기면 Between 이 빈 결과를
             * 내므로 동작은 같지만, 설정을 잘못 넣은 것(유예 > 상한)이 "재시도가 그냥 안 도는"
             * 조용한 상태로 굳는다 — 그건 이 이슈가 고치려는 실패 모양 그 자체다.
             */
            return List.of();
        }

        return recordingRepository
                .findBySttTriggeredTrueAndCreatedAtBetweenOrderByCreatedAtAsc(
                        createdFrom, createdUntil, PageRequest.of(0, limit))
                .stream()
                .map(CapRecordingJpaEntity::toDomain)
                .filter(recording -> !sttBlockRepository.existsByMeetingId(recording.getMeetingId()))
                .toList();
    }
}
