package com.hospital.appointment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The unique constraint below is the safety net against race conditions:
 * even if two requests pass the application-level overlap check at the
 * same instant, the database will reject the second INSERT for the exact
 * same doctor/date/startTime combination.
 *
 * Note: this only blocks identical start times. True overlap prevention
 * (e.g. a 30-min slot booked inside a 60-min slot) is handled in
 * AppointmentService using an application-level overlap query wrapped
 * in a transaction — see the service class for details.
 */
@Entity
@Table(
    name = "appointments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_doctor_date_start",
        columnNames = {"doctor_id", "appointment_date", "start_time"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 1000)
    private String reasonForVisit;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
