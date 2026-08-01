package com.module06.backend.reviewloop.judge;

import java.io.IOException;

/**
 * 사람 승인 처리(반자동) — AI 초안을 사람이 승인(원하면 노트 수정)하면 lessons.jsonl에 기록한다.
 * 이 서비스가 승인 화면(REST/CLI) 뒤에서 도는 로직. 반려하면 아무것도 저장하지 않는다(Judge 판단이 옳았음).
 */
public class LessonApprovalService {

    private final KnowledgeStore store;

    public LessonApprovalService(KnowledgeStore store) {
        this.store = store;
    }

    /**
     * 승인 — 초안을 그대로 또는 수정한 노트로 확정해 저장한다.
     * @param editedNote 사람이 노트를 고쳤으면 그 텍스트, 초안 그대로면 null
     */
    public Lesson approve(Lesson draft, String editedNote, String timestamp) throws IOException {
        String finalNote = (editedNote == null || editedNote.isBlank()) ? draft.humanNote() : editedNote;
        Lesson approved = new Lesson(timestamp, draft.ruleId(), draft.kind(), finalNote);
        store.record(approved);
        return approved;
    }

    /** 반려 — 저장하지 않는다(교훈 아님). */
    public void reject(Lesson draft) {
        // no-op: Judge 판단이 옳았으므로 교훈으로 축적하지 않는다.
    }
}
