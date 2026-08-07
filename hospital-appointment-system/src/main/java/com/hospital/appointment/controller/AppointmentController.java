package com.hospital.appointment.controller;

import com.hospital.appointment.dto.AppointmentDtos.*;
import com.hospital.appointment.entity.AppointmentStatus;
import com.hospital.appointment.entity.User;
import com.hospital.appointment.exception.ApiExceptions.ResourceNotFoundException;
import com.hospital.appointment.repository.UserRepository;
import com.hospital.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    /** Books an appointment. Only patients can book for themselves. */
    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> book(
        @AuthenticationPrincipal UserDetails principal,
        @Valid @RequestBody BookAppointmentRequest request
    ) {
        Long userId = currentUserId(principal);
        return ResponseEntity.ok(appointmentService.bookAppointment(userId, request));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(appointmentService.getPatientAppointments(currentUserId(principal)));
    }

    @GetMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> doctorAppointments(
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(appointmentService.getDoctorAppointments(currentUserId(principal)));
    }

    /** Patient cancels their own appointment. */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, AppointmentStatus.CANCELLED));
    }

    /** Doctor confirms or marks an appointment complete. */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateStatusRequest request
    ) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, request.status()));
    }

    private Long currentUserId(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
