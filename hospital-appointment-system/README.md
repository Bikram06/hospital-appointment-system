# Hospital Appointment Management System — Backend

Spring Boot + MySQL backend for a hospital appointment booking system with
role-based access (Admin / Doctor / Patient), JWT auth, and conflict-safe
slot booking.

## Tech Stack
- Java 17, Spring Boot 3.3
- Spring Security + JWT (jjwt)
- Spring Data JPA / Hibernate
- MySQL
- Lombok

## Setup

1. **Create the database** (or let it auto-create — see `application.properties`):
   ```sql
   CREATE DATABASE hospital_db;
   ```

2. **Configure credentials** in `src/main/resources/application.properties`:
   ```
   spring.datasource.username=root
   spring.datasource.password=your_password_here
   ```
   Also replace `app.jwt.secret` with your own long random string before
   deploying anywhere real.

3. **Run**:
   ```bash
   ./mvnw spring-boot:run
   ```
   Server starts on `http://localhost:8080`.

4. A default admin is seeded on first run:
   - email: `admin@hospital.com`
   - password: `Admin@123`
   (Change this immediately — it's here only so you have a way in.)

## API Overview

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register as DOCTOR or PATIENT |
| POST | `/api/auth/login` | Public | Returns JWT |
| GET | `/api/doctors` | Public | List doctors, `?specialization=` filter |
| GET | `/api/doctors/{id}/slots?date=YYYY-MM-DD` | Public | Available slots for a date |
| POST | `/api/appointments` | PATIENT | Book an appointment |
| GET | `/api/appointments/my` | PATIENT | Own appointment history |
| PUT | `/api/appointments/{id}/cancel` | PATIENT | Cancel own appointment |
| GET | `/api/appointments/doctor` | DOCTOR | Doctor's own schedule |
| PUT | `/api/appointments/{id}/status` | DOCTOR | Confirm/complete an appointment |
| GET | `/api/admin/stats` | ADMIN | Dashboard counts |
| GET | `/api/admin/users` | ADMIN | List all users |

Send the JWT from login as `Authorization: Bearer <token>` on protected routes.

## What's Not Included Yet (your next steps)

This scaffold covers the backend core. To finish the project:

1. **Doctor availability setup** — currently `DoctorAvailability` rows must be
   inserted manually (or via a quick admin endpoint you add) before slots
   will show up for a doctor. Add a `POST /api/doctors/availability` endpoint
   for doctors to self-manage their weekly hours.
2. **React frontend** — wire up login/register, the doctor list + slot
   picker, and role-based dashboards against these APIs.
3. **Unit tests** — write tests for `AppointmentService.bookAppointment()`
   specifically the overlap-conflict branch; this is the single most
   interview-worthy piece of logic in the project.
4. **Real notifications** — swap `NotificationService`'s `sendMock()` for
   `JavaMailSender` (Gmail SMTP) or Twilio if you want it to actually send.
5. **Deploy** — Render/Railway for the backend + a managed MySQL instance,
   Vercel/Netlify for the React frontend.

## Why the Conflict Logic Is Built the Way It Is

`AppointmentService.bookAppointment()` uses two layers of protection:
1. An application-level query (`findConflictingAppointments`) checks for any
   overlapping PENDING/CONFIRMED appointment before inserting.
2. A database-level unique constraint on `(doctor_id, appointment_date,
   start_time)` catches the case where two requests race past the
   application check at the exact same instant — the second insert fails
   and the service returns a clean 409 Conflict instead of silently
   double-booking a doctor.

This combination (check + constraint, wrapped in a SERIALIZABLE
transaction) is worth explaining in interviews — it shows you're thinking
about concurrency, not just the happy path.
