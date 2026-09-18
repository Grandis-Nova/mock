package com.grandis.nova.mockapi.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 외부 예약 Mock 의 등록 원장. 키 하나에 행 하나다.
 *
 * <p>스키마는 {@code docs/schema.sql} 이 정본이다. 칸을 바꾸면 그쪽도 함께 고친다.
 *
 * <p>등록·취소 동작(멱등 재생, 내용 비교, 취소 표식)은 등록 파트(NV-3)에서 채운다.
 */
@Entity
@Table(name = "preorder_registrations")
public class Registration {

    /** 본 서비스 preorders.preorder_token 을 그대로 쓴다. */
    @Id
    @Column(name = "external_key", length = 100, nullable = false, updatable = false)
    private String externalKey;

    /** Mock 이 최초 등록에서 발급한다. 등록 전 취소 표식이면 null. */
    @Column(name = "external_number", length = 100)
    private String externalNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku", length = 80)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RegistrationStatus status;

    /** UTC. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** UTC. 등록 전에 취소가 와도 이 값만 남는다. */
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    protected Registration() {
    }

    private Registration(String externalKey, String externalNumber, Long customerId, Long productId,
                         String sku, RegistrationStatus status,
                         LocalDateTime confirmedAt, LocalDateTime canceledAt) {
        this.externalKey = externalKey;
        this.externalNumber = externalNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.sku = sku;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.canceledAt = canceledAt;
    }

    /** 등록 성공 행. */
    public static Registration active(String externalKey, String externalNumber, Long customerId,
                                      Long productId, String sku, LocalDateTime confirmedAt) {
        return new Registration(externalKey, externalNumber, customerId, productId, sku,
                RegistrationStatus.ACTIVE, confirmedAt, null);
    }

    /** 등록 전 취소로 남기는 표식 행. 번호가 없다. */
    public static Registration cancelMarker(String externalKey, LocalDateTime canceledAt) {
        return new Registration(externalKey, null, null, null, null,
                RegistrationStatus.CANCELED, null, canceledAt);
    }

    public boolean isActive() {
        return status == RegistrationStatus.ACTIVE;
    }

    public boolean isCanceled() {
        return status == RegistrationStatus.CANCELED;
    }

    /** 같은 키의 재요청이 같은 신청인지. 저장된 세 칸을 직접 비교한다. */
    public boolean sameContent(Long customerId, Long productId, String sku) {
        return java.util.Objects.equals(this.customerId, customerId)
                && java.util.Objects.equals(this.productId, productId)
                && java.util.Objects.equals(this.sku, sku);
    }

    /** 취소한다. 이미 취소된 행은 그대로 둔다(재활성화·시각 덮어쓰기 없음). */
    public void cancel(LocalDateTime canceledAt) {
        if (isCanceled()) {
            return;
        }
        this.status = RegistrationStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public String externalKey() {
        return externalKey;
    }

    public String externalNumber() {
        return externalNumber;
    }

    public Long customerId() {
        return customerId;
    }

    public Long productId() {
        return productId;
    }

    public String sku() {
        return sku;
    }

    public RegistrationStatus status() {
        return status;
    }

    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime canceledAt() {
        return canceledAt;
    }
}
