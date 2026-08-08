package com.module06.backend.capture.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.CustomVocabularyPort;
import com.module06.backend.capture.application.port.out.CustomVocabularyPort.BuildRequest;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.usecase.GetMeetingVocabularyUseCase;
import com.module06.backend.capture.application.usecase.RebuildMeetingVocabularyUseCase;
import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * STT-01(어휘 상태 조회) · STT-02(어휘 재생성).
 *
 * <h2>이 값은 녹음을 막지 않는다</h2>
 * READY 가 아니어도 회의는 시작할 수 있다. 고유명사 인식률만 낮아진다 — "모모시티",
 * "인수인계서" 같은 단어가 틀리게 받아쓰인다. 그래서 화면에도 경고이지 차단이 아니고, 여기서도
 * 상태를 이유로 무엇을 막지 않는다.
 *
 * <h2>재생성은 회의 담당자만이다</h2>
 * 어휘 생성은 **제공자 계정의 한정된 자원**을 쓴다. 계정당 개수 상한이 있어서, 참석자 아무나
 * 반복해 누르면 그 상한을 갉아먹고 **다른 회의가 어휘 없이 돌게 된다.** RVW-05 가 분배 확정을
 * 담당자로 좁히는 것과 같은 종류의 판단이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingVocabularyService implements GetMeetingVocabularyUseCase, RebuildMeetingVocabularyUseCase {

    private final MeetingVocabularyRepository meetingVocabularyRepository;
    private final CustomVocabularyPort customVocabularyPort;
    private final MeetingParticipantProvider meetingParticipantProvider;
    private final MeetingAccessGuard meetingAccessGuard;
    private final MeetingHostProvider meetingHostProvider;

    /*
     * 아직 만든 적이 없는 회의는 **PENDING 으로 답한다.**
     *
     * 404 로 두면 어휘를 만든 적 없는 대부분의 회의에서 화면이 오류를 받는다 — 그건 사실이
     * 아니다. 정보가 없다는 뜻이지 잘못된 요청이 아니기 때문이다.
     *
     * 그렇다고 "아직 시작 안 함"이라는 새 상태를 만들지도 않는다. 명세의 상태값이 셋뿐이고,
     * **사람이 할 일이 PENDING 과 같다** — 기다리거나 재생성을 누른다. 구분이 행동을 바꾸지
     * 않는 자리에서 값을 늘리면 화면이 그 값을 모르고 아무것도 못 그린다.
     */
    @Override
    @Transactional(readOnly = true)
    public VocabularyView getVocabulary(long companyId, long meetingId) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        return meetingVocabularyRepository.findByMeeting(meetingId)
                .orElseGet(() -> new VocabularyView(
                        0L, meetingId, VocabularyStatus.PENDING, 0, null, null));
    }

    @Override
    @Transactional
    public VocabularyView rebuild(RebuildVocabularyCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());
        requireHost(command);

        List<String> phrases = phrasesOf(command.meetingId());
        if (phrases.isEmpty()) {
            /*
             * 넣을 단어가 없다. 그대로 만들면 **빈 어휘 리소스가 계정 상한을 하나 차지한다** —
             * 인식률은 그대로인데 다른 회의가 쓸 자리만 줄어든다.
             */
            throw new BusinessException(CaptureErrorCode.VOCABULARY_NO_PHRASES);
        }

        /*
         * 상태를 먼저 PENDING 으로 올린다. 제출이 몇 분 걸리는 작업이라, 그 사이 화면이 이전
         * 상태(READY·FAILED)를 그대로 보여주면 사람이 버튼을 다시 누른다 — 그 반복이 그대로
         * 계정 상한을 갉아먹는다.
         */
        VocabularyView view = meetingVocabularyRepository.markRebuilding(command.meetingId());

        String providerName = customVocabularyPort.requestBuild(
                new BuildRequest(command.meetingId(), phrases));
        // 제출이 성공한 뒤에 적는다 — 미리 적으면 만들어지지도 않은 이름이 남고, 정리 작업이
        // 그 이름을 지우려다 정작 상한을 쓰는 진짜 리소스를 놓친다.
        meetingVocabularyRepository.assignProviderName(view.id(), providerName);

        log.info("커스텀 어휘 재생성 접수 — meetingId={} 단어={}개 resource={}",
                command.meetingId(), phrases.size(), providerName);

        return view;
    }

    /*
     * 어휘에 넣을 단어를 모은다.
     *
     * 지금은 **참석자 이름뿐이다.** 명세는 "참석자 이름 + 프로젝트 태그"인데 프로젝트 **이름**을
     * 읽는 포트가 없다(MeetingProjectProvider 는 id 만 준다). 없는 값을 지어내지 않고, 태그는
     * 그 포트가 생길 때 붙인다 — 이름 없이 id 를 넣으면 "500" 같은 숫자가 어휘로 들어간다.
     *
     * 명단 밖 탈출구(personId=null)의 이름도 넣는다. 그 자리도 실제로 회의에서 불리는 이름이고,
     * 어휘의 목적은 **받아쓰기 정확도**이지 명단 판정이 아니다 — L1·L4 가 personId 로 거르는
     * 것과 목적이 다르다.
     */
    private List<String> phrasesOf(long meetingId) {
        Set<String> phrases = new LinkedHashSet<>();
        meetingParticipantProvider.participantsOf(meetingId).stream()
                .map(AiLayerPort.Participant::name)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .forEach(phrases::add);
        return List.copyOf(phrases);
    }

    /*
     * 회의 담당자만 재생성할 수 있다(명세 403).
     *
     * 담당자를 모르면 통과시키지 않는다 — 모르는 채로 지나가면 이 검사는 없는 것과 같다
     * (RVW-05 의 requireHost 와 같은 판단이다).
     */
    private void requireHost(RebuildVocabularyCommand command) {
        long host = meetingHostProvider.hostMemberIdOf(command.meetingId())
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY));

        if (host != command.requestedBy()) {
            throw new BusinessException(CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY);
        }
    }
}
