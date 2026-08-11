package com.module06.backend.metering.application.port.in;

import com.module06.backend.metering.application.result.StorageQuotaStatusResult;

/*
 * cap이 청크 업로드 presign 발급 전 저장 용량 한도를 확인하는 경계(TokenQuotaPort와 동일 패턴).
 */
public interface StorageQuotaPort {

    StorageQuotaStatusResult getStatus(Long companyId);
}
