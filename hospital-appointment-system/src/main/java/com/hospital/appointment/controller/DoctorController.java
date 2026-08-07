package com.hospital.appointment.controller;

import com.hospital.appointment.dto.AppointmentDtos.DoctorSummaryDto;
import com.hospital.appointment.dto.AppointmentDtos.SlotDto;
import com.hospital.appointment.entity.Doctor;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<DoctorSummaryDto>> listDoctors(
        @RequestParam(required = false) String specialization
    ) {
        List<Doctor> doctors = specialization == null || specialization.isBlank()
            ? doctorRepository.findAll()
            : doctorRepository.findBySpecializationContainingIgnoreCase(specialization);

        List<DoctorSummaryDto> summaries = doctors.stream()
            .map(doctor -> new DoctorSummaryDto(
                doctor.getId(),
                doctor.getUser() != null ? doctor.getUser().getName() : "Doctor",
                doctor.getSpecialization(),
                doctor.getUser() != null ? doctor.getUser().getEmail() : null
            ))
            .toList();

        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<List<SlotDto>> getSlots(
        @PathVariable Long id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(appointmentService.getAvailableSlots(id, date));
    }
}
