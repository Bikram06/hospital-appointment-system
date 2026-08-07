package com.hospital.appointment.config;

import com.hospital.appointment.entity.Appointment;
import com.hospital.appointment.entity.AppointmentStatus;
import com.hospital.appointment.entity.Doctor;
import com.hospital.appointment.entity.DoctorAvailability;
import com.hospital.appointment.entity.Patient;
import com.hospital.appointment.entity.Role;
import com.hospital.appointment.entity.User;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.DoctorAvailabilityRepository;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.repository.PatientRepository;
import com.hospital.appointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedDemoDoctors();
        log.info("Seeded demo clinic data for dashboards and availability management");
    }

    private void seedAdmin() {
        String adminEmail = "admin@hospital.com";
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }
        User admin = new User();
        admin.setName("System Admin");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode("Admin@123"));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.info("Seeded default admin account -> email: {}, password: Admin@123 (change this immediately)", adminEmail);
    }

    private void seedDemoDoctors() {
        if (doctorRepository.count() > 0) {
            return;
        }

        User doctorOneUser = createUser("Dr. Asha Rao", "asha@hospital.com", Role.DOCTOR);
        User doctorTwoUser = createUser("Dr. Michael Chen", "michael@hospital.com", Role.DOCTOR);
        User doctorThreeUser = createUser("Dr. Priya Shah", "priya@hospital.com", Role.DOCTOR);
        User doctorFourUser = createUser("Dr. Daniel Kim", "daniel@hospital.com", Role.DOCTOR);
        User doctorFiveUser = createUser("Dr. Sara Patel", "sara@hospital.com", Role.DOCTOR);
        User patientUser = createUser("Riya Patel", "riya@hospital.com", Role.PATIENT);

        Doctor doctorOne = createDoctor(doctorOneUser, "Cardiology", 45.0);
        Doctor doctorTwo = createDoctor(doctorTwoUser, "Neurology", 50.0);
        Doctor doctorThree = createDoctor(doctorThreeUser, "Dermatology", 40.0);
        Doctor doctorFour = createDoctor(doctorFourUser, "Orthopedics", 55.0);
        Doctor doctorFive = createDoctor(doctorFiveUser, "Pediatrics", 38.0);

        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setDateOfBirth(java.time.LocalDate.of(1993, 4, 12));
        patient.setGender("Female");
        patient.setEmergencyContact("+91 98765 43210");
        patientRepository.save(patient);

        saveAvailability(doctorOne, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        saveAvailability(doctorOne, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(17, 0));
        saveAvailability(doctorTwo, DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(13, 0));
        saveAvailability(doctorThree, DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(15, 0));
        saveAvailability(doctorFour, DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(11, 0));
        saveAvailability(doctorFive, DayOfWeek.FRIDAY, LocalTime.of(9, 30), LocalTime.of(13, 0));

        Appointment demoAppointment = new Appointment();
        demoAppointment.setDoctor(doctorOne);
        demoAppointment.setPatient(patient);
        demoAppointment.setAppointmentDate(LocalDate.now().plusDays(1));
        demoAppointment.setStartTime(LocalTime.of(10, 0));
        demoAppointment.setEndTime(LocalTime.of(10, 30));
        demoAppointment.setReasonForVisit("Routine cardiology follow-up");
        demoAppointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(demoAppointment);
    }

    private Doctor createDoctor(User user, String specialization, Double consultationFee) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization(specialization);
        doctor.setConsultationFee(consultationFee);
        doctor.setSlotDurationMinutes(30);
        return doctorRepository.save(doctor);
    }

    private User createUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Demo@123"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private void saveAvailability(Doctor doctor, DayOfWeek day, LocalTime start, LocalTime end) {
        DoctorAvailability availability = new DoctorAvailability();
        availability.setDoctor(doctor);
        availability.setDayOfWeek(day);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availabilityRepository.save(availability);
    }
}
