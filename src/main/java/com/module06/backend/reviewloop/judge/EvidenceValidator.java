package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 환각(hallucination) finding 차단 — finding이 가리키는 file:line이 실제로 존재하는지 검증한다.
 *
 * LLM은 없는 파일·없는 라인을 지적할 수 있다. 근거가 없는 finding을 채점에 넣으면
 * "AI가 지어낸 지적으로 점수가 깎였다"가 된다. 그래서 채점 전에 이 검증을 통과한 finding만 남긴다.
 * (Promptfoo recall 실측과 함께 Judge 신뢰의 필수 세트.)
 */
public class EvidenceValidator {

    private final Path repoRoot;

    public EvidenceValidator(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    /** file이 실재하고, line이 파일 라인 수 이내면 근거 있음. line<=0(파일 단위 지적)은 파일 존재만 본다. */
    public boolean isGrounded(Finding finding) {
        if (finding.file() == null || finding.file().isBlank()) {
            return false;
        }
        Path target = repoRoot.resolve(finding.file());
        if (!Files.isRegularFile(target)) {
            return false;
        }
        if (finding.line() <= 0) {
            return true;
        }
        try (var lines = Files.lines(target)) {
            long lineCount = lines.count();
            return finding.line() <= lineCount;
        } catch (IOException e) {
            return false;
        }
    }

    /** 근거 있는 finding만 남긴다(환각 제거). */
    public List<Finding> keepGrounded(List<Finding> findings) {
        return findings.stream().filter(this::isGrounded).toList();
    }
}
