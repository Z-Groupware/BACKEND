package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicSegment;

/*
 * 계층 재개(ANLZ-02)가 되살려야 하는 중간 산출물의 보관 포트다.
 *
 * <h2>왜 둘만 있나</h2>
 * 나머지 계층의 산출물은 이미 자기 테이블에 남는다 — 요약은 meeting_summary,
 * 배정은 meeting_assignment_tuple, 화자는 transcript_chunk. 남지 않는 것은 둘뿐이다.
 *
 * <ul>
 *   <li><b>L1.5 지시어 해소</b> — 결과를 발화 <b>사본</b>에 주석으로 붙이고 DB 의 원문은
 *       고치지 않는다. 원문을 치환하면 "정말 그렇게 말했나"의 근거가 사라진다.</li>
 *   <li><b>L2 주제 분할</b> — meeting_decision 에 주제 이름과 순번은 남지만 <b>그 주제가
 *       어느 발화를 묶은 것인지</b>는 남지 않는다. 그 목록이 L3~L5 가 모델에 넘기는 문맥이다.</li>
 * </ul>
 *
 * 이 둘이 없으면 L4 부터 재개할 때 문맥 없이 모델을 부르게 되고, 빈 결과가 DONE 으로 기록된다.
 *
 * <h2>없으면 빈 목록이다 — 예외가 아니다</h2>
 * 이 마이그레이션 이전에 분석된 회의에는 산출물이 없다. 그걸 예외로 만들면 예전 회의는
 * 재개 자체가 불가능해지는데, 재개가 가장 필요한 것이 그 회의들이다. 비어 있음을 호출자가
 * 보고 "되살릴 문맥이 없다"고 판정한다.
 */
public interface AnalysisArtifactRepository {

    /* 회의당 계층당 1건. 재실행하면 갱신한다 — 지난 실행의 문맥은 이번 결과와 짝이 맞지 않는다. */
    void saveReferences(long meetingId, List<ResolvedReference> references);

    List<ResolvedReference> findReferences(long meetingId);

    void saveTopics(long meetingId, List<TopicSegment> topics);

    List<TopicSegment> findTopics(long meetingId);
}
