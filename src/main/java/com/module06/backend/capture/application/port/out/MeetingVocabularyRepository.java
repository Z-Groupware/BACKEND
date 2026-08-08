package com.module06.backend.capture.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

import com.module06.backend.capture.domain.model.VocabularyStatus;

/* meeting_vocabulary(V5.19) 접근 포트다. STT-01(조회) · STT-02(재생성)가 쓴다. */
public interface MeetingVocabularyRepository {

    /* 회의당 하나다(UNIQUE). 아직 만든 적이 없으면 비어 있다. */
    Optional<VocabularyView> findByMeeting(long meetingId);

    /*
     * 재생성을 접수한다 — 없으면 만들고 있으면 PENDING 으로 되돌린다.
     *
     * <h2>phraseCount·builtAt 을 지우지 않는다</h2>
     * 재생성이 도는 동안에도 **제공자에는 이전 어휘가 그대로 살아 있다.** 여기서 0 으로
     * 비우면 화면이 "어휘 없음"으로 보여주는데 실제로는 지난 어휘가 쓰이고 있다 — 사람이
     * 인식률 문제를 어휘 탓으로 잘못 짚게 된다. 마지막으로 성공한 생성이 언제 몇 개였는지는
     * 그대로 두고 status 만 PENDING 으로 바꾼다.
     *
     * @return 접수 뒤의 상태
     */
    VocabularyView markRebuilding(long meetingId);

    /*
     * 제공자에 제출한 리소스 이름을 적어 둔다.
     *
     * **제출 뒤에 따로 적는다.** 이름은 제공자가 정하거나 제출이 성공해야 확정되는 값이라,
     * 제출 전에 미리 적으면 만들어지지도 않은 리소스 이름이 남는다 — 나중에 정리 작업이 그
     * 이름을 지우려다 없는 리소스를 찾게 되고, 정작 계정 상한을 쓰고 있는 진짜 리소스는
     * 아무도 모른다.
     */
    void assignProviderName(long vocabularyId, String providerVocabularyName);

    /*
     * @param providerVocabularyName 제공자 리소스 이름. **삭제에 필요하다** — 계정당 어휘
     *                               개수 상한이 있어 정리하지 않으면 신규 회의가 어휘 없이
     *                               돌게 되는데, 지우려면 이름을 알아야 한다
     * @param builtAt                마지막으로 성공한 생성 시각. 재생성 중에도 남는다
     */
    record VocabularyView(
            long id,
            long meetingId,
            VocabularyStatus status,
            int phraseCount,
            String providerVocabularyName,
            LocalDateTime builtAt
    ) {
    }
}
