package com.module06.backend.capture.application.service;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * STT 잡 이름을 만드는 유일한 자리.
 *
 * <h2>왜 한 곳으로 모았나</h2>
 * 규칙이 두 군데에 손으로 적혀 있었다 — 최초 제출(SttBlockCreationService)이 문자열 리터럴로
 * `-r0` 을 조립하고, 재처리(SttBlockService.jobNameOf)가 같은 형식을 다시 적었다. 한쪽 주석이
 * "SttBlockService.jobNameOf와 같은 형식"이라고 말하는 것 자체가 그 중복의 증거다. 규칙이
 * 갈리면 최초 제출과 재처리가 서로 다른 네임스페이스를 쓰게 되고, 그건 제출 시점이 아니라
 * 몇 주 뒤 폴링이 잡을 못 찾을 때 드러난다.
 *
 * <h2>네임스페이스를 앞에 붙인다 — 이름이 DB보다 오래 살기 때문</h2>
 * 이름은 meetingId(DB auto-increment)로만 만들어지는데, <b>Transcribe 의 잡 이름 네임스페이스는
 * AWS 계정+리전 단위고 DB 보다 오래 산다</b>(완료된 잡 이력이 계정에 남는다). 그래서 "이 DB 가
 * 그 계정과 1:1 이고 영원히 안 바뀐다"는 전제가 깨지는 순간 전부 충돌한다 —
 * 데모/스테이징 재시드로 meetingId=1 이 재사용되는 경우(2026-08-18 P1, job=meeting-1-block-0-r0),
 * 여러 환경이 한 계정을 같이 쓰는 경우, DB 스냅샷을 복원하는 경우가 전부 같은 뿌리다.
 * DB 의 UK_STT_BLOCK_PROVIDER_JOB_NAME(V5.4)은 테이블이 비면 아무것도 못 막는다 — 우리 쪽
 * 기억이 지워져도 AWS 쪽 기억은 남아 있는 것이 이 실패의 본질이라서다.
 *
 * 그 전제를 깨는 쪽(재시드하는 사람)이 <b>설정값 하나만 올리면</b> 네임스페이스가 갈린다.
 * AWS Transcribe 잡 삭제 권한이 필요 없어서 인프라에 매번 요청하지 않아도 된다.
 *
 * <h2>결정적인 이름을 유지한다 — 랜덤 suffix 로 가지 않는다</h2>
 * SttTranscribeJobAdapter 의 ConflictException 처리는 "같은 이름이 이미 있다"를
 * <b>우리 상태와 제공자 상태가 어긋났다는 신호</b>로 읽는다(제출은 됐는데 응답을 못 받은 경우).
 * 이름에 난수를 넣으면 그 신호가 사라지고, 대신 아무도 안 보는 중복 잡이 조용히 하나 더 생긴다.
 * 네임스페이스는 결정성을 그대로 두고 <b>네임스페이스만 옮긴다.</b>
 *
 * <h2>기본값은 빈 문자열이다</h2>
 * 안 주면 예전 이름 그대로 나온다. 이름은 provider_job_name 에 저장되고 폴링은 저장된 값으로
 * 조회하므로(SttJobResultPort.fetch), 규칙을 바꿔도 이미 떠 있는 블록은 영향을 받지 않는다.
 */
@Component
public class SttJobNameFactory {

    /*
     * Transcribe 가 잡 이름에 허용하는 문자다. 여기서 막지 않으면 제출 시점에 제공자가
     * 거절하는데, 그때는 이미 블록이 만들어진 뒤라 "설정이 틀렸다"가 아니라 "STT 가 실패한다"로
     * 보인다 — 설정 실수는 부팅에서 끊는다(SttTranscribeProperties 와 같은 관용구).
     */
    private static final Pattern ALLOWED_NAMESPACE = Pattern.compile("[0-9a-zA-Z._-]*");

    /*
     * 이름 전체 상한은 200 이다(Transcribe 상한 · provider_job_name VARCHAR(200)). 나머지 부분이
     * 최악(meetingId 19자리·blockSeq/retryCount 10자리)이어도 56자를 넘지 않으므로, 네임스페이스만
     * 여기서 묶으면 완성된 이름이 상한을 넘을 수 없다.
     */
    private static final int MAX_NAMESPACE_LENGTH = 64;

    private final String namespace;

    public SttJobNameFactory(@Value("${stt.job-name-namespace:}") String namespace) {
        String trimmed = namespace == null ? "" : namespace.trim();
        if (!ALLOWED_NAMESPACE.matcher(trimmed).matches()) {
            throw new IllegalStateException("stt.job-name-namespace 에는 영숫자와 . _ - 만 쓸 수 있습니다: "
                    + trimmed);
        }
        if (trimmed.length() > MAX_NAMESPACE_LENGTH) {
            throw new IllegalStateException("stt.job-name-namespace 는 " + MAX_NAMESPACE_LENGTH
                    + "자 이하여야 합니다: " + trimmed.length() + "자");
        }
        this.namespace = trimmed;
    }

    /*
     * 이 블록·이 시도의 잡 이름 — `meeting-500-block-3-r3`, 네임스페이스가 있으면 `stg1-` 이
     * 앞에 붙는다.
     *
     * retryCount 를 이름에 넣는 이유는 그대로다 — 같은 블록을 다시 돌릴 때 이름이 갈려야
     * 제출이 거절되지 않는다(V5.4 주석).
     */
    public String create(long meetingId, int blockSeq, int retryCount) {
        String name = "meeting-" + meetingId + "-block-" + blockSeq + "-r" + retryCount;
        return namespace.isEmpty() ? name : namespace + "-" + name;
    }
}
