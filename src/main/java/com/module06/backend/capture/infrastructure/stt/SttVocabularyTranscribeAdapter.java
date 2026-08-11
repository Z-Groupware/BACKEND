package com.module06.backend.capture.infrastructure.stt;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.BadRequestException;
import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.CreateVocabularyRequest;
import software.amazon.awssdk.services.transcribe.model.DeleteVocabularyRequest;
import software.amazon.awssdk.services.transcribe.model.GetVocabularyRequest;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.NotFoundException;
import software.amazon.awssdk.services.transcribe.model.TranscribeException;

import com.module06.backend.capture.application.port.out.CustomVocabularyPort;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * CustomVocabularyPort 의 AWS Transcribe 구현.
 *
 * <h2>이름 규칙을 여기서 정한다</h2>
 * 제공자마다 허용 문자와 길이가 다르다 — Transcribe 는 `[0-9a-zA-Z._-]` 200자까지다. 규칙이
 * 서비스로 새면 제공자를 바꿀 때 도메인 코드를 고쳐야 하므로 어댑터가 정한다(스텁이 예고한 자리).
 *
 * <h2>이름에 시각을 넣는다 — 재생성이 있다</h2>
 * 스텁은 `meeting-{id}-vocab` 하나만 만들었다. 그런데 재생성(STT-02)은 **이전 어휘가 살아 있는
 * 동안 새 어휘를 만든다**(V5.19 의 활성/대기 분리가 그 전제다). 같은 이름을 쓰면 제공자가
 * ConflictException 으로 거절하거나 이전 것을 덮어 **재생성 중에 쓰이던 어휘가 사라진다.**
 * 그래서 이름에 접수 시각을 붙여 매번 다른 리소스를 만든다.
 *
 * <h2>한국어는 Phrases 로 보낸다</h2>
 * 어휘 파일(S3)로도 만들 수 있지만 그건 발음 표기·표시형까지 줄 때 필요한 방식이다. 지금 넣는
 * 것은 참석자 이름뿐이라 목록으로 충분하고, S3 파일을 쓰면 그 파일의 수명 관리가 하나 더 생긴다.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SttVocabularyTranscribeAdapter implements CustomVocabularyPort {

    private static final LanguageCode LANGUAGE = LanguageCode.KO_KR;

    /* Transcribe 이름 상한은 200자다. 접미사(시각)를 붙일 자리를 남겨 둔다. */
    private static final int MAX_NAME_LENGTH = 200;

    private final TranscribeClient transcribeClient;

    @Override
    public String requestBuild(BuildRequest request) {
        String name = nameFor(request.meetingId());
        try {
            transcribeClient.createVocabulary(CreateVocabularyRequest.builder()
                    .vocabularyName(name)
                    .languageCode(LANGUAGE)
                    .phrases(sanitize(request.phrases()))
                    .build());
        } catch (ConflictException e) {
            /*
             * 같은 이름이 이미 있다. 이름에 시각이 들어가므로 같은 초에 두 번 접수된 경우다 —
             * 선점(claimRebuild)이 막는 자리이지만 막지 못했다면 우리 상태와 제공자 상태가
             * 어긋난 것이다. 성공으로 삼키지 않는다: 삼키면 남이 만든 리소스를 우리 것으로
             * 참조하게 된다.
             */
            log.error("어휘 이름이 이미 있다 — meetingId={} resource={}", request.meetingId(), name, e);
            throw new BusinessException(CaptureErrorCode.VOCABULARY_BUILD_FAILED);
        } catch (TranscribeException e) {
            log.error("어휘 생성 요청 실패 — meetingId={} resource={} 단어={}개",
                    request.meetingId(), name, request.phrases().size(), e);
            throw new BusinessException(CaptureErrorCode.VOCABULARY_BUILD_FAILED);
        }

        log.info("커스텀 어휘 생성 요청 — meetingId={} resource={} 단어={}개",
                request.meetingId(), name, request.phrases().size());
        return name;
    }

    @Override
    public VocabularyState stateOf(String providerVocabularyName) {
        try {
            String state = transcribeClient.getVocabulary(GetVocabularyRequest.builder()
                            .vocabularyName(providerVocabularyName)
                            .build())
                    .vocabularyStateAsString();

            return switch (state == null ? "" : state) {
                case "READY" -> VocabularyState.READY;
                case "PENDING" -> VocabularyState.PENDING;
                case "FAILED" -> VocabularyState.FAILED;
                default -> {
                    /*
                     * 제공자가 새 상태를 낸 것이다. 실패로 접지 않는다 — 아직 만들어지는 중일
                     * 수 있고, 실패로 닫으면 사람이 재생성을 눌러 리소스가 하나 더 만들어진다.
                     */
                    log.warn("어휘 상태를 해석할 수 없다 — resource={} state={}",
                            providerVocabularyName, state);
                    yield VocabularyState.UNAVAILABLE;
                }
            };
        } catch (NotFoundException | BadRequestException e) {
            // 그 이름의 어휘가 없다. 제출이 실제로 안 됐거나 콘솔에서 지운 것이다.
            log.warn("어휘를 찾을 수 없다 — resource={}", providerVocabularyName);
            return VocabularyState.UNKNOWN;
        } catch (RuntimeException e) {
            // 네트워크·권한·스로틀. 상태를 바꾸지 않고 다음 주기에 다시 본다.
            log.warn("어휘 상태 조회 실패 — resource={}", providerVocabularyName, e);
            return VocabularyState.UNAVAILABLE;
        }
    }

    /*
     * 없는 이름을 지우려 해도 실패로 보지 않는다(포트 주석) — 이미 지워진 것과 애초에 없던 것을
     * 구분해도 할 일이 같고, 여기서 터뜨리면 정리 작업이 그 하나 때문에 멈춘다.
     */
    @Override
    public void delete(String providerVocabularyName) {
        try {
            transcribeClient.deleteVocabulary(DeleteVocabularyRequest.builder()
                    .vocabularyName(providerVocabularyName)
                    .build());
            log.info("커스텀 어휘 삭제 — resource={}", providerVocabularyName);
        } catch (NotFoundException | BadRequestException e) {
            log.info("지울 어휘가 이미 없다 — resource={}", providerVocabularyName);
        } catch (TranscribeException e) {
            /*
             * 지우지 못했다. **던지지 않는다** — 정리는 주기 작업이고, 여기서 터뜨리면 나머지
             * 대상이 함께 밀린다. 다만 계정 상한을 계속 쓰고 있으므로 크게 로깅한다: 이 로그가
             * 쌓이면 상한에 걸리기 전에 사람이 알아야 한다.
             */
            log.error("어휘 삭제 실패 — 계정 상한을 계속 쓴다. resource={}", providerVocabularyName, e);
        }
    }

    /*
     * `meeting-{id}-vocab-{yyyyMMddHHmmss}` 형태로 만든다.
     *
     * 시각을 붙이는 이유는 클래스 주석에 적었다(재생성이 이전 어휘를 살려 둔다). 회의 id 만으로는
     * 재생성이 성립하지 않는다.
     *
     * ⚠ 상한을 넘으면 **앞을 자르지 않고 뒤를 자른다.** 앞(회의 id)이 사라지면 어느 회의의
     * 리소스인지 알 수 없어 정리에서 못 찾는다. 실제로는 회의 id 가 아무리 커도 200자에 닿지
     * 않지만, 규칙을 여기 적어 두지 않으면 나중에 접두사를 늘릴 때 앞부터 자르게 된다.
     */
    private static String nameFor(long meetingId) {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String name = "meeting-" + meetingId + "-vocab-" + stamp;
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    /*
     * 제공자가 받는 모양으로 다듬는다.
     *
     * <h2>공백이 든 구절은 하이픈으로 잇는다</h2>
     * Transcribe 의 Phrases 는 항목 하나가 한 구절이고, **여러 낱말로 된 구절은 하이픈으로
     * 잇는 것이 그쪽 규칙이다.** 공백을 그대로 보내면 항목이 쪼개져 "김 민섭"이 두 낱말로
     * 등록되고, 정작 고유명사 인식률을 올리려던 목적이 무너진다.
     *
     * 빈 값은 버린다 — 참석자 이름이 비어 있는 행이 섞이면 제공자가 요청 전체를 거절한다.
     */
    private static List<String> sanitize(List<String> phrases) {
        return phrases.stream()
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .map(phrase -> phrase.trim().replaceAll("\\s+", "-"))
                .distinct()
                .toList();
    }
}
