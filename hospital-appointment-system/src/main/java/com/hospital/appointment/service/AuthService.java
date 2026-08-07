package com.hospital.appointment.service;

import com.hospital.appointment.dto.AuthDtos.*;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.ApiExceptions.BadRequestException;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.repository.PatientRepository;
import com.hospital.appointment.repository.UserRepository;
import com.hospital.appointment.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email already registered");
        }

        Role role;
        try {
            role = Role.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role must be DOCTOR or PATIENT");
        }
        if (role == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be self-registered");
        }

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone());
        user.setRole(role);
        user = userRepository.save(user);

        if (role == Role.DOCTOR) {
            if (req.specialization() == null || req.specialization().isBlank()) {
                throw new BadRequestException("Specialization is required for doctors");
            }
            Doctor doctor = new Doctor();
            doctor.setUser(user);
            doctor.setSpecialization(req.specialization());
            doctorRepository.save(doctor);
        } else {
            Patient patient = new Patient();
            patient.setUser(user);
            patientRepository.save(patient);
        }

        String token = jwtService.generateToken(toUserDetails(user), Map.of("role", role.name()));
        return new AuthResponse(token, user.getId(), user.getName(), role);
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email())
            .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        String token = jwtService.generateToken(toUserDetails(user), Map.of("role", user.getRole().name()));
        return new AuthResponse(token, user.getId(), user.getName(), user.getRole());
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .authorities("ROLE_" + user.getRole().name())
            .build();
    }
}
