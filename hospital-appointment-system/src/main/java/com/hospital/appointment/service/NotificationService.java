package com.hospital.appointment.service;

import com.hospital.appointment.entity.Appointment;
import com.hospital.appointment.entity.Notification;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mocked notifications: instead of wiring up real SMTP/SMS providers, this
 * logs and persists a Notification row. Swap sendEmail()/sendSms() for
 * JavaMailSender or Twilio calls when you're ready to make it real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;

    public void scheduleBookingConfirmation(Appointment appointment) {
        String message = String.format(
            "Appointment booked with Dr. %s on %s at %s.",
            appointment.getDoctor().getUser().getName(),
            appointment.getAppointmentDate(),
            appointment.getStartTime()
        );
        sendMock(appointment, "EMAIL", message);
    }

    /** Runs every hour; sends reminders for appointments happening tomorrow. */
    @Scheduled(cron = "0 0 * * * *")
    public void sendDayBeforeReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> upcoming = appointmentRepository.findConfirmedAppointmentsOnDate(tomorrow);

        for (Appointment appointment : upcoming) {
            String message = String.format(
                "Reminder: appointment with Dr. %s tomorrow at %s.",
                appointment.getDoctor().getUser().getName(),
                appointment.getStartTime()
            );
            sendMock(appointment, "SMS", message);
        }
        if (!upcoming.isEmpty()) {
            log.info("Sent {} reminder notifications for {}", upcoming.size(), tomorrow);
        }
    }

    private void sendMock(Appointment appointment, String type, String message) {
        log.info("[MOCK {}] To patient {}: {}", type, appointment.getPatient().getUser().getEmail(), message);

        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setType(type);
        notification.setMessage(message);
        notification.setSentAt(LocalDateTime.now());
        notification.setStatus("SENT");
        notificationRepository.save(notification);
    }
}
