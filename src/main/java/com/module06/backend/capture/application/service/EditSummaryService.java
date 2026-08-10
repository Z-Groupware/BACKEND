package com.module06.backend.capture.application.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.ItemEdit;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.ItemView;
import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.application.port.out.ReviewLogRepository.ReviewLogEntry;
import com.module06.backend.capture.application.usecase.EditSummaryUseCase;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.domain.model.ReviewTargetType;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * ANLZ-04 · 요약 수정.
 *
 * <h2>문장을 고치는 것이 아니라 라벨을 만드는 API 다</h2>
 * 화면에서는 "요약 고치고 저장"이지만, 이 저장소가 이 순간에 얻는 것은 <b>{AI 가 낸 문장 →
 * 사람이 인정한 문장} 한 쌍</b>이다. 명세가 그걸 한 줄로 못 박아 두었다 — <b>"액션만 라벨이
 * 아니라 요약도 라벨이다."</b> L3 를 고칠 재료가 이것뿐이고, 지나간 회의는 다시 만들 수 없다.
 *
 * <h2>RVW-02 와 사유 규칙이 다르다</h2>
 * 액션 수정·반려는 사유 코드를 422 로 강제하지만 요약 수정은 요구하지 않는다. V5.9 의
 * {@code CK_REVIEW_LOG_REASON} 이 SUMMARY_ITEM 만 예외로 빼 두었고 이유도 주석에 있다 —
 * 요약은 문구만 다듬는 수정이 있어 대응하는 사유 코드가 없고, <b>강제하면 수정 기록이 아예
 * 남지 못한다.</b> 같은 규칙을 복사해 오면 안 되는 자리다.
 *
 * <h2>한 트랜잭션이다</h2>
 * 항목·편집표시·라벨이 함께 커밋된다. 나누면 문장은 바뀌었는데 라벨이 없는 상태가 남고,
 * 그건 이 API 를 만든 이유가 사라진 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EditSummaryService implements EditSummaryUseCase {

    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final MeetingAccessGuard meetingAccessGuard;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SummaryEdited edit(EditSummaryCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());

        if (command.items() == null || command.items().isEmpty()) {
            // 빈 요청을 200 으로 돌려주면 "저장했다"는 응답만 받고 아무것도 안 바뀐다.
            throw new BusinessException(CaptureErrorCode.SUMMARY_EDIT_EMPTY);
        }

        List<Long> ids = command.items().stream().map(ItemEditCommand::itemId).toList();

        /*
         * 고치기 **전** 값을 먼저 읽는다. 라벨의 llm_output 이 이 값이다 — 고친 뒤에 읽으면
         * 사람이 쓴 문장이 "AI 가 낸 값"으로 기록되고, 그 행은 나중에 "AI 가 맞혔다"로 읽힌다.
         * RVW-02 가 CONFIRM 에 값을 못 싣게 막는 것과 같은 판단이다.
         *
         * 이 조회가 회사 스코프이기도 하다. itemId 는 meeting_decision.id 라 회의를 함께 걸지
         * 않으면 다른 회사의 요약 문장을 고칠 수 있다(#100 과 같은 성질, 이쪽은 쓰기 경로다).
         */
        Map<Long, ItemView> before = new LinkedHashMap<>();
        meetingSummaryRepository.findItemsInMeeting(command.meetingId(), ids)
                .forEach(item -> before.put(item.id(), item));

        if (before.size() != ids.size()) {
            /*
             * 이 회의에 없는 항목이 섞였다. 일부만 고치고 넘어가지 않는다 — 사람은 보낸 것이
             * 다 반영됐다고 믿는데 일부는 사라지고, 어느 것이 빠졌는지 응답에 없다.
             *
             * 404 다. 다른 회의 항목이라는 사실 자체를 알려주지 않는다(REVIEW_ACTION_NOT_FOUND 와 같은 이유).
             */
            throw new BusinessException(CaptureErrorCode.SUMMARY_ITEM_NOT_FOUND);
        }

        List<ItemEdit> edits = command.items().stream()
                .map(item -> new ItemEdit(item.itemId(), item.content(), item.reason()))
                .toList();
        LocalDateTime editedAt = meetingSummaryRepository.applyItemEdits(
                command.meetingId(), edits, command.editorMemberId());

        boolean labelLogged = appendLabels(command, before);

        log.info("요약 수정 — meetingId={} 항목 {}건 편집자={} 라벨={}",
                command.meetingId(), edits.size(), command.editorMemberId(), labelLogged);

        return new SummaryEdited(editedAt, labelLogged, edits.size());
    }

    /*
     * 항목마다 라벨을 하나씩 남긴다.
     *
     * <b>MODIFY 로 남긴다.</b> 사람이 문장을 고쳤다는 뜻이고, 사유 코드는 붙이지 않는다
     * (V5.9 CHECK 가 SUMMARY_ITEM 에 대해 그것을 허용한다).
     *
     * <b>layer 는 L3 다.</b> 요약 문장을 만든 계층이 L3 이고, few-shot 조회가 layer 를 필수
     * 필터로 쓴다 — 다른 값을 적으면 이 라벨을 아무도 못 찾는다(명세 처리 정책도 L3 다).
     */
    private boolean appendLabels(EditSummaryCommand command, Map<Long, ItemView> before) {
        boolean logged = true;
        for (ItemEditCommand item : command.items()) {
            ItemView original = before.get(item.itemId());
            reviewLogRepository.append(new ReviewLogEntry(
                    command.companyId(),
                    command.meetingId(),
                    ReviewTargetType.SUMMARY_ITEM,
                    item.itemId(),
                    LayerName.L3,
                    ReviewDecision.MODIFY,
                    // 요약 수정에는 사유 코드가 없다 — 문구만 다듬는 수정에 대응하는 코드가 없고,
                    // 강제하면 이 기록이 아예 남지 못한다(V5.9 CK_REVIEW_LOG_REASON 주석).
                    null,
                    inputContextJson(original),
                    llmOutputJson(original),
                    humanValueJson(item, original),
                    command.editorMemberId(),
                    // 요약 문장을 만든 모델·프롬프트는 meeting_summary 에 회의 단위로만 남는다.
                    // 항목 단위로 되짚을 값이 없어 비운다 — 지어내면 그게 근거로 읽힌다.
                    null,
                    null,
                    // AI 가 만든 항목을 고친 것이다. 사람이 처음부터 쓴 항목(RVW-03)이 아니다.
                    false));
        }
        return logged;
    }

    /*
     * 재현 불가한 값을 담는다(V5.9 주석). 주제·항목 유형·근거 발화·게이트 판정이다.
     *
     * 게이트 판정을 담는 이유 — 그 문장이 확정으로 분류된 상태에서 고쳐진 것인지, 논의로 남은
     * 상태에서 고쳐진 것인지가 라벨의 전제다. 나중에 재분석이 돌면 그 값은 바뀐다.
     */
    private String inputContextJson(ItemView item) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("itemType", item.itemType() == null ? null : item.itemType().name());
        context.put("evidenceTranscriptId", item.evidenceUtteranceId());
        context.put("gateStatus", item.gateStatus());
        return toJson(context);
    }

    /* AI 가 낸 원본 문장. 고치기 전 값이다. */
    private String llmOutputJson(ItemView item) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", item.content());
        output.put("reason", item.reason());
        return toJson(output);
    }

    /*
     * 사람이 고친 값.
     *
     * reason 을 안 보냈으면 <b>원본 reason 을 그대로 담는다.</b> 고치지 않았다는 뜻이지
     * 비웠다는 뜻이 아니고, null 로 담으면 "사람이 근거를 지우는 것이 정답"이라고 가르치게 된다.
     */
    private String humanValueJson(ItemEditCommand item, ItemView original) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", item.content() == null || item.content().isBlank()
                ? original.content()
                : item.content());
        value.put("reason", item.reason() == null ? original.reason() : item.reason());
        return toJson(value);
    }

    /*
     * 직렬화 실패를 삼키지 않는다.
     *
     * 빈 라벨을 남기면 정답 쌍이 아닌 행이 라벨셋에 섞이고, 나중에 그걸로 잰 수치가 조용히
     * 틀어진다. QLTY-01 이 gold set 직렬화 실패에서 내린 것과 같은 판단이다 — 사람 판정은
     * 다시 만들 수 없으므로 차라리 실패한다.
     */
    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(CaptureErrorCode.SUMMARY_LABEL_SERIALIZATION_FAILED);
        }
    }
}
