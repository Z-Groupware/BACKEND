package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// domain의 CaptionChunkRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class CaptionChunkPersistenceAdapter implements CaptionChunkRepository {

    private final SpringDataCapCaptionChunkRepository springDataCapCaptionChunkRepository;

    public CaptionChunkPersistenceAdapter(SpringDataCapCaptionChunkRepository springDataCapCaptionChunkRepository) {
        this.springDataCapCaptionChunkRepository = springDataCapCaptionChunkRepository;
    }

    // 이미 존재하는 (meetingId, memberId, seq)는 재전송으로 보고 먼저 걸러낸 뒤, 새 조각만 배치 저장한다.
    // saveAndFlush + catch(DataIntegrityViolationException)로 개별 처리하면, 위반 이후 영속성 컨텍스트가
    // 오염돼("has a null identifier") 같은 트랜잭션 안의 다음 저장까지 깨진다 — 그래서 저장 전에 걸러낸다.
    // 동시 요청의 진짜 경쟁(같은 seq 동시 전송)은 남지만, UNIQUE 제약이 그 경우의 최종 방어선이다.
    @Override
    public List<CaptionChunk> saveAllSkippingDuplicates(List<CaptionChunk> chunks) {
        List<CaptionChunk> newChunks = chunks.stream()
                .filter(chunk -> !springDataCapCaptionChunkRepository.existsByMeetingIdAndMemberIdAndSeq(
                        chunk.getMeetingId(), chunk.getMemberId(), chunk.getSeq()))
                .toList();
        return springDataCapCaptionChunkRepository.saveAll(newChunks.stream()
                        .map(CapCaptionChunkJpaEntity::fromDomain)
                        .toList())
                .stream()
                .map(CapCaptionChunkJpaEntity::toDomain)
                .toList();
    }
}
