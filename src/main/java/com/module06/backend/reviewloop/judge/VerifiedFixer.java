package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 💤 <b>휴면(dormant)</b> — {@link CodeFixerPort} 데코레이터. 제안된 수정을 디스크에 쓰고 컴파일 검증한다.
 * 실패하면 원본으로 롤백하고 '무변경'(원본 코드)을 반환한다 →
 * 다음 라운드에서 동일 판정이 반복되다 budget 소진으로 종료(= 사람 인계). AutoFixRunner 내부는 건드리지 않는다.
 *
 * <p>통합 설계(review-loop/UNIFIED_DESIGN.md §3.1)에서 내려온 이유: <b>자동 롤백이 불필요해졌다.</b>
 * 무인 fixer를 방어하려던 장치인데, 이제 수정 주체가 맥락을 아는 드라이버(Claude Code)라
 * "실패 이유를 읽고 다시 고치는" 편이 항상 낫다. 드라이버 검증은 scripts/review-verify.sh(전체 컴파일).
 *
 * <p>writeRoot는 파일이 실제 위치하는 디렉터리(= 해당 파일용 AutoFixRunner의 repoRoot). filePath는 파일명.
 */
public class VerifiedFixer implements CodeFixerPort {

    private final CodeFixerPort delegate;
    private final VerificationPort verify;
    private final Path writeRoot;

    public VerifiedFixer(CodeFixerPort delegate, VerificationPort verify, Path writeRoot) {
        this.delegate = delegate;
        this.verify = verify;
        this.writeRoot = writeRoot;
    }

    @Override
    public String fix(String filePath, String code, List<Finding> findings) {
        String proposed = delegate.fix(filePath, code, findings);
        Path target = writeRoot.resolve(filePath);
        try {
            Files.writeString(target, proposed);
            VerifyResult r = verify.verify(target);
            if (r.ok()) {
                return proposed;
            }
            Files.writeString(target, code);   // 컴파일 실패 → 롤백
            System.out.println("[autoloop] 컴파일 실패 → 롤백(무변경): " + filePath);
            if (r.log() != null && !r.log().isBlank()) {
                System.out.println(r.log().strip());
            }
            return code;
        } catch (IOException e) {
            throw new UncheckedIOException("수정본 디스크 쓰기 실패: " + target, e);
        }
    }
}
