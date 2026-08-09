package com.module06.backend.capture.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.module06.backend.capture.domain.model.TranscriptCursor;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * 정본 조회 커서를 문자열로 싣고 내린다(ANLZ-05).
 *
 * <h2>왜 불투명한 문자열인가</h2>
 * 커서는 클라이언트가 **해석하지 않고 그대로 되돌려주는** 값이다. 숫자 두 개를 그대로
 * 노출하면 화면이 그것을 계산해 넣기 시작하고 — 예를 들어 seq 에 +1 을 해서 보낸다 —
 * 그 순간 페이지 경계 규칙이 서버가 아니라 클라이언트에 생긴다. 나중에 정렬을 바꾸면
 * 서버만 고쳐서는 안 되는 상태가 된다.
 *
 * base64 는 암호가 아니다. 감추려는 것이 아니라 **손대지 말라는 표시**다.
 *
 * <h2>ObjectMapper 를 자체 보유한다</h2>
 * 커서 포맷은 앱의 web 직렬화 설정과 무관하게 고정이어야 한다. 공용 매퍼의 설정이 바뀌면
 * 이전에 발행한 커서를 못 읽게 되는데, 그건 페이지를 넘기던 사용자에게만 나타나 재현이 어렵다.
 */
@Component
public class TranscriptCursorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /* URL 쿼리로 오가므로 URL 안전 알파벳을 쓴다. 패딩(=)은 빼서 인코딩 사고를 줄인다. */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode(TranscriptCursor cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(new CursorPayload(cursor.offsetMs(), cursor.seq()));
            return ENCODER.encodeToString(json);
        } catch (JsonProcessingException e) {
            // 우리가 만든 두 필드를 우리 매퍼로 쓰다 실패한 것이라 입력 문제가 아니다.
            throw new IllegalStateException("정본 조회 커서를 만들 수 없습니다.", e);
        }
    }

    /*
     * 없으면 null(첫 페이지), 깨졌으면 400 이다.
     *
     * **조용히 첫 페이지로 되돌리지 않는다.** 그러면 페이지를 넘기던 화면이 맨 앞으로 돌아가
     * 같은 발화를 다시 그리는데, 사용자에게는 목록이 무한히 반복되는 것으로만 보인다.
     * 커서가 깨졌다는 사실이 응답에 남아야 원인을 찾을 수 있다.
     */
    public TranscriptCursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            byte[] json = DECODER.decode(raw);
            CursorPayload payload = OBJECT_MAPPER.readValue(new String(json, StandardCharsets.UTF_8),
                    CursorPayload.class);
            if (payload.seq() == null) {
                throw new BusinessException(CaptureErrorCode.TRANSCRIPT_CURSOR_INVALID);
            }
            return new TranscriptCursor(payload.offsetMs(), payload.seq());
        } catch (IllegalArgumentException | JsonProcessingException e) {
            // base64 가 아니거나 JSON 이 아니다 — 어느 쪽이든 우리가 발행한 커서가 아니다.
            throw new BusinessException(CaptureErrorCode.TRANSCRIPT_CURSOR_INVALID);
        }
    }

    /* offsetMs 는 null 일 수 있다 — 오프셋 없는 꼬리 구간을 가리키는 커서다. */
    private record CursorPayload(Integer offsetMs, Integer seq) {
    }
}
