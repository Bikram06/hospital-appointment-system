package com.hospital.appointment.dto;

import com.hospital.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentDtos {

    public record DoctorSummaryDto(
        Long id,
        String name,
        String specialization,
        String email
    ) {}

    public record BookAppointmentRequest(
        @NotNull Long doctorId,
        @NotNull @Future LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        String reasonForVisit
    ) {}

    public record AppointmentResponse(
        Long id,
        Long doctorId,
        String doctorName,
        String specialization,
        Long patientId,
        String patientName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String reasonForVisit
    ) {}

    public record SlotDto(
        LocalTime startTime,
        LocalTime endTime,
        boolean available
    ) {}

    public record UpdateStatusRequest(
        @NotNull AppointmentStatus status
    ) {}

    public record DoctorAvailabilityDto(
        Long id,
        String day,
        String startTime,
        String endTime
    ) {}

    public record AdminDashboardDto(
        long totalDoctors,
        long totalPatients,
        long totalAppointments,
        long pendingAppointments,
        long confirmedAppointments,
        String featuredDoctor
    ) {}

    public record ReminderSummaryDto(
        int upcomingCount,
        String nextAppointment,
        String message
    ) {}
}
