package com.hospital.appointment.controller;

import com.hospital.appointment.dto.AppointmentDtos.AdminDashboardDto;
import com.hospital.appointment.dto.AppointmentDtos.DoctorAvailabilityDto;
import com.hospital.appointment.dto.AppointmentDtos.ReminderSummaryDto;
import com.hospital.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final AppointmentService appointmentService;

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardDto> adminDashboard() {
        return ResponseEntity.ok(appointmentService.getAdminDashboard());
    }

    @GetMapping("/doctors/{id}/availability")
    public ResponseEntity<List<DoctorAvailabilityDto>> doctorAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getDoctorAvailability(id));
    }

    @GetMapping("/appointments/reminders")
    public ResponseEntity<ReminderSummaryDto> reminders() {
        return ResponseEntity.ok(appointmentService.getReminderSummary());
    }
}
