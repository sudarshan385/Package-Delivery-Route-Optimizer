package com.routeoptimizer.entity;

import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Package extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 50)
    private String trackingNumber;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "sender_phone", length = 20)
    private String senderPhone;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    @Column(name = "delivery_location", length = 255)
    private String deliveryLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = true)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id", nullable = true)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = true)
    private Location destinationLocation;

    @Column(nullable = false)
    private Double weight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PackageStatus status = PackageStatus.PENDING;

    @Column(length = 255)
    private String description;

    @Column(name = "item_name", length = 150)
    private String itemName;

    @Column(name = "item_category", length = 100)
    private String itemCategory;

    @Column
    @Builder.Default
    private Integer quantity = 1;

    @Column
    @Builder.Default
    private Boolean fragile = false;

    @Column(name = "declared_value")
    private Double declaredValue;

    @Column(name = "length_cm")
    private Double lengthCm;

    @Column(name = "width_cm")
    private Double widthCm;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "pickup_instructions", length = 500)
    private String pickupInstructions;

    @Column(name = "delivery_instructions", length = 500)
    private String deliveryInstructions;

    @Column(name = "sender_email", length = 100)
    private String senderEmail;

    @Column(name = "receiver_email", length = 100)
    private String receiverEmail;
}
