package com.module06.backend.architecture.fixture.application;

import com.module06.backend.architecture.fixture.infrastructure.S3Adapter;

/** ❌ ARCH_003 위반 픽스처 — 유스케이스가 어댑터를 직접 참조한다(포트 미경유). */
public class ViolatingService {

    private final S3Adapter adapter;

    public ViolatingService(S3Adapter adapter) {
        this.adapter = adapter;
    }

    public String url(String key) {
        return adapter.presign(key);
    }
}
