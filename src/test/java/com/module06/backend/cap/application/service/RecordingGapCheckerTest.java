package com.module06.backend.cap.application.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * RecordingAssemblyService(CAP-05)와 MeetingCompletedAssemblyTrigger(MEET-08) 양쪽이 공유하는
 * seq 연속성 검증 로직. RecordingAssemblyServiceTest가 이미 통합된 형태로 이 로직을 훑고 있지만,
 * 이제 독립된 컴포넌트가 됐으므로 경계값을 직접 검증한다.
 */
@DisplayName("녹음 seq 연속성 검사기")
class RecordingGapCheckerTest {

    @Test
    @DisplayName("단일 세그먼트가 1..lastSeq까지 연속이면 구멍이 없다")
    void 연속이면_구멍이_없다() {
        RecordingGapChecker checker = checker(Map.of(0, List.of(1, 2, 3)));

        assertThat(checker.hasGap(500L, 0, 3)).isFalse();
    }

    @Test
    @DisplayName("중간 순번이 빠지면 구멍이다")
    void 중간이_빠지면_구멍이다() {
        RecordingGapChecker checker = checker(Map.of(0, List.of(1, 2, 4)));

        assertThat(checker.hasGap(500L, 0, 4)).isTrue();
    }

    @Test
    @DisplayName("꼬리가 아직 안 올라왔으면 구멍이다")
    void 꼬리가_유실되면_구멍이다() {
        RecordingGapChecker checker = checker(Map.of(0, List.of(1, 2, 3)));

        assertThat(checker.hasGap(500L, 0, 5)).isTrue();
    }

    @Test
    @DisplayName("여러 세그먼트가 각각 연속이면 구멍이 없다 — 중간 세그먼트는 내부 연속성만 본다")
    void 여러_세그먼트가_각각_연속이면_구멍이_없다() {
        RecordingGapChecker checker = checker(Map.of(0, List.of(1, 2, 3), 1, List.of(1, 2)));

        assertThat(checker.hasGap(500L, 1, 2)).isFalse();
    }

    @Test
    @DisplayName("중간 세그먼트 내부에 구멍이 있으면 구멍이다")
    void 중간_세그먼트_내부_구멍도_잡는다() {
        RecordingGapChecker checker = checker(Map.of(0, List.of(1, 3), 1, List.of(1)));

        assertThat(checker.hasGap(500L, 1, 1)).isTrue();
    }

    @Test
    @DisplayName("lastSeq가 0이면(업로드 없음) 구멍 없이 통과한다")
    void lastSeq가_0이면_통과한다() {
        RecordingGapChecker checker = checker(Map.of());

        assertThat(checker.hasGap(500L, 0, 0)).isFalse();
    }

    private RecordingGapChecker checker(Map<Integer, List<Integer>> seqsBySegment) {
        RecordingPartRepository repository = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return seqsBySegment.getOrDefault(segmentSeq, List.of());
            }

            @Override
            public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq,
                                                                 int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        return new RecordingGapChecker(repository);
    }
}
