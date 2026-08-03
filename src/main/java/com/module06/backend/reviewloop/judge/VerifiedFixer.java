package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * P1 — {@link CodeFixerPort} 데코레이터. 제안된 수정을 디스크에 쓰고 컴파일 검증한다.
 * 실패하면 원본으로 롤백하고 '무변경'(원본 코드)을 반환한다 →
 * 다음 라운드에서 동일 판정이 반복되다 budget 소진으로 종료(= 사람 인계). AutoFixRunner 내부는 건드리지 않는다.
 *
 * writeRoot는 파일이 실제 위치하는 디렉터리(= 해당 파일용 AutoFixRunner의 repoRoot). filePath는 파일명.
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
