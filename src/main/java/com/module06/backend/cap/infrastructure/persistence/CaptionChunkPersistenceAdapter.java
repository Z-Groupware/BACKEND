package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    // 배치 안에서도 같은 키가 중복되면(클라이언트 버그 등) DB 확인만으로는 못 걸러진다 — 아직 DB엔 없으니
    // 둘 다 통과했다가 saveAll에서 UNIQUE 위반으로 배치 전체가 죽는다. 그래서 배치 내부 중복도 먼저 제거한다.
    // 동시 요청의 진짜 경쟁(서로 다른 두 요청이 같은 seq를 동시 전송)은 남지만, UNIQUE 제약이 최종 방어선이다.
    @Override
    public List<CaptionChunk> saveAllSkippingDuplicates(List<CaptionChunk> chunks) {
        List<CaptionChunk> deduped = dedupeByKey(chunks);
        List<CaptionChunk> newChunks = deduped.stream()
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

    // 배치 내부에서 (meetingId, memberId, seq)가 겹치면 먼저 온 것만 남긴다.
    private List<CaptionChunk> dedupeByKey(List<CaptionChunk> chunks) {
        Map<String, CaptionChunk> byKey = new LinkedHashMap<>();
        for (CaptionChunk chunk : chunks) {
            String key = chunk.getMeetingId() + ":" + chunk.getMemberId() + ":" + chunk.getSeq();
            byKey.putIfAbsent(key, chunk);
        }
        return List.copyOf(byKey.values());
    }
}
