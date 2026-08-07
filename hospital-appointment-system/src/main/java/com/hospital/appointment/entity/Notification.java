package com.hospital.appointment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false)
    private String type; // EMAIL or SMS (mocked)

    @Column(length = 1000)
    private String message;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, SENT, FAILED
}
