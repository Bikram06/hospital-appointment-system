/* ---------------------------------------------------------------
   Meridian Clinic — frontend for the Hospital Appointment System API
   Talks to the Spring Boot backend described in the project README:
     POST /api/auth/register
     POST /api/auth/login
     GET  /api/doctors
     GET  /api/doctors/{id}/slots?date=YYYY-MM-DD
     POST /api/appointments
     GET  /api/appointments/my
     PUT  /api/appointments/{id}/cancel
     GET  /api/appointments/doctor
     PUT  /api/appointments/{id}/status
     GET  /api/admin/stats
     GET  /api/admin/users
----------------------------------------------------------------*/

const API_BASE = window.API_BASE || "http://localhost:8080";

const state = {
  token: localStorage.getItem("mc_token") || null,
  role: localStorage.getItem("mc_role") || null,
  name: localStorage.getItem("mc_name") || null,
  selectedDoctor: null,
  selectedSlot: null,
};

const fallbackDoctors = [
  { id: 1, name: "Dr. Asha Rao", specialization: "Cardiology", email: "asha@hospital.com", bio: "Specializes in preventive heart care and complex cardiac diagnostics.", nextSlot: "Next available: Today · 09:30" },
  { id: 2, name: "Dr. Michael Chen", specialization: "Neurology", email: "michael@hospital.com", bio: "Focuses on migraine management, stroke recovery, and neurological assessments.", nextSlot: "Next available: Today · 11:00" },
  { id: 3, name: "Dr. Priya Shah", specialization: "Dermatology", email: "priya@hospital.com", bio: "Known for acne treatment, skin allergy care, and cosmetic dermatology.", nextSlot: "Next available: Tomorrow · 10:15" },
  { id: 4, name: "Dr. Daniel Kim", specialization: "Orthopedics", email: "daniel@hospital.com", bio: "Treats sports injuries, joint pain, and post-surgery rehabilitation.", nextSlot: "Next available: Today · 13:00" },
  { id: 5, name: "Dr. Sara Patel", specialization: "Pediatrics", email: "sara@hospital.com", bio: "Provides family-centered care for infants, children, and adolescent wellness.", nextSlot: "Next available: Tomorrow · 08:45" },
  { id: 6, name: "Dr. Rahul Verma", specialization: "Gastroenterology", email: "rahul@hospital.com", bio: "Helps patients manage digestive disorders, reflux, and abdominal pain.", nextSlot: "Next available: Today · 15:30" },
  { id: 7, name: "Dr. Nina Gomez", specialization: "Psychiatry", email: "nina@hospital.com", bio: "Offers counseling support and treatment plans for anxiety, stress, and mood balance.", nextSlot: "Next available: Tomorrow · 14:00" },
  { id: 8, name: "Dr. Arjun Singh", specialization: "Pulmonology", email: "arjun@hospital.com", bio: "Specializes in asthma care, respiratory testing, and chronic lung support.", nextSlot: "Next available: Today · 16:15" },
];

function getFallbackDoctors(spec = "") {
  const term = spec.trim().toLowerCase();
  if (!term) return fallbackDoctors;
  return fallbackDoctors.filter((doc) =>
    (doc.name || "").toLowerCase().includes(term) ||
    (doc.specialization || "").toLowerCase().includes(term)
  );
}

/* ---------------- API helper ---------------- */
async function api(path, { method = "GET", body, auth = false } = {}) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && state.token) headers["Authorization"] = `Bearer ${state.token}`;

  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    throw new Error("Can't reach the server. Is the backend running on " + API_BASE + "?");
  }

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || `Request failed (${res.status})`;
    throw new Error(msg);
  }
  return data;
}

/* ---------------- Toast ---------------- */
let toastTimer;
function toast(msg, type = "") {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.className = "toast " + type;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 3200);
}

/* ---------------- Router ---------------- */
const views = ["home", "login", "register", "doctors", "booking", "dashboard"];

function navigate(name) {
  if (!views.includes(name)) name = "home";

  // Guard: dashboard requires auth
  if (name === "dashboard" && !state.token) name = "login";
  // Guard: booking requires a selected doctor
  if (name === "booking" && !state.selectedDoctor) name = "doctors";
  // Guard: logged-in users skip login/register
  if ((name === "login" || name === "register") && state.token) name = "dashboard";

  views.forEach((v) => {
    document.getElementById(`view-${v}`).hidden = v !== name;
  });
  window.scrollTo({ top: 0, behavior: "smooth" });

  if (name === "doctors") loadDoctors();
  if (name === "dashboard") loadDashboard();

  history.replaceState(null, "", `#${name}`);
}

document.body.addEventListener("click", (e) => {
  const btn = e.target.closest("[data-nav]");
  if (btn) {
    e.preventDefault();
    navigate(btn.dataset.nav);
  }
});

window.addEventListener("DOMContentLoaded", () => {
  refreshAuthUI();
  const initial = location.hash ? location.hash.slice(1) : "home";
  navigate(initial);
  pingApi();
});

/* ---------------- Auth ---------------- */
function refreshAuthUI() {
  const loggedIn = !!state.token;
  document.getElementById("authArea").hidden = loggedIn;
  document.getElementById("userArea").hidden = !loggedIn;
  document.getElementById("navDashboard").hidden = !loggedIn;
  if (loggedIn) {
    document.getElementById("roleBadge").textContent = state.role;
    document.getElementById("userName").textContent = state.name || "";
  }
}

function setSession({ token, role, name }) {
  state.token = token;
  state.role = role;
  state.name = name;
  localStorage.setItem("mc_token", token);
  localStorage.setItem("mc_role", role);
  localStorage.setItem("mc_name", name || "");
  refreshAuthUI();
}

function clearSession() {
  state.token = state.role = state.name = null;
  localStorage.removeItem("mc_token");
  localStorage.removeItem("mc_role");
  localStorage.removeItem("mc_name");
  refreshAuthUI();
}

document.getElementById("logoutBtn").addEventListener("click", () => {
  clearSession();
  toast("Logged out");
  navigate("home");
});

// Register: toggle specialization field for doctors
document.querySelector('#registerForm select[name="role"]').addEventListener("change", (e) => {
  document.getElementById("specializationField").hidden = e.target.value !== "DOCTOR";
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const errEl = document.getElementById("loginError");
  errEl.hidden = true;
  const fd = new FormData(e.target);
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: { email: fd.get("email"), password: fd.get("password") },
    });
    setSession({ token: data.token, role: data.role, name: data.name || data.fullName || fd.get("email") });
    toast("Welcome back", "success");
    e.target.reset();
    navigate("dashboard");
  } catch (err) {
    errEl.textContent = err.message;
    errEl.hidden = false;
  }
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const errEl = document.getElementById("registerError");
  errEl.hidden = true;
  const fd = new FormData(e.target);
  const body = {
    name: fd.get("name"),
    email: fd.get("email"),
    password: fd.get("password"),
    role: fd.get("role"),
  };
  if (body.role === "DOCTOR") body.specialization = fd.get("specialization");
  try {
    await api("/api/auth/register", { method: "POST", body });
    toast("Account created — log in to continue", "success");
    e.target.reset();
    document.getElementById("specializationField").hidden = true;
    navigate("login");
  } catch (err) {
    errEl.textContent = err.message;
    errEl.hidden = false;
  }
});

/* ---------------- Doctors ---------------- */
function formatTimeLabel(value) {
  if (!value) return "";
  const text = String(value);
  if (text.includes(":")) {
    const [hours, minutes] = text.split(":");
    return `${hours.padStart(2, "0")}:${minutes.padStart(2, "0")}`;
  }
  return text;
}

async function getDoctorNextAvailableSlot(doctorId) {
  if (!doctorId) return null;

  const dates = [];
  const today = new Date();
  for (let offset = 0; offset < 3; offset += 1) {
    const date = new Date(today);
    date.setDate(today.getDate() + offset);
    dates.push(date.toISOString().slice(0, 10));
  }

  for (let offset = 0; offset < dates.length; offset += 1) {
    const date = dates[offset];
    try {
      const slots = await api(`/api/doctors/${doctorId}/slots?date=${date}`);
      const firstOpen = (slots || []).find((slot) => slot.available !== false && slot.available !== "false");
      if (firstOpen) {
        const timeLabel = formatTimeLabel(firstOpen.startTime || firstOpen.time || firstOpen.start);
        const dayLabel = offset === 0 ? "Today" : offset === 1 ? "Tomorrow" : new Date(`${date}T00:00:00`).toLocaleDateString("en", { month: "short", day: "numeric" });
        return { dayLabel, timeLabel };
      }
    } catch {
      // Fall back to the static demo text if the slot lookup fails.
    }
  }

  return null;
}

async function loadDoctors(spec) {
  const grid = document.getElementById("doctorGrid");
  grid.innerHTML = `<p class="empty-state">Loading doctors…</p>`;
  let doctors = [];
  try {
    const path = spec ? `/api/doctors?specialization=${encodeURIComponent(spec)}` : "/api/doctors";
    doctors = await api(path);
  } catch {
    doctors = [];
  }

  if (!doctors || doctors.length === 0) {
    doctors = getFallbackDoctors(spec);
  } else if (spec) {
    const term = spec.trim().toLowerCase();
    doctors = doctors.filter((doc) => {
      const name = (doc.name || doc.user?.name || doc.fullName || "").toLowerCase();
      const specialization = (doc.specialization || "").toLowerCase();
      return name.includes(term) || specialization.includes(term);
    });
  }

  if (!doctors || doctors.length === 0) {
    grid.innerHTML = `<p class="empty-state">No doctors match that specialization.</p>`;
    return;
  }

  grid.innerHTML = "";
  doctors.forEach((doc) => {
    const card = document.createElement("div");
    card.className = "doctor-card";
    const name = doc.name || doc.user?.name || doc.fullName || "Doctor";
    const displayName = name.startsWith("Dr.") ? name : `Dr. ${name}`;
    const bioText = doc.bio || doc.description || doc.summary || "Tap to see open slots and book a visit.";
    const fallbackSlotText = doc.nextSlot || doc.availableSlot || "Available soon";
    card.innerHTML = `
      <h3>${escapeHtml(displayName)}</h3>
      <span class="doctor-spec">${escapeHtml(doc.specialization || "General")}</span>
      <p>${escapeHtml(bioText)}</p>
      <p class="doctor-slot">${escapeHtml(fallbackSlotText)}</p>
    `;

    const slotEl = card.querySelector(".doctor-slot");
    if (doc.id) {
      getDoctorNextAvailableSlot(doc.id).then((slotInfo) => {
        if (slotInfo) {
          slotEl.textContent = `Next available: ${slotInfo.dayLabel} · ${slotInfo.timeLabel}`;
        } else {
          slotEl.textContent = fallbackSlotText;
        }
      });
    }

    card.addEventListener("click", () => openBooking(doc));
    grid.appendChild(card);
  });
}

document.getElementById("specFilterBtn").addEventListener("click", () => {
  loadDoctors(document.getElementById("specFilter").value.trim());
});
document.getElementById("specFilterClear").addEventListener("click", () => {
  document.getElementById("specFilter").value = "";
  loadDoctors();
});
document.getElementById("specFilter").addEventListener("keydown", (e) => {
  if (e.key === "Enter") { e.preventDefault(); document.getElementById("specFilterBtn").click(); }
});

/* ---------------- Booking ---------------- */
function openBooking(doc) {
  if (!state.token) {
    toast("Log in as a patient to book an appointment");
    navigate("login");
    return;
  }
  if (state.role !== "PATIENT") {
    toast("Only patient accounts can book appointments");
    return;
  }
  state.selectedDoctor = doc;
  state.selectedSlot = null;
  document.getElementById("bookingDoctorName").textContent = `Dr. ${doc.name || doc.fullName || ""}`;
  document.getElementById("bookingSpec").textContent = doc.specialization || "General";
  document.getElementById("slotDate").value = "";
  document.getElementById("visitReason").value = "";
  document.getElementById("slotGrid").innerHTML = `<p class="empty-state">Pick a date to see open slots.</p>`;
  document.getElementById("bookingError").hidden = true;
  document.getElementById("bookingSuccess").hidden = true;
  loadAvailabilityPreview();
  navigate("booking");
}

document.getElementById("slotDate").addEventListener("change", async (e) => {
  const date = e.target.value;
  const grid = document.getElementById("slotGrid");
  state.selectedSlot = null;
  if (!date || !state.selectedDoctor) return;
  grid.innerHTML = `<p class="empty-state">Loading slots…</p>`;
  try {
    const slots = await api(`/api/doctors/${state.selectedDoctor.id}/slots?date=${date}`);
    if (!slots || slots.length === 0) {
      grid.innerHTML = `<p class="empty-state">No open slots on this date — try another day.</p>`;
      return;
    }
    grid.innerHTML = "";
    slots.forEach((slot) => {
      const time = typeof slot === "string" ? slot : slot.time || slot.startTime;
      const available = typeof slot === "object" && "available" in slot ? slot.available : true;
      const b = document.createElement("button");
      b.type = "button";
      b.className = "slot-btn";
      b.textContent = time;
      b.disabled = !available;
      b.addEventListener("click", () => {
        document.querySelectorAll(".slot-btn.selected").forEach((s) => s.classList.remove("selected"));
        b.classList.add("selected");
        state.selectedSlot = time;
      });
      grid.appendChild(b);
    });
  } catch (err) {
    grid.innerHTML = `<p class="empty-state">Couldn't load slots — ${escapeHtml(err.message)}</p>`;
  }
});

document.getElementById("bookingPanel");
document.querySelector(".booking-panel")?.addEventListener("click", () => {});

document.getElementById("view-booking").addEventListener("submit", (e) => e.preventDefault());

// Book button lives inside booking panel — add it dynamically via a submit-style button
(function initBookButton() {
  const panel = document.querySelector(".booking-panel");
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "btn btn-primary btn-block";
  btn.textContent = "Book this slot";
  btn.style.marginTop = "4px";
  btn.addEventListener("click", confirmBooking);
  panel.appendChild(btn);
})();

async function confirmBooking() {
  const errEl = document.getElementById("bookingError");
  const okEl = document.getElementById("bookingSuccess");
  errEl.hidden = true;
  okEl.hidden = true;

  const date = document.getElementById("slotDate").value;
  const reason = document.getElementById("visitReason").value.trim();

  if (!date) { errEl.textContent = "Pick a date first."; errEl.hidden = false; return; }
  if (!state.selectedSlot) { errEl.textContent = "Pick an open slot first."; errEl.hidden = false; return; }

  try {
    await api("/api/appointments", {
      method: "POST",
      auth: true,
      body: {
        doctorId: state.selectedDoctor.id,
        appointmentDate: date,
        startTime: state.selectedSlot,
        reason: reason || undefined,
      },
    });
    okEl.textContent = "Appointment requested — you'll find it under My chart.";
    okEl.hidden = false;
    toast("Appointment booked", "success");
    setTimeout(() => navigate("dashboard"), 900);
  } catch (err) {
    errEl.textContent = err.message;
    errEl.hidden = false;
  }
}

/* ---------------- Dashboard ---------------- */
async function loadDashboard() {
  document.getElementById("dashPatient").hidden = true;
  document.getElementById("dashDoctor").hidden = true;
  document.getElementById("dashAdmin").hidden = true;

  if (state.role === "PATIENT") {
    document.getElementById("dashEyebrow").textContent = "My chart";
    document.getElementById("dashTitle").textContent = "Your appointments";
    document.getElementById("dashPatient").hidden = false;
    loadPatientAppointments();
  } else if (state.role === "DOCTOR") {
    document.getElementById("dashEyebrow").textContent = "Your schedule";
    document.getElementById("dashTitle").textContent = "Appointments on your calendar";
    document.getElementById("dashDoctor").hidden = false;
    loadDoctorAppointments();
  } else if (state.role === "ADMIN") {
    document.getElementById("dashEyebrow").textContent = "Admin";
    document.getElementById("dashTitle").textContent = "Practice overview";
    document.getElementById("dashAdmin").hidden = false;
    loadAdminData();
  }
}

function statusPill(status) {
  return `<span class="status-pill status-${status}">${status}</span>`;
}

async function loadAvailabilityPreview() {
  try {
    const doctorId = state.selectedDoctor?.id;
    if (!doctorId) return;
    const slots = await api(`/api/doctors/${doctorId}/availability`);
    const preview = document.getElementById("bookingSpec");
    if (preview) preview.textContent = `${state.selectedDoctor.specialization || "General"} • demo availability`;
    if (slots && slots.length) {
      const grid = document.getElementById("slotGrid");
      if (grid) {
        grid.innerHTML = `<p class="empty-state">Demo availability: ${slots.map((s) => `${s.day} ${s.startTime}-${s.endTime}`).join(" • ")}</p>`;
      }
    }
  } catch {
    // ignore preview failures
  }
}

async function loadPatientAppointments() {
  const tbody = document.querySelector("#patientTable tbody");
  const emptyEl = document.getElementById("patientEmpty");
  tbody.innerHTML = "";
  try {
    const list = await api("/api/appointments/my", { auth: true });
    if (!list || list.length === 0) { emptyEl.hidden = false; return; }
    emptyEl.hidden = true;
    list.forEach((a) => {
      const tr = document.createElement("tr");
      const cancellable = a.status === "PENDING" || a.status === "CONFIRMED";
      tr.innerHTML = `
        <td>Dr. ${escapeHtml(a.doctorName || "")}</td>
        <td>${escapeHtml(a.specialization || "")}</td>
        <td>${escapeHtml(a.appointmentDate || "")}</td>
        <td>${escapeHtml(a.startTime || "")}</td>
        <td>${statusPill(a.status)}</td>
        <td>${cancellable ? `<button class="btn btn-danger btn-sm" data-cancel="${a.id}">Cancel</button>` : ""}</td>
      `;
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll("[data-cancel]").forEach((btn) => {
      btn.addEventListener("click", () => cancelAppointment(btn.dataset.cancel));
    });
  } catch (err) {
    emptyEl.textContent = "Couldn't load your appointments — " + err.message;
    emptyEl.hidden = false;
  }
}

async function cancelAppointment(id) {
  try {
    await api(`/api/appointments/${id}/cancel`, { method: "PUT", auth: true });
    toast("Appointment cancelled", "success");
    loadPatientAppointments();
  } catch (err) {
    toast(err.message, "error");
  }
}

async function loadDoctorAppointments() {
  const tbody = document.querySelector("#doctorTable tbody");
  const emptyEl = document.getElementById("doctorEmpty");
  tbody.innerHTML = "";
  try {
    const list = await api("/api/appointments/doctor", { auth: true });
    if (!list || list.length === 0) { emptyEl.hidden = false; return; }
    emptyEl.hidden = true;
    const options = ["PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"];
    list.forEach((a) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${escapeHtml(a.patientName || "")}</td>
        <td>${escapeHtml(a.appointmentDate || "")}</td>
        <td>${escapeHtml(a.startTime || "")}</td>
        <td>${escapeHtml(a.reason || "—")}</td>
        <td>${statusPill(a.status)}</td>
        <td>
          <select class="status-select" data-status-for="${a.id}">
            ${options.map((o) => `<option value="${o}" ${o === a.status ? "selected" : ""}>${o}</option>`).join("")}
          </select>
        </td>
      `;
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll("[data-status-for]").forEach((sel) => {
      sel.addEventListener("change", () => updateAppointmentStatus(sel.dataset.statusFor, sel.value));
    });
  } catch (err) {
    emptyEl.textContent = "Couldn't load your schedule — " + err.message;
    emptyEl.hidden = false;
  }
}

async function updateAppointmentStatus(id, status) {
  try {
    await api(`/api/appointments/${id}/status`, { method: "PUT", auth: true, body: { status } });
    toast("Status updated", "success");
  } catch (err) {
    toast(err.message, "error");
    loadDoctorAppointments();
  }
}

async function loadAdminData() {
  const statRow = document.getElementById("statRow");
  const tbody = document.querySelector("#adminTable tbody");
  statRow.innerHTML = "";
  tbody.innerHTML = "";
  try {
    const stats = await api("/api/admin/dashboard", { auth: true });
    [
      ["Doctors", stats.totalDoctors],
      ["Patients", stats.totalPatients],
      ["Appointments", stats.totalAppointments],
      ["Pending", stats.pendingAppointments],
      ["Confirmed", stats.confirmedAppointments],
    ].forEach(([label, value]) => {
      const card = document.createElement("div");
      card.className = "stat-card";
      card.innerHTML = `<div class="stat-num">${escapeHtml(String(value))}</div><div class="stat-label">${escapeHtml(label)}</div>`;
      statRow.appendChild(card);
    });

    const reminderPanel = document.getElementById("reminderPanel");
    const reminders = await api("/api/appointments/reminders");
    reminderPanel.innerHTML = `<h3>Reminder summary</h3><p>${escapeHtml(reminders.message || "No reminders")}</p><p><strong>Upcoming:</strong> ${escapeHtml(String(reminders.upcomingCount || 0))}</p><p><strong>Next:</strong> ${escapeHtml(reminders.nextAppointment || "No appointments")}</p>`;
  } catch (err) {
    statRow.innerHTML = `<p class="empty-state">Couldn't load stats — ${escapeHtml(err.message)}</p>`;
  }
  try {
    const users = await api("/api/admin/users", { auth: true });
    (users || []).forEach((u) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${escapeHtml(u.name || u.fullName || "")}</td><td>${escapeHtml(u.email || "")}</td><td>${escapeHtml(u.role || "")}</td>`;
      tbody.appendChild(tr);
    });
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="3">Couldn't load users — ${escapeHtml(err.message)}</td></tr>`;
  }
}

/* ---------------- API status ping ---------------- */
async function pingApi() {
  const el = document.getElementById("apiStatus");
  try {
    await fetch(`${API_BASE}/api/doctors`, { method: "GET" });
    el.textContent = `Connected — ${API_BASE}`;
    el.className = "api-status ok";
  } catch {
    el.textContent = `Backend not reachable at ${API_BASE}`;
    el.className = "api-status down";
  }
}

/* ---------------- Utils ---------------- */
function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
