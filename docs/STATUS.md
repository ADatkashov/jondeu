# Implementation status

Legend: ✅ done & tested · 🟡 partial · ⛔ not done.

## Verified in this environment
* Backend: `mvn test` — 26 tests green (real PostgreSQL 16 via embedded Zonky, Flyway migration `V1`).
* Backend run for real: Spring Boot jar against PostgreSQL 16 with demo data; health/readiness UP.
* Web: `tsc`, `vitest` (20 tests), `next build` green; browser run (Playwright) of login → dashboard → realtime update with no console errors.
* Mobile: `flutter analyze` clean, `flutter test` (14 tests) green.
* **Not verified here:** Docker images/compose (no Docker daemon in the sandbox), Android/iOS builds (no Android SDK / Xcode), push to FCM/APNs, S3/R2 storage, real Kaspi/acquiring.

## Backend
| Area | Status | Notes |
|---|---|---|
| Auth: register/login, JWT, refresh rotation + reuse detection, logout(-all), OTP request/login, rate limit | ✅ | SMS gateway is a logging stub (`SmsSender`); password recovery ⛔ |
| RBAC (8 roles) + project-level permissions | ✅ | `ProjectRole` matrix; admin routes by role; every service call goes through `ProjectAccess` |
| Projects/requests lifecycle, proposals (compare/accept/reject), members, archive, complete | ✅ | statuses as specified |
| Estimates: versions, immutability once approved, submit/approve/reject/request-changes/revise, PDF, history | ✅ | totals only server-side; PDF with embedded Cyrillic+₸ font |
| Stages: 11 defaults, weights, dependencies (cycle check), review/accept flow, comments, weighted progress | ✅ | stage photos by `stageId`; per-stage assignees (field only) 🟡 |
| Materials: planned vs actual, order/receive/cancel | ✅ | catalog / supplier entities / marketplace ⛔ (fields only) |
| Payments: ledger, idempotency, reservation, lock, manual confirm, refund, signed sandbox callback | ✅ | **Kaspi / real acquiring NOT connected** (503), see `docs/PAYMENTS.md` |
| Chat: project & direct conversations, attachments, unread, read receipts, WS push | ✅ | "delivered" state not persisted (SENT/READ only) |
| Media: validated upload, signed URLs, categories, filters | ✅ | local storage only; S3/R2 implementation ⛔ (interface ready) |
| Inspections, calendar (derived read-model + events), notifications (in-app + WS), reviews, masters directory, admin, audit log | ✅ | notification *preferences* ⛔; external push/email channel = extension point only |
| Scheduled jobs: delayed stages, payment reminders | ✅ | single-node (add ShedLock for multi-node) |
| Master verification workflow | 🟡 | admin toggle + audit; document review flow ⛔ |
| Master certifications, availability calendar, company teams/permissions beyond project members | ⛔ | |
| Structured (JSON) logging | ⛔ | plain logs |

## Web dashboard (all pages call the real API)
✅ Overview (7 KPIs, stages, calendar, budget donut, materials, team, photos, activity, quality), projects + 5-step wizard + proposal comparison, estimates (editor, approval, PDF, history), stages (Gantt-style timeline), materials, calendar (month/week, create/delete), photos (filters, upload, lightbox), quality, messages, documents, payments, masters directory + detail, requests (pros), reports, settings, admin. kk/ru/en. Realtime indicator + toasts.
🟡 "Project Plan" is merged into Stages; reports have no export except print; component tests are basic; no Playwright suite committed (browser check was run ad-hoc).

## Mobile (Flutter, one app, role-aware)
✅ Auth, realtime sync, home overview, masters directory (search/filter/sort/detail/contact), request wizard (5 steps), proposals compare/accept, estimate view/approve, stages (progress/review/accept), payments (pay with idempotency, confirm, request), materials, photos (upload), chat, notifications, profile/language.
⛔ Push notifications (FCM), onboarding carousel + city picker, OTP screens, offline cache (only provider memory), contractor-side estimate editor, calendar and inspections screens, floor-plan/document upload, Android/iOS release builds.

## Next executable tasks
1. Docker smoke test in CI (compose up + `/actuator/health` + login).
2. Mobile: estimate editor for contractors, calendar + inspections screens, FCM push, offline cache (Drift/Isar), onboarding.
3. `S3FileStorage` (AWS SDK v2, R2-compatible endpoint, presigned URLs).
4. Real payment provider behind `PaymentProvider` + recorded-payload tests.
5. Notification preferences, password recovery, SMS gateway, JSON logging, ShedLock.
6. Playwright E2E suite for the web dashboard.
