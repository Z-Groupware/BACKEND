package com.module06.backend.cap.infrastructure.storage;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;

/*
 * CapObjectStoragePort의 실제 S3 구현체(#155) — CapObjectStorageStubAdapter를 prod에서 대체한다.
 * PUT/GET 둘 다 presigned URL만 서버가 발급하고, 실제 바이트 전송은 클라이언트가 S3와 직접
 * 주고받는다 — 녹음 파일이 이 서버를 거치지 않는다(대역폭·메모리 부담 없음).
 *
 * ⚠️ HTTP Range(재생 탐색바 seek)는 별도 서명이 필요 없다 — Range 헤더는 SigV4 서명 대상이
 * 아니라서, presigned GET URL에 클라이언트가 Range를 얹어 요청하면 S3가 그대로 처리한다.
 *
 * 업로드 크기 상한은 여기서 강제하지 않는다(presigned PUT 자체엔 크기 제한 조건을 걸 수 있지만
 * 이번 범위 밖) — 기존 CAP-07 정책대로 애플리케이션 레이어(RecordingPart.MAX_SIZE_BYTES)가 검증한다.
 */
@Component
@Profile("prod")
public class CapS3ObjectStorageAdapter implements CapObjectStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CapS3ObjectStorageAdapter.class);

    private static final Duration UPLOAD_URL_TTL = Duration.ofSeconds(900);      // 15분 — CAP-04 문서값
    private static final Duration PLAYBACK_URL_TTL = Duration.ofSeconds(10_800); // 3시간 — 재생 URL 확정값

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public CapS3ObjectStorageAdapter(S3Client s3Client, S3Presigner s3Presigner, CapS3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new IssuedPartUploadUrl(presigned.url().toString(), (int) UPLOAD_URL_TTL.getSeconds());
    }

    @Override
    public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PLAYBACK_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new IssuedPlaybackUrl(presigned.url().toString(), (int) PLAYBACK_URL_TTL.getSeconds());
    }

    @Override
    public void deleteRecording(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build());
    }

    // HEAD로 실제 존재·크기를 확인한다.
    //
    // 없는 키에 대한 응답이 IAM 정책에 따라 갈린다 — s3:ListBucket 권한이 있으면 S3가
    // NoSuchKeyException(404)을 명확히 돌려주지만, 최소권한 정책(리스트 권한 없음)에서는 존재
    // 여부를 숨기려고 **403 AccessDenied**로 돌려준다(S3Exception, NoSuchKeyException이 아니다).
    // NoSuchKeyException만 잡으면 후자 환경에서 원본 S3Exception이 그대로 새어나가 500이 되고,
    // "업로드 안 했다"는 정상 시나리오(CAP_PART_NOT_UPLOADED/CAP_RECORDING_NOT_UPLOADED, 둘 다
    // 400)가 서버 오류로 둔갑한다.
    //
    // 403을 "없음"으로 간주해도 안전한 이유 — 이 어댑터의 다른 메서드(GET presign 등)가 이미 같은
    // 버킷·같은 IAM 역할로 정상 동작한다는 전제이므로, 진짜 광범위한 권한 미스매치라면 403이 이
    // 메서드에만 국한되지 않고 로그에 대량으로 반복될 것이다 — 그래서 WARN으로 남겨 운영에서
    // "그냥 없는 파일"과 "권한 자체가 잘못됨"을 로그 빈도로 구분할 수 있게 한다.
    //
    // 네트워크 타임아웃(SdkClientException 계열)은 여기서 잡지 않는다 — 그건 S3가 응답한 게
    // 아니라 우리 쪽에서 확인 자체를 못 한 것이라, "업로드 안 함"으로 단정하면 안 되고 재시도할
    // 수 있는 500으로 그대로 흘려보내는 게 맞다.
    @Override
    public boolean objectMatches(String s3Key, long expectedSizeBytes) {
        HeadObjectResponse response;
        try {
            response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                log.warn("S3 HEAD 403 — 존재하지 않는 키로 간주한다(최소권한 IAM 환경 예상 동작). "
                        + "이 로그가 대량으로 반복되면 IAM 권한 자체를 의심할 것. s3Key={}", s3Key);
                return false;
            }
            throw e;
        }
        return response.contentLength() != null && response.contentLength() == expectedSizeBytes;
    }
}
