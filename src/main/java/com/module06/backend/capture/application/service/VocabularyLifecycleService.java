package com.module06.backend.capture.application.service;

import java.util.List;
import java.util.Optional;

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

    private final MeetingVocabularyRepository meetingVocabularyRepository;
    private final CustomVocabularyPort customVocabularyPort;

    /* 정리 시점을 가르는 값 — 받아쓰기가 아직 도는 중이면 어휘를 지우면 안 된다. */
    private final SttBlockRepository sttBlockRepository;

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
        VocabularyState state = customVocabularyPort.stateOf(vocabulary.pendingVocabularyName());
        return switch (state) {
            case READY -> {
                promote(vocabulary);
                yield true;
            }
            case FAILED -> {
                log.warn("어휘 생성 실패 — meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.pendingVocabularyName());
                meetingVocabularyRepository.markBuildFailed(vocabulary.id(), ERROR_PROVIDER_FAILED);
                yield true;
            }
            /*
             * 제공자가 그 이름을 모른다. **실패로 닫는다** — 그대로 두면 영원히 PENDING 이고,
             * 선점이 PENDING 을 막으므로 사람이 다시 누를 수도 없다(markBuildFailed 주석).
             */
            case UNKNOWN -> {
                log.warn("어휘를 제공자가 모른다 — 우리 상태와 어긋났다. meetingId={} resource={}",
                        vocabulary.meetingId(), vocabulary.pendingVocabularyName());
                meetingVocabularyRepository.markBuildFailed(vocabulary.id(), ERROR_NOT_FOUND);
                yield true;
            }
            // 아직 만드는 중이거나 못 읽었다. 상태를 바꾸지 않고 다음 주기에 다시 본다.
            case PENDING, UNAVAILABLE -> false;
        };
    }

    /*
     * 승격하고 **이전 활성 리소스를 지운다.**
     *
     * 순서가 이렇다 — 먼저 승격해 새 이름이 활성이 되고, 그 뒤에 이전 것을 지운다. 반대로 하면
     * 지우는 데 성공하고 승격이 실패했을 때 **활성 어휘가 없는 채로 이름만 남는다**(STT 제출이
     * 없는 어휘를 참조한다).
     *
     * 삭제가 실패해도 승격은 유지한다. 어댑터가 삼키고 크게 로깅하므로(계정 상한을 계속 쓴다)
     * 여기서 되돌릴 이유가 없다 — 되돌리면 방금 만든 어휘를 못 쓰게 된다.
     *
     * phraseCount 는 **제출 시점에 알던 값**이다. 제공자가 개수를 돌려주지 않아 그것 말고 쓸
     * 값이 없다. 지금은 승격 시점에 그 값을 다시 알 방법이 없어 기존 값을 유지한다 —
     * 재생성으로 참석자가 바뀌면 이 숫자가 한 주기 늦게 맞는다.
     */
    private void promote(VocabularyView vocabulary) {
        Optional<String> previousActive =
                meetingVocabularyRepository.promoteToReady(vocabulary.id(), vocabulary.phraseCount());

        log.info("커스텀 어휘 준비 완료 — meetingId={} resource={}",
                vocabulary.meetingId(), vocabulary.pendingVocabularyName());

        previousActive.ifPresent(name -> {
            log.info("이전 어휘 정리 — meetingId={} resource={}", vocabulary.meetingId(), name);
            customVocabularyPort.delete(name);
        });
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
}
