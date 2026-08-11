package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.CustomVocabularyPort;
import com.module06.backend.capture.application.port.out.CustomVocabularyPort.VocabularyState;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttBlockRepository;

/*
 * 커스텀 어휘의 뒷단 — **완료 확인(승격)과 정리(삭제)**.
 *
 * 스텁 주석이 "READY 로 옮기는 것은 제공자 완료 확인(콜백·폴링)의 몫이고 그게 아직 없다"고
 * 적어 둔 자리, 그리고 포트 주석이 "회의 종료 시 정리 트리거가 후속이라 지금은 만든 어휘가
 * 계속 쌓인다"고 적어 둔 자리 둘이다.
 *
 * <h2>정리가 선택이 아니다</h2>
 * 계정당 어휘 개수 상한이 있다. 정리하지 않으면 상한에 걸려 **신규 회의가 어휘 없이 돌게 되는데,
 * 그 회의들은 아무 오류 없이 그냥 인식률만 낮아진다** — 상한에 걸렸다는 사실이 한참 뒤에야
 * "왜 이 회의만 어휘가 없지"로 드러난다.
 *
 * <h2>받아쓰기가 끝나기 전에는 지우지 않는다</h2>
 * 제출된 STT 잡이 어휘 이름을 참조한다. 도는 중에 지우면 그 잡이 어휘 없이 돌거나 실패한다 —
 * 회의 하나 분량의 인식률이 조용히 떨어지는 경로다. 그래서 미완 블록이 하나라도 있으면 미룬다.
 *
 * <h2>블록이 아예 없는 회의는 지우지 않는다</h2>
 * 아직 녹음하지 않은 예약 회의다. 어휘는 그 회의를 위해 만든 것이고 지우면 정작 필요할 때 없다.
 * ⚠ 그래서 **끝내 녹음되지 않은 회의의 어휘는 남는다** — 회의 상태(D 소유)를 봐야 지울 수 있고,
 * 지금은 그 정보가 이쪽에 없다. 상한이 실제로 문제가 되면 그 조회를 여는 것이 다음 수순이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyLifecycleService {

    /* 한 주기에 볼 건수. 상한이 없으면 밀린 작업이 많을 때 한 주기가 끝나지 않는다. */
    private static final int BATCH_LIMIT = 20;

    /* 화면이 이 코드로 문구를 고른다. 제공자 메시지가 아니다. */
    private static final String ERROR_PROVIDER_FAILED = "PROVIDER_FAILED";
    private static final String ERROR_NOT_FOUND = "VOCABULARY_NOT_FOUND";

    /* 응답 없이 이만큼 지나면 포기한다. 명세가 "몇 분 걸린다"고 적은 작업이라 넉넉히 잡는다. */
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(30);

    /* 화면이 이 코드로 문구를 고른다. */
    private static final String ERROR_TIMEOUT = "BUILD_TIMEOUT";

    private final MeetingVocabularyRepository meetingVocabularyRepository;
    private final CustomVocabularyPort customVocabularyPort;

    /* 정리 시점을 가르는 값 — 받아쓰기가 아직 도는 중이면 어휘를 지우면 안 된다. */
    private final SttBlockRepository sttBlockRepository;

    /* 포기 판정의 기준 시각. 프로젝트 전체에 Clock 빈이 하나뿐이라 타입으로 주입된다. */
    private final Clock clock;

    /*
     * 만들어지는 중인 어휘를 확인해 승격하거나 실패로 닫는다.
     *
     * @return 이번에 끝맺은 건수(승격 + 실패)
     */
    public int promoteReadyOnce() {
        List<VocabularyView> pending = meetingVocabularyRepository.findPendingBuilds(BATCH_LIMIT);
        int settled = 0;
        for (VocabularyView vocabulary : pending) {
            try {
                if (settle(vocabulary)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                // 어휘 하나 때문에 나머지가 밀리면 안 된다(폴링 워커와 같은 규칙).
                log.error("어휘 완료 확인 실패 — meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.pendingVocabularyName(), e);
            }
        }
        return settled;
    }

    private boolean settle(VocabularyView vocabulary) {
        String pendingName = vocabulary.pendingVocabularyName();
        VocabularyState state = customVocabularyPort.stateOf(pendingName);
        return switch (state) {
            /*
             * 전이는 전부 **폴링한 이름을 함께 넘긴다.** 그 사이 재생성이 새 빌드를 접수했으면
             * 저장소가 물러난다 — 옛 결과로 승격하면 만들어지지도 않은 리소스가 활성이 되고,
             * 옛 결과로 닫으면 방금 접수된 빌드가 버려진다(CodeRabbit PR #353 지적).
             */
            case READY -> promote(vocabulary, pendingName);
            case FAILED -> {
                log.warn("어휘 생성 실패 — meetingId={} resource={}", vocabulary.meetingId(), pendingName);
                yield meetingVocabularyRepository.markBuildFailedIfPending(
                        vocabulary.id(), pendingName, ERROR_PROVIDER_FAILED);
            }
            /*
             * 제공자가 그 이름을 모른다. **실패로 닫는다** — 그대로 두면 영원히 PENDING 이고,
             * 선점이 PENDING 을 막으므로 사람이 다시 누를 수도 없다(markBuildFailed 주석).
             */
            case UNKNOWN -> {
                log.warn("어휘를 제공자가 모른다 — 우리 상태와 어긋났다. meetingId={} resource={}",
                        vocabulary.meetingId(), pendingName);
                yield meetingVocabularyRepository.markBuildFailedIfPending(
                        vocabulary.id(), pendingName, ERROR_NOT_FOUND);
            }
            // 아직 만드는 중이거나 못 읽었다. 상태를 바꾸지 않고 다음 주기에 다시 본다.
            case PENDING, UNAVAILABLE -> false;
        };
    }

    /*
     * 응답 없이 오래 걸린 빌드를 포기한다.
     *
     * <h2>왜 필요한가 — 두 가지가 함께 막힌다</h2>
     * 제공자가 계속 PENDING 을 답하면 그 회의는 영원히 "만드는 중"이고, 선점이 PENDING 을
     * 막으므로 **사람이 다시 누를 수도 없다.** 게다가 폴링 배치가 id 순 선두 몇 건만 보므로
     * 멈춘 빌드가 뒤 항목의 승격을 계속 미룬다(CodeRabbit PR #353 지적).
     *
     * 상한을 넉넉히 잡는다 — 명세가 "몇 분 걸린다"고 적은 작업이고, 짧게 잡으면 정상적으로
     * 만들어지는 중인 어휘를 포기해 사람이 다시 눌러 **리소스가 하나 더 만들어진다.**
     * 오탐의 대가가 더 크므로 한쪽으로만 틀리게 한다(LayerLiveness 와 같은 판단).
     *
     * @return 포기한 건수
     */
    public int abandonStuckOnce() {
        List<VocabularyView> stuck = meetingVocabularyRepository.findStuckBuilds(
                LocalDateTime.now(clock).minus(BUILD_TIMEOUT), BATCH_LIMIT);

        int abandoned = 0;
        for (VocabularyView vocabulary : stuck) {
            try {
                if (meetingVocabularyRepository.markBuildFailedIfPending(
                        vocabulary.id(), vocabulary.pendingVocabularyName(), ERROR_TIMEOUT)) {
                    log.warn("어휘 생성 포기 — {}분을 넘겼다. meetingId={} resource={}",
                            BUILD_TIMEOUT.toMinutes(), vocabulary.meetingId(),
                            vocabulary.pendingVocabularyName());
                    abandoned++;
                }
            } catch (RuntimeException e) {
                log.error("어휘 포기 처리 실패 — meetingId={}", vocabulary.meetingId(), e);
            }
        }
        return abandoned;
    }

    /*
     * 승격한다 — **여기서 지우지 않는다.**
     *
     * 처음에는 밀려난 이전 리소스를 이 자리에서 곧바로 지웠는데, 두 가지가 깨졌다
     * (CodeRabbit PR #353 지적):
     *   · 삭제가 실패하면 다시 시도할 이름이 없다. 승격이 활성 칸을 덮었으므로 그 이름은
     *     어디에도 남지 않는다 — V5.19 가 경고한 계정 상한 누수가 정확히 이 모양이다.
     *   · **아직 도는 STT 잡이 그 이름을 참조할 수 있다.** 정리 경로는 "받아쓰기가 끝났나"를
     *     보고 지우는데, 승격 경로만 그 검사를 건너뛰면 규칙이 두 갈래가 된다.
     *
     * 그래서 저장소가 밀려난 이름을 stale 칸에 적어 두고(V5.21), 정리 워커가 같은 검사를
     * 지나 지운다. 단어 수도 저장소가 옮긴다 — 제출 시점에 적어 둔 값이 유일한 출처다.
     */
    private boolean promote(VocabularyView vocabulary, String pendingName) {
        if (!meetingVocabularyRepository.promoteToReady(vocabulary.id(), pendingName)) {
            // 그 사이 재생성이 새 빌드를 접수했다. 이 결과는 옛 것이므로 물러난다.
            log.info("어휘 승격 물러남 — 그 사이 새 빌드가 접수됐다. meetingId={} resource={}",
                    vocabulary.meetingId(), pendingName);
            return false;
        }
        log.info("커스텀 어휘 준비 완료 — meetingId={} resource={}", vocabulary.meetingId(), pendingName);
        return true;
    }

    /*
     * 끝난 회의의 어휘 리소스를 지운다.
     *
     * @return 이번에 지운 건수
     */
    public int cleanupOnce() {
        List<VocabularyView> targets = meetingVocabularyRepository.findCleanupTargets(BATCH_LIMIT);
        int cleaned = 0;
        for (VocabularyView vocabulary : targets) {
            try {
                if (cleanup(vocabulary)) {
                    cleaned++;
                }
            } catch (RuntimeException e) {
                log.error("어휘 정리 실패 — meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.providerVocabularyName(), e);
            }
        }
        return cleaned;
    }

    private boolean cleanup(VocabularyView vocabulary) {
        List<SttBlockRepository.SttBlockView> blocks =
                sttBlockRepository.findByMeeting(vocabulary.meetingId());
        if (blocks.isEmpty()) {
            // 아직 녹음하지 않은 회의다. 어휘는 그 회의를 위한 것이라 지우면 정작 필요할 때 없다.
            return false;
        }
        if (sttBlockRepository.countUnfinished(vocabulary.meetingId()) > 0) {
            /*
             * 제출된 잡이 이 어휘 이름을 참조하고 있다. 지금 지우면 그 잡이 어휘 없이 돌거나
             * 실패한다 — 회의 하나 분량의 인식률이 조용히 떨어진다.
             */
            return false;
        }

        customVocabularyPort.delete(vocabulary.providerVocabularyName());
        /*
         * 삭제 성공 여부를 어댑터가 알려주지 않는다(없는 이름을 지우는 것도 성공으로 본다).
         * 그래서 여기서 정리했다고 표시한다 — 실패한 경우 어댑터가 크게 로깅하고, 그 로그가
         * 쌓이면 사람이 상한 문제를 알게 된다. 표시를 안 하면 매 주기 같은 대상을 다시 집어
         * 제공자 호출만 늘어난다.
         */
        meetingVocabularyRepository.markCleaned(vocabulary.id());
        return true;
    }

    /*
     * 승격으로 밀려난 리소스를 지운다.
     *
     * <h2>활성 정리와 나눈 이유</h2>
     * 밀려난 것은 **회의가 끝나기 전에도 나온다**(재생성). 그래서 대상이 되는 시점이 다르고,
     * 한 조회로 합치면 행마다 "무엇을 지워야 하는가"가 달라진다.
     *
     * <h2>같은 검사를 지난다</h2>
     * 받아쓰기가 도는 중이면 미룬다 — 제출된 잡이 **밀려난 이름을 참조할 수 있다.** 재생성
     * 전에 제출된 잡이 정확히 그것이다. 승격 자리에서 곧바로 지우면 이 검사를 건너뛰게 되고,
     * 그게 이 메서드가 존재하는 이유다.
     *
     * @return 이번에 지운 건수
     */
    public int cleanupStaleOnce() {
        List<VocabularyView> targets = meetingVocabularyRepository.findStaleTargets(BATCH_LIMIT);
        int cleaned = 0;
        for (VocabularyView vocabulary : targets) {
            try {
                if (sttBlockRepository.countUnfinished(vocabulary.meetingId()) > 0) {
                    // 재생성 전에 제출된 잡이 아직 이 이름을 참조할 수 있다.
                    continue;
                }
                customVocabularyPort.delete(vocabulary.staleVocabularyName());
                /*
                 * 이름 칸을 비운다. 활성 정리와 달리 deleted_at 을 쓰지 않는 이유 — 그 값은
                 * "이 회의의 활성 리소스를 지웠는가"이고, 밀려난 것은 별개다. 이름을 비우지
                 * 않으면 매 주기 같은 대상을 다시 집어 제공자 호출만 늘어난다.
                 */
                meetingVocabularyRepository.clearStaleName(vocabulary.id());
                log.info("밀려난 어휘 정리 — meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.staleVocabularyName());
                cleaned++;
            } catch (RuntimeException e) {
                log.error("밀려난 어휘 정리 실패 — meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.staleVocabularyName(), e);
            }
        }
        return cleaned;
    }
}
