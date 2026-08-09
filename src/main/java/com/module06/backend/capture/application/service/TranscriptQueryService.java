package com.module06.backend.capture.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.UtteranceView;
import com.module06.backend.capture.application.usecase.GetTranscriptsUseCase;
import com.module06.backend.capture.domain.model.TranscriptCursor;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * ANLZ-05 · 정본 스크립트 조회.
 *
 * <h2>회사 스코프를 먼저 지난다</h2>
 * 이 API 는 **회의에서 오간 말 전문**을 그대로 내보낸다. 캡처 파이프라인에서 유출 시 피해가
 * 가장 큰 자리다 — 요약은 추려진 것이지만 정본은 원문이다. {@link MeetingAccessGuard} 를
 * 먼저 지나지 않으면 로그인한 사원이 남의 회사 회의 id 를 넣어 회의록을 통째로 가져간다
 * (CAP-06 이 실제로 그렇게 뚫려 있었다 · 이슈 #100).
 */
@Service
@RequiredArgsConstructor
public class TranscriptQueryService implements GetTranscriptsUseCase {

    /*
     * 한 페이지 건수다. 명세에 페이지 크기 파라미터가 없어 서버가 정한다.
     *
     * 클라이언트가 정하게 열어두지 않는 이유 — limit=100000 을 넣으면 페이징이 무의미해진다.
     * 200 은 2시간 회의(수천 건)를 열 번 남짓에 훑을 수 있으면서, 한 응답이 과하게 커지지 않는
     * 선이다. 필요해지면 상한을 둔 파라미터로 여는 것이 다음 단계다.
     */
    private static final int PAGE_SIZE = 200;

    /*
     * ids 로 한 번에 받을 수 있는 최대 건수.
     *
     * 상한이 없으면 ids 가 **페이징을 우회하는 경로**가 된다 — 회의 전체 id 를 넣으면 수천 건이
     * 한 응답에 실린다. 근거 발화를 보는 용도라 실제로는 한 자리 수를 넘지 않는다.
     */
    private static final int MAX_IDS = 100;

    private final TranscriptRepository transcriptRepository;
    private final TranscriptCursorCodec cursorCodec;
    private final MeetingAccessGuard meetingAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public TranscriptPage getTranscripts(long companyId, long meetingId, String cursor, List<Long> ids) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        if (ids != null && !ids.isEmpty()) {
            return selected(meetingId, ids);
        }
        return paged(meetingId, cursor);
    }

    /*
     * 근거 발화 선택 조회. **커서를 함께 쓰지 않는다.**
     *
     * 둘을 섞으면 "지정한 id 중 커서 이후의 것"이라는 뜻이 되는데, ids 는 이미 볼 것을 다 나열한
     * 요청이라 그 위에 페이지를 또 나눌 이유가 없다. 대신 개수에 상한을 둔다.
     */
    private TranscriptPage selected(long meetingId, List<Long> ids) {
        if (ids.size() > MAX_IDS) {
            throw new BusinessException(CaptureErrorCode.TRANSCRIPT_IDS_TOO_MANY);
        }
        // nextCursor 는 null 이다 — 요청한 것이 전부이므로 이어질 페이지가 없다.
        return new TranscriptPage(transcriptRepository.findByMeetingAndIds(meetingId, ids), null);
    }

    /*
     * 한 페이지를 뜨고 다음 커서를 만든다.
     *
     * <h2>가득 찼으면 다음 커서를 준다</h2>
     * PAGE_SIZE 만큼 왔다는 것은 더 있을 수 있다는 뜻이다. 정확히 마지막 페이지가 가득 찬
     * 경우에는 빈 다음 페이지를 한 번 더 부르게 되는데, 그 낭비를 없애려면 한 건을 더 떠서
     * 확인해야 한다. 매 페이지에 조회를 하나 더 붙이는 대신 마지막에 한 번 헛도는 쪽을 택했다.
     */
    private TranscriptPage paged(long meetingId, String rawCursor) {
        TranscriptCursor cursor = cursorCodec.decode(rawCursor);
        List<UtteranceView> page = transcriptRepository.findPage(meetingId, cursor, PAGE_SIZE);

        if (page.size() < PAGE_SIZE) {
            return new TranscriptPage(page, null);
        }
        UtteranceView last = page.get(page.size() - 1);
        return new TranscriptPage(page, cursorCodec.encode(new TranscriptCursor(last.startOffsetMs(), last.seq())));
    }
}
