package com.module06.backend.architecture.fixture.infrastructure;

/** 픽스처 — application이 직접 참조하면 안 되는 어댑터 역할. */
public class S3Adapter {
    public String presign(String key) {
        return "https://example.invalid/" + key;
    }
}
