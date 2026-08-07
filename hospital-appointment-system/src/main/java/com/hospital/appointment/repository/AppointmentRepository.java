package com.hospital.appointment.repository;

import com.hospital.appointment.entity.Appointment;
import com.hospital.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate date);

    List<Appointment> findByPatientIdOrderByAppointmentDateDesc(Long patientId);

    List<Appointment> findByDoctorIdOrderByAppointmentDateDesc(Long doctorId);

    /**
     * Core conflict-detection query.
     * Two time ranges [start1,end1) and [start2,end2) overlap when:
     *   start1 < end2 AND end1 > start2
     * Only PENDING/CONFIRMED appointments count as real conflicts —
     * a CANCELLED slot should be bookable again.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.doctor.id = :doctorId
          AND a.appointmentDate = :date
          AND a.status IN (com.hospital.appointment.entity.AppointmentStatus.PENDING,
                            com.hospital.appointment.entity.AppointmentStatus.CONFIRMED)
          AND a.startTime < :endTime
          AND a.endTime > :startTime
        """)
    List<Appointment> findConflictingAppointments(
        @Param("doctorId") Long doctorId,
        @Param("date") LocalDate date,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime
    );

    /** Used by the reminder scheduler to find appointments starting within a window. */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status = com.hospital.appointment.entity.AppointmentStatus.CONFIRMED
          AND a.appointmentDate = :date
        """)
    List<Appointment> findConfirmedAppointmentsOnDate(@Param("date") LocalDate date);
}
