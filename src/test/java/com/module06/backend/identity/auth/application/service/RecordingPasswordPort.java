package com.module06.backend.identity.auth.application.service;

import java.util.ArrayList;
import java.util.List;

import com.module06.backend.identity.member.application.port.out.MemberPasswordPort;

/**
 * 비밀번호 저장소 대역. 실제 구현이 하는 일(직전 해시를 이력으로 옮기고 새 해시로 바꾸기)을
 * 메모리에서 그대로 흉내 낸다 — 그래야 "두 번 바꾸면 첫 번째 값으로 못 돌아간다"를 검증할 수 있다.
 *
 * <p>{@code currentHash} 를 여기서도 들고 있는 이유: 서비스는 현재 해시를 {@code MemberAuthQueryPort}
 * 로 읽는데, 테스트에서 두 대역이 같은 값을 보게 하려면 한쪽이 바뀔 때 다른 쪽도 따라와야 한다.
 */
class RecordingPasswordPort implements MemberPasswordPort {

    private final List<String> history = new ArrayList<>();
    private String currentHash;
    private int changeCount;

    RecordingPasswordPort() {
        this(null);
    }

    RecordingPasswordPort(String currentHash) {
        this.currentHash = currentHash;
    }

    @Override
    public List<String> findUsedPasswordHashes(Long memberId, Long companyId) {
        return List.copyOf(history);
    }

    @Override
    public void changePassword(Long memberId, Long companyId, String newPasswordHash) {
        if (currentHash != null) {
            history.add(currentHash);
        }
        currentHash = newPasswordHash;
        changeCount++;
    }

    /** 변경과 달리 변경 시각을 비우지만, 이 대역은 시각을 들고 있지 않아 적재 규칙만 같게 흉내 낸다. */
    @Override
    public void resetPassword(Long memberId, Long companyId, String newPasswordHash) {
        changePassword(memberId, companyId, newPasswordHash);
    }

    String currentHash() {
        return currentHash;
    }

    int changeCount() {
        return changeCount;
    }
}
