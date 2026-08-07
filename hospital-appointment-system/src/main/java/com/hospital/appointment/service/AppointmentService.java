package com.hospital.appointment.service;

import com.hospital.appointment.dto.AppointmentDtos.*;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.ApiExceptions.*;
import com.hospital.appointment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final NotificationService notificationService;

    /**
     * Books an appointment for a patient with a doctor.
     *
     * Conflict prevention has two layers:
     *  1. Application-level: query for any PENDING/CONFIRMED appointment for
     *     this doctor on this date whose time range overlaps the requested one.
     *  2. Database-level: the unique constraint on (doctor_id, appointment_date,
     *     start_time) in the Appointment entity rejects the insert if two
     *     requests race past the application check at the same instant.
     *
     * SERIALIZABLE isolation on this transaction ensures the overlap check
     * and the insert are treated as one atomic unit against concurrent
     * bookings for the same doctor/date.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppointmentResponse bookAppointment(Long patientUserId, BookAppointmentRequest req) {
        Doctor doctor = doctorRepository.findById(req.doctorId())
            .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + req.doctorId()));

        Patient patient = patientRepository.findByUserId(patientUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        LocalTime startTime = req.startTime();
        LocalTime endTime = startTime.plusMinutes(doctor.getSlotDurationMinutes());

        validateWithinAvailability(doctor, req.appointmentDate(), startTime, endTime);

        List<Appointment> conflicts = appointmentRepository.findConflictingAppointments(
            doctor.getId(), req.appointmentDate(), startTime, endTime
        );
        if (!conflicts.isEmpty()) {
            throw new SlotConflictException(
                "The selected slot (" + startTime + " - " + endTime + ") on " + req.appointmentDate() +
                " is already booked. Please choose another slot."
            );
        }

        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setAppointmentDate(req.appointmentDate());
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);
        appointment.setReasonForVisit(req.reasonForVisit());
        appointment.setStatus(AppointmentStatus.PENDING);

        try {
            appointment = appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException e) {
            // The DB unique constraint caught a race condition the app-level check missed
            throw new SlotConflictException(
                "This slot was just booked by someone else. Please choose another slot."
            );
        }

        notificationService.scheduleBookingConfirmation(appointment);

        return toResponse(appointment);
    }

    private void validateWithinAvailability(Doctor doctor, LocalDate date, LocalTime start, LocalTime end) {
        DayOfWeek day = date.getDayOfWeek();
        List<DoctorAvailability> windows = availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), day);

        boolean fitsInWindow = windows.stream().anyMatch(w ->
            !start.isBefore(w.getStartTime()) && !end.isAfter(w.getEndTime())
        );

        if (!fitsInWindow) {
            throw new BadRequestException(
                "Doctor is not available at " + start + " on " + date + " (" + day + ")"
            );
        }
    }

    /** Generates the bookable slot grid for a doctor on a given date, marking which are taken. */
    @Transactional(readOnly = true)
    public List<SlotDto> getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
            .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        List<DoctorAvailability> windows = availabilityRepository
            .findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek());

        List<Appointment> existing = appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, date);

        List<SlotDto> slots = new ArrayList<>();
        int duration = doctor.getSlotDurationMinutes();

        for (DoctorAvailability window : windows) {
            LocalTime cursor = window.getStartTime();
            while (!cursor.plusMinutes(duration).isAfter(window.getEndTime())) {
                LocalTime slotEnd = cursor.plusMinutes(duration);
                LocalTime finalCursor = cursor;
                boolean taken = existing.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED)
                    .anyMatch(a -> finalCursor.isBefore(a.getEndTime()) && slotEnd.isAfter(a.getStartTime()));

                slots.add(new SlotDto(cursor, slotEnd, !taken));
                cursor = slotEnd;
            }
        }
        return slots;
    }

    @Transactional
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        appointment.setStatus(newStatus);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPatientAppointments(Long patientUserId) {
        Patient patient = patientRepository.findByUserId(patientUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patient.getId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorAppointments(Long doctorUserId) {
        Doctor doctor = doctorRepository.findByUserId(doctorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));
        return appointmentRepository.findByDoctorIdOrderByAppointmentDateDesc(doctor.getId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminDashboardDto getAdminDashboard() {
        long totalDoctors = doctorRepository.count();
        long totalPatients = patientRepository.count();
        long totalAppointments = appointmentRepository.count();
        long pendingAppointments = appointmentRepository.findAll().stream()
            .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
            .count();
        long confirmedAppointments = appointmentRepository.findAll().stream()
            .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
            .count();
        String featuredDoctor = doctorRepository.findAll().stream()
            .findFirst()
            .map(d -> d.getUser().getName())
            .orElse("No doctors yet");

        return new AdminDashboardDto(
            totalDoctors,
            totalPatients,
            totalAppointments,
            pendingAppointments,
            confirmedAppointments,
            featuredDoctor
        );
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityDto> getDoctorAvailability(Long doctorId) {
        return availabilityRepository.findByDoctorId(doctorId).stream()
            .map(a -> new DoctorAvailabilityDto(
                a.getId(),
                a.getDayOfWeek().name(),
                a.getStartTime().toString(),
                a.getEndTime().toString()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public ReminderSummaryDto getReminderSummary() {
        List<Appointment> upcoming = appointmentRepository.findAll().stream()
            .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
            .sorted(Comparator.comparing(Appointment::getAppointmentDate).thenComparing(Appointment::getStartTime))
            .toList();

        if (upcoming.isEmpty()) {
            return new ReminderSummaryDto(0, "No upcoming appointments", "No reminder messages to send right now.");
        }

        Appointment next = upcoming.get(0);
        return new ReminderSummaryDto(
            upcoming.size(),
            next.getAppointmentDate() + " at " + next.getStartTime() + " with " + next.getDoctor().getUser().getName(),
            "Reminder queued for " + next.getPatient().getUser().getName() + " for their visit with " + next.getDoctor().getUser().getName() + "."
        );
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(
            a.getId(),
            a.getDoctor().getId(),
            a.getDoctor().getUser().getName(),
            a.getDoctor().getSpecialization(),
            a.getPatient().getId(),
            a.getPatient().getUser().getName(),
            a.getAppointmentDate(),
            a.getStartTime(),
            a.getEndTime(),
            a.getStatus(),
            a.getReasonForVisit()
        );
    }
}
