# 1. System Overview

## Runtime services

- `api-gateway`: single browser entrypoint on port `8090`; validates JWTs, applies RBAC, injects `X-User-Id`, `X-User-Role`, and `X-Auth-Source: gateway` before forwarding.
- `auth-service`: registration, login, refresh-token rotation, logout, forgot-password, reset-password, admin seeding.
- `user-service`: user profile read/update store; internal sync target for `auth-service`.
- `startup-service`: startup CRUD, founder-owned startup listing, discovery/search.
- `investment-service`: investor investment creation, founder approval/rejection/completion, startup deletion reaction, payment result reaction.
- `team-service`: invitations, join team, remove member, member history, active roles, startup deletion reaction.
- `messaging-service`: direct messages and conversation history.
- `notification-service`: in-app notifications and email dispatch for async events.
- `payment-service`: creates payment records from approved investments, Razorpay order creation/confirmation, wallet credit, payment result publishing, DLQ handling.
- `wallet-service`: startup wallet creation, deposit, balance lookup.
- `config-server`: Spring Cloud Config Server on port `8888`.
- `eureka-server` / application name `service-registry`: Eureka service discovery on port `8761`.

## Architecture

- Client -> `api-gateway` -> downstream services.
- Every business service imports configuration from `config-server` and registers with Eureka.
- Each business service has its own MySQL database.
- Redis is used for caching in most business services.
- RabbitMQ is the async backbone for cross-service events.
- Zipkin, Prometheus, Loki, Promtail, and Grafana are wired for tracing, metrics, and logs.

## Communication patterns

- Synchronous REST:
  - Gateway to all downstream services.
  - `auth-service` -> `user-service` (`POST /users/internal`).
  - `investment-service` -> `startup-service`.
  - `team-service` -> `startup-service`.
  - `messaging-service` -> `user-service`.
  - `notification-service` -> `user-service`, `startup-service`.
  - `payment-service` -> `wallet-service`.
- Asynchronous RabbitMQ:
  - `auth-service` publishes `user.registered`, `password.reset`.
  - `startup-service` publishes `startup.created`, `startup.deleted`.
  - `investment-service` publishes `investment.created`, `investment.approved`, `investment.rejected`; consumes `payment.completed`, `payment.failed`, `startup.deleted`.
  - `team-service` publishes `team.invite.sent`, `team.member.accepted`, `team.member.rejected`; consumes `startup.deleted`.
  - `messaging-service` publishes `message.sent`.
  - `payment-service` consumes `investment.approved`, `investment.rejected`; publishes `payment.completed`, `payment.failed`.
  - `notification-service` consumes most domain events and creates notifications/emails.

## Important repo/runtime note

- The checked-in `config-repo/` contains service config files used for this analysis.
- `config-server` is actually configured to fetch from `https://github.com/aditya-7562/FounderLink_config`.
- The remote Git config exactly matches the checked-in local `config-repo/`.

---

# 2. Service Breakdown

## Service Name: api-gateway

### Purpose

- Central entrypoint for browser/API clients.

### Key Responsibilities

- Service routing.
- JWT validation.
- Route-level RBAC.
- CORS.
- Injecting trusted auth headers for downstream services.

### Exposed APIs

- No business controller endpoints in code.
- Routed paths:
  - `/auth/**` -> `auth-service`
  - `/users/**` -> `user-service`
  - `/startup/**` -> `startup-service`
  - `/investments/**` -> `investment-service`
  - `/teams/**` -> `team-service`
  - `/messages/**` -> `messaging-service`
  - `/notifications/**` -> `notification-service`
  - `/payments/**` -> `payment-service`
  - `/wallets/**` -> `wallet-service`
- Swagger doc pass-through routes exist for each service and rewrite `/service-prefix/v3/api-docs/**` to downstream `/v3/api-docs/**`.

### Internal Dependencies

- Eureka service discovery.
- Shared JWT secret from config.

---

## Service Name: auth-service

### Purpose

- Owns authentication credentials, access tokens, refresh tokens, password reset, and initial user creation.

### Key Responsibilities

- Register user in auth DB.
- Sync user identity into `user-service`.
- Issue access JWT and refresh token.
- Rotate/revoke refresh tokens.
- Generate and validate password reset PINs.
- Publish welcome/password reset events.

### Exposed APIs

#### `POST /auth/register`

- Description: register a new self-service user.
- Request body:
```json
{
  "name": "string",
  "email": "string",
  "password": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN"
}
```
- Response body:
```json
{
  "email": "string",
  "role": "string",
  "message": "User registered successfully"
}
```
- Status codes: `200`, `400`, `403`, `409`, `503`.
- Validation rules:
  - `name` required.
  - `email` required, valid email.
  - `password` required.
  - `role` required.
  - `ADMIN` is explicitly rejected for self-registration.
  - Allowed self-service roles: `FOUNDER`, `INVESTOR`, `COFOUNDER`.

#### `POST /auth/login`

- Description: authenticate and return access token; also sets refresh-token cookie.
- Request body:
```json
{
  "email": "string",
  "password": "string"
}
```
- Response body:
```json
{
  "token": "jwt",
  "email": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN",
  "userId": 1
}
```
- Status codes: `200`, `400`, `401`.
- Validation rules:
  - `email` required, valid email.
  - `password` required.
- Extra behavior:
  - Sets `Set-Cookie` for refresh token.
  - Cookie is `HttpOnly`, `Secure=true`, `SameSite=Strict`, path `/auth`, max-age `30d`.

#### `POST /auth/refresh`

- Description: rotate refresh token and issue fresh access token.
- Request body: none.
- Request auth source:
  - refresh token from cookie `refresh_token`, or
  - `Authorization: Bearer <refreshToken>`, or
  - raw `Authorization: <refreshToken>`.
- Response body:
```json
{
  "token": "jwt",
  "email": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN",
  "userId": 1
}
```
- Status codes: `200`, `401`, `403`.
- Validation rules:
  - refresh token must exist.
  - token must not be revoked or expired.

#### `POST /auth/logout`

- Description: revoke refresh token if present and clear refresh cookie.
- Request body: none.
- Response body: none.
- Status codes: `204`.
- Validation rules:
  - invalid/missing refresh token is swallowed; logout still returns `204`.

#### `POST /auth/forgot-password`

- Description: create 6-digit PIN and publish password reset email event.
- Request body:
```json
{
  "email": "string"
}
```
- Response body:
```json
{
  "message": "Password reset PIN has been sent to your email"
}
```
- Status codes: `200`, `400`, `401`.
- Validation rules:
  - `email` required, valid email.
  - email must already exist in auth DB.

#### `POST /auth/reset-password`

- Description: verify PIN and replace password.
- Request body:
```json
{
  "email": "string",
  "pin": "123456",
  "newPassword": "string"
}
```
- Response body:
```json
{
  "message": "Password has been reset successfully"
}
```
- Status codes: `200`, `400`, `401`.
- Validation rules:
  - `email` required, valid email.
  - `pin` required, exactly 6 digits.
  - `newPassword` required, minimum length `8`.
  - PIN must exist, be unused, and expire within `5 minutes`.

### Internal Dependencies

- `user-service` internal sync endpoint `/users/internal`.
- RabbitMQ for `user.registered` and `password.reset`.

---

## Service Name: user-service

### Purpose

- Stores user profile data used by the rest of the platform.

### Key Responsibilities

- Create synced profile records from auth-service.
- Read/update profile data.
- Filter users by role.

### Exposed APIs

#### `POST /users/internal`

- Description: internal auth-service sync endpoint.
- Required headers:
  - `X-Auth-Source: gateway`
  - `X-Internal-Secret: <INTERNAL_SECRET>`
- Request body:
```json
{
  "userId": 1,
  "name": "string",
  "email": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN",
  "skills": "string | null",
  "experience": "string | null",
  "bio": "string | null",
  "portfolioLinks": "string | null"
}
```
- Response body:
```json
{
  "userId": 1,
  "name": "string",
  "email": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN",
  "skills": "string | null",
  "experience": "string | null",
  "bio": "string | null",
  "portfolioLinks": "string | null"
}
```
- Status codes: `200`, `400`, `403`, `409`.
- Validation rules:
  - `userId` required.
  - `email` required and valid.
  - idempotent if same `userId` already exists with same `email` and `role`.
  - conflict if existing identity data differs.

#### `GET /users/{id}`

- Description: get user by id.
- Request body: none.
- Response body:
```json
{
  "userId": 1,
  "name": "string | null",
  "email": "string",
  "role": "FOUNDER | INVESTOR | COFOUNDER | ADMIN",
  "skills": "string | null",
  "experience": "string | null",
  "bio": "string | null",
  "portfolioLinks": "string | null"
}
```
- Status codes: `200`, `404`.

#### `PUT /users/{id}`

- Description: partial profile update.
- Request body:
```json
{
  "name": "string | null",
  "skills": "string | null",
  "experience": "string | null",
  "bio": "string | null",
  "portfolioLinks": "string | null"
}
```
- Response body: same as `GET /users/{id}`.
- Status codes: `200`, `404`, `409`.
- Validation rules:
  - no field-level Bean Validation annotations.
  - only non-null fields are applied.

#### `GET /users`

- Description: get all users.
- Request body: none.
- Response body: array of `UserResponseDto`.
- Status codes: `200`.

#### `GET /users/role/{role}`

- Description: get users by role.
- Request body: none.
- Response body: array of `UserResponseDto`.
- Status codes:
  - `200` for valid non-admin roles.
  - `400` with empty list for invalid role string.
  - `403` with empty list for `ADMIN`.
- Validation rules:
  - role path is uppercased and strips `ROLE_` prefix before enum parsing.

### Internal Dependencies

- Called by `auth-service`, `messaging-service`, `notification-service`.

---

## Service Name: startup-service

### Purpose

- Founder-owned startup lifecycle and investor discovery.

### Key Responsibilities

- Create/update/delete startup records.
- Search active startups.
- Publish startup created/deleted events.
- Soft delete with `isDeleted=true`.

### Exposed APIs

#### `POST /startup`

- Description: founder creates startup.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Request body:
```json
{
  "name": "string",
  "description": "string",
  "industry": "string",
  "problemStatement": "string",
  "solution": "string",
  "fundingGoal": 1000.00,
  "stage": "IDEA | MVP | EARLY_TRACTION | SCALING"
}
```
- Response body:
```json
{
  "message": "Startup created successfully",
  "data": {
    "id": 1,
    "name": "string",
    "description": "string",
    "industry": "string",
    "problemStatement": "string",
    "solution": "string",
    "fundingGoal": 1000.00,
    "stage": "IDEA | MVP | EARLY_TRACTION | SCALING",
    "founderId": 1,
    "createdAt": "<LocalDateTime>"
  }
}
```
- Status codes: `201`, `400`, `403`.
- Validation rules:
  - all fields required.
  - `fundingGoal >= 1000.00`.

#### `GET /startup`

- Description: get all active startups.
- Required headers: `X-User-Role` in `ROLE_INVESTOR | ROLE_FOUNDER | ROLE_COFOUNDER | ROLE_ADMIN`.
- Response body:
```json
{
  "message": "Startups fetched successfully",
  "data": [ { "id": 1, "name": "string", "description": "string", "industry": "string", "problemStatement": "string", "solution": "string", "fundingGoal": 1000.00, "stage": "IDEA", "founderId": 1, "createdAt": "<LocalDateTime>" } ]
}
```
- Status codes: `200`, `403`.

#### `GET /startup/{id}`

- Description: internal/plain startup lookup used by Feign clients.
- Request body: none.
- Response body: plain `StartupResponseDto` without wrapper.
- Status codes: `200`, `404`.

#### `GET /startup/details/{id}`

- Description: wrapped startup details endpoint for frontend-style use.
- Response body:
```json
{
  "message": "Startup fetched successfully",
  "data": {
    "id": 1,
    "name": "string",
    "description": "string",
    "industry": "string",
    "problemStatement": "string",
    "solution": "string",
    "fundingGoal": 1000.00,
    "stage": "IDEA",
    "founderId": 1,
    "createdAt": "<LocalDateTime>"
  }
}
```
- Status codes: `200`, `404`.

#### `GET /startup/founder`

- Description: founder gets own startups.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Response body: wrapper with array of `StartupResponseDto`.
- Status codes: `200`, `403`.

#### `PUT /startup/{id}`

- Description: founder updates own startup.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Request body: same as create.
- Response body: wrapper with updated `StartupResponseDto`.
- Status codes: `200`, `400`, `403`, `404`.
- Validation rules:
  - same Bean Validation as create.
  - `founderId` must match startup owner.

#### `DELETE /startup/{id}`

- Description: founder soft deletes own startup.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Response body:
```json
{
  "message": "Startup deleted successfully",
  "data": null
}
```
- Status codes: `200`, `403`, `404`.
- Validation rules:
  - owner check required.

#### `GET /startup/search`

- Description: filtered active startup search.
- Required headers: `X-User-Role` in `ROLE_INVESTOR | ROLE_FOUNDER | ROLE_COFOUNDER | ROLE_ADMIN`.
- Query params:
  - `industry` optional `string`
  - `stage` optional `IDEA | MVP | EARLY_TRACTION | SCALING`
  - `minFunding` optional number
  - `maxFunding` optional number
- Response body: wrapper with array of `StartupResponseDto`.
- Status codes: `200`, `400`, `403`.
- Validation rules:
  - negative funding values rejected.
  - `minFunding` cannot exceed `maxFunding`.
  - invalid enum value yields explicit accepted-stage message.
  - search is exact-match for `industry` and/or `stage`; no sort/pagination.

### Internal Dependencies

- RabbitMQ publish: `startup.created`, `startup.deleted`.
- Used synchronously by `investment-service`, `team-service`, `notification-service`, `payment-service`.

---

## Service Name: investment-service

### Purpose

- Investor interest pipeline between startup discovery and payment.

### Key Responsibilities

- Create investments.
- Let founder/admin review startup investments.
- Approve/reject/manual-complete investments.
- React to startup deletion and payment results.

### Exposed APIs

#### `POST /investments`

- Description: investor creates investment request.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_INVESTOR`.
- Request body:
```json
{
  "startupId": 1,
  "amount": 1000.00
}
```
- Response body:
```json
{
  "message": "Investment created successfully",
  "data": {
    "id": 1,
    "startupId": 1,
    "investorId": 2,
    "amount": 1000.00,
    "status": "PENDING",
    "createdAt": "<LocalDateTime>"
  }
}
```
- Status codes: `201`, `400`, `403`, `404`, `409`, `503`, `502`.
- Validation rules:
  - `startupId` required.
  - `amount >= 1000.00`.
  - duplicate check only blocks another `PENDING` investment for same `(startupId, investorId)`.

#### `GET /investments/startup/{startupId}`

- Description: founder/admin sees investments for owned startup.
- Required headers: `X-User-Id`, `X-User-Role` in `ROLE_FOUNDER | ROLE_ADMIN`.
- Response body: wrapper with array of `InvestmentResponseDto`.
- Status codes: `200`, `403`, `404`, `503`, `502`.
- Validation rules:
  - if caller is founder, startup ownership is verified via startup-service.

#### `GET /investments/investor`

- Description: investor/admin gets portfolio for current investor id.
- Required headers: `X-User-Id`, `X-User-Role` in `ROLE_INVESTOR | ROLE_ADMIN`.
- Response body: wrapper with array of `InvestmentResponseDto`.
- Status codes: `200`, `403`.

#### `PUT /investments/{id}/status`

- Description: founder/admin updates investment status.
- Required headers: `X-User-Id`, `X-User-Role` in `ROLE_FOUNDER | ROLE_ADMIN`.
- Request body:
```json
{
  "status": "APPROVED | REJECTED | COMPLETED"
}
```
- Response body: wrapper with updated `InvestmentResponseDto`.
- Status codes: `200`, `400`, `403`, `404`, `503`, `502`.
- Validation rules:
  - status required.
  - founder ownership of startup verified.
  - invalid transitions:
    - cannot update from `COMPLETED`
    - cannot update from `REJECTED`
    - cannot update from `STARTUP_CLOSED`
    - cannot set `COMPLETED` unless current status is `APPROVED`
- Side effects:
  - `APPROVED` publishes `investment.approved`.
  - `REJECTED` publishes `investment.rejected`.

#### `GET /investments/{id}`

- Description: get single investment.
- Required headers: `X-User-Role` in `ROLE_FOUNDER | ROLE_INVESTOR | ROLE_ADMIN`.
- Response body: wrapper with single `InvestmentResponseDto`.
- Status codes: `200`, `403`, `404`.

### Internal Dependencies

- `startup-service` Feign client.
- RabbitMQ:
  - publish `investment.created`, `investment.approved`, `investment.rejected`
  - consume `payment.completed`, `payment.failed`, `startup.deleted`

---

## Service Name: team-service

### Purpose

- Founder/cofounder team formation around a startup.

### Key Responsibilities

- Send/cancel/reject invitations.
- Accept invitation and create active team member.
- Remove members.
- Read team by startup, member history, active roles.
- Clean up on startup deletion.

### Exposed APIs

#### `POST /teams/invite`

- Description: founder invites user into startup role.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Request body:
```json
{
  "startupId": 1,
  "invitedUserId": 2,
  "role": "CTO | CPO | MARKETING_HEAD | ENGINEERING_LEAD"
}
```
- Response body:
```json
{
  "message": "Invitation sent successfully",
  "data": {
    "id": 1,
    "startupId": 1,
    "founderId": 1,
    "invitedUserId": 2,
    "role": "CTO",
    "status": "PENDING",
    "createdAt": "<LocalDateTime>",
    "updatedAt": null
  }
}
```
- Status codes: `201`, `400`, `403`, `404`, `409`, `503`, `502`.
- Validation rules:
  - all fields required.
  - founder must own startup.
  - founder cannot invite self.
  - only one pending invitation per `(startupId, invitedUserId)`.
  - only one pending invitation per `(startupId, role)`.

#### `PUT /teams/invitations/{id}/cancel`

- Description: founder cancels pending invitation.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Response body: wrapper with updated `InvitationResponseDto`.
- Status codes: `200`, `400`, `403`, `404`.
- Validation rules:
  - invitation must belong to founder.
  - only `PENDING` invitations can be cancelled.

#### `PUT /teams/invitations/{id}/reject`

- Description: invited cofounder rejects pending invitation.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_COFOUNDER`.
- Response body: wrapper with updated `InvitationResponseDto`.
- Status codes: `200`, `400`, `403`, `404`.
- Validation rules:
  - invitation must belong to caller.
  - only `PENDING` invitations can be rejected.

#### `GET /teams/invitations/user`

- Description: get invitations for current cofounder user id.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_COFOUNDER`.
- Response body: wrapper with array of `InvitationResponseDto`.
- Status codes: `200`, `403`.

#### `GET /teams/invitations/startup/{startupId}`

- Description: founder gets invitations for owned startup.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Response body: wrapper with array of `InvitationResponseDto`.
- Status codes: `200`, `403`, `404`, `503`, `502`.

#### `POST /teams/join`

- Description: invited cofounder accepts invitation and becomes active team member.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_COFOUNDER`.
- Request body:
```json
{
  "invitationId": 1
}
```
- Response body:
```json
{
  "message": "Successfully joined the team",
  "data": {
    "id": 1,
    "startupId": 1,
    "userId": 2,
    "role": "CTO",
    "isActive": true,
    "joinedAt": "<LocalDateTime>",
    "leftAt": null
  }
}
```
- Status codes: `201`, `400`, `403`, `409`.
- Validation rules:
  - `invitationId` required.
  - invitation must belong to caller.
  - invitation must still be `PENDING`.
  - active membership for same startup/user forbidden.
  - active member occupying same role forbidden.

#### `GET /teams/startup/{startupId}`

- Description: get active team members for startup.
- Required headers: `X-User-Id`, `X-User-Role`.
- Response body: wrapper with array of active `TeamMemberResponseDto`.
- Status codes: `200`, `403`, `404`, `503`, `502`.
- Validation rules:
  - founders must own startup.
  - cofounders must already be active team members of that startup.
  - investors/admins are allowed by controller.

#### `DELETE /teams/{teamMemberId}`

- Description: founder removes team member by soft-deactivating member record.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_FOUNDER`.
- Response body:
```json
{
  "message": "Team member removed successfully",
  "data": null
}
```
- Status codes: `200`, `403`, `404`, `503`, `502`.
- Validation rules:
  - founder must own startup tied to member.
  - founder cannot remove self.

#### `GET /teams/member/history`

- Description: get full member history for current user.
- Required headers: `X-User-Id`, `X-User-Role` in `ROLE_COFOUNDER | ROLE_ADMIN`.
- Response body: wrapper with array of `TeamMemberResponseDto`.
- Status codes: `200`, `403`.

#### `GET /teams/member/active`

- Description: get active roles for current user.
- Required headers: `X-User-Id`, `X-User-Role` in `ROLE_COFOUNDER | ROLE_ADMIN`.
- Response body: wrapper with array of `TeamMemberResponseDto`.
- Status codes: `200`, `403`.

### Internal Dependencies

- `startup-service` Feign client.
- RabbitMQ:
  - publish `team.invite.sent`, `team.member.accepted`, `team.member.rejected`
  - consume `startup.deleted`

---

## Service Name: messaging-service

### Purpose

- Stores direct messages between users.

### Key Responsibilities

- Send message.
- Fetch message by id.
- Fetch full conversation and unique conversation partners.
- Publish message event for notifications.

### Exposed APIs

#### `POST /messages`

- Description: send message.
- Request body:
```json
{
  "senderId": 1,
  "receiverId": 2,
  "content": "string"
}
```
- Response body:
```json
{
  "id": 1,
  "senderId": 1,
  "receiverId": 2,
  "content": "string",
  "createdAt": "<LocalDateTime>"
}
```
- Status codes: `201`, `400`, `503`.
- Validation rules:
  - `senderId` required.
  - `receiverId` required.
  - `content` required and non-blank.
  - sender and receiver cannot be same.
  - sender and receiver must exist in `user-service`.

#### `GET /messages/{id}`

- Description: get message by id.
- Response body: `MessageResponseDTO`.
- Status codes: `200`, `404`.

#### `GET /messages/conversation/{user1}/{user2}`

- Description: get full conversation ordered ascending by `createdAt`.
- Response body: array of `MessageResponseDTO`.
- Status codes: `200`.
- Edge case:
  - circuit-breaker fallback returns `[]` rather than error.

#### `GET /messages/partners/{userId}`

- Description: distinct ids that have messaged with user.
- Response body: array of `Long`.
- Status codes: `200`.
- Edge case:
  - circuit-breaker fallback returns `[]`.

### Internal Dependencies

- `user-service` Feign client for sender/receiver existence.
- RabbitMQ publish `message.sent`.

---

## Service Name: notification-service

### Purpose

- Stores in-app notifications and sends transactional emails.

### Key Responsibilities

- Create notification rows.
- Read notifications and unread notifications.
- Mark notifications as read.
- Consume domain events and fan out in-app/email side effects.

### Exposed APIs

#### `GET /notifications/{userId}`

- Description: get all notifications for user, newest first.
- Response body: array of `NotificationResponseDTO`.
- Status codes: `200`, `404` documented but controller/service do not check user existence.

#### `GET /notifications/{userId}/unread`

- Description: get unread notifications for user, newest first.
- Response body: array of `NotificationResponseDTO`.
- Status codes: `200`, `404` documented but controller/service do not check user existence.

#### `PUT /notifications/{id}/read`

- Description: mark notification as read.
- Response body:
```json
{
  "id": 1,
  "userId": 2,
  "type": "string",
  "message": "string",
  "read": true,
  "createdAt": "<LocalDateTime>"
}
```
- Status codes: `200`, `404`.

### Internal Dependencies

- `user-service` and `startup-service` Feign clients.
- RabbitMQ consumers for:
  - `startup.created`
  - `investment.created`
  - `team.invite.sent`
  - `message.sent`
  - `password.reset`
  - `team.member.accepted`
  - `team.member.rejected`
  - `payment.completed`
  - `payment.failed`
  - `investment.approved`
  - `investment.rejected`
  - `user.registered`
- SMTP email provider.

---

## Service Name: payment-service

### Purpose

- Converts approved investments into payable Razorpay orders, confirms payment, credits startup wallet, and publishes payment outcome.

### Key Responsibilities

- Create `Payment` row on `investment.approved`.
- Create or reuse Razorpay order.
- Verify Razorpay signature.
- Mark payment success/failure.
- Credit wallet synchronously and retry if needed.
- Handle investment rejection saga compensation.

### Exposed APIs

#### `POST /payments/create-order`

- Description: create or reuse Razorpay order for approved investment payment.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_INVESTOR`.
- Request body:
```json
{
  "investmentId": 1
}
```
- Response body:
```json
{
  "message": "Razorpay order created successfully",
  "data": {
    "orderId": "order_xxx",
    "amount": 1000.00,
    "currency": "INR",
    "investmentId": 1
  }
}
```
- Status codes: `201`, `400`, `403`, `404`, `502`.
- Validation rules:
  - `investmentId` required.
  - caller must own payment row for that investment.
  - payment row must already exist; it is created asynchronously by `InvestmentApprovedListener`.
  - if payment already `SUCCESS`, request fails.
  - if payment already `INITIATED` with existing Razorpay order, same order is returned.

#### `POST /payments/confirm`

- Description: verify successful Razorpay checkout and finalize payment.
- Required headers: `X-User-Id`, `X-User-Role=ROLE_INVESTOR`.
- Request body:
```json
{
  "razorpayOrderId": "order_xxx",
  "razorpayPaymentId": "pay_xxx",
  "razorpaySignature": "string"
}
```
- Response body:
```json
{
  "message": "Payment confirmed successfully",
  "data": {
    "status": "SUCCESS",
    "investmentId": 1
  }
}
```
- Status codes: `200`, `400`, `403`, `404`, `502`.
- Validation rules:
  - all three fields required.
  - caller must own payment row for the order id.
  - cannot confirm a `FAILED` payment.
- Side effects:
  - sets payment `SUCCESS`.
  - tries `wallet-service` create + deposit.
  - publishes `payment.completed`.
  - if wallet credit fails, leaves `walletCredited=false` for scheduled retry job.

#### `GET /payments/{paymentId}`

- Description: get payment by payment id.
- Required headers: `X-User-Id`.
- Response body:
```json
{
  "message": "Payment retrieved successfully",
  "data": {
    "id": 1,
    "investmentId": 1,
    "investorId": 2,
    "startupId": 3,
    "founderId": 4,
    "amount": 1000.00,
    "status": "PENDING | INITIATED | SUCCESS | FAILED",
    "externalPaymentId": null,
    "failureReason": "string | null",
    "createdAt": "<LocalDateTime>",
    "updatedAt": "<LocalDateTime>"
  }
}
```
- Status codes: `200`, `403`, `404`.
- Validation rules:
  - caller must own payment row.
- Important quirk:
  - `PaymentResponseDto.externalPaymentId` exists, but mapper source field is `razorpayPaymentId`; this is the value frontend should expect conceptually.

#### `GET /payments/investment/{investmentId}`

- Description: get payment by investment id.
- Required headers: `X-User-Id`.
- Response body: same wrapper as `GET /payments/{paymentId}`.
- Status codes: `200`, `403`, `404`.
- Validation rules:
  - caller must own payment row.

### Internal Dependencies

- Razorpay external API.
- `wallet-service` Feign client.
- RabbitMQ:
  - consumes `investment.approved`, `investment.rejected`
  - publishes `payment.completed`, `payment.failed`
- scheduled wallet credit retry every `60000 ms`.
- DLQ queue `founderlink.dlq`.

---

## Service Name: wallet-service

### Purpose

- Tracks each startup's raised balance.

### Key Responsibilities

- Create startup wallet.
- Deposit funds idempotently.
- Return wallet and balance.

### Exposed APIs

#### `POST /wallets/{startupId}`

- Description: create wallet or return existing wallet.
- Request body: none.
- Response body:
```json
{
  "message": "Wallet created successfully",
  "data": {
    "id": 1,
    "startupId": 3,
    "balance": 0.00,
    "createdAt": "<LocalDateTime>",
    "updatedAt": "<LocalDateTime>"
  }
}
```
- Status codes: `201`, `400`, `404`, `409` documented; actual service returns existing wallet instead of conflict when already present.

#### `POST /wallets/deposit`

- Description: deposit money into startup wallet.
- Request body:
```json
{
  "referenceId": 1,
  "startupId": 3,
  "amount": 1000.00,
  "sourcePaymentId": 10,
  "idempotencyKey": "wallet-deposit-1"
}
```
- Response body:
```json
{
  "message": "Funds deposited successfully",
  "data": {
    "id": 1,
    "startupId": 3,
    "balance": 1000.00,
    "createdAt": "<LocalDateTime>",
    "updatedAt": "<LocalDateTime>"
  }
}
```
- Status codes: `200`, `400`, `404`.
- Validation rules:
  - every field required.
  - `amount` positive.
  - idempotency is enforced by existing `referenceId`, not by `idempotencyKey`.
  - if transaction for `referenceId` already exists, existing wallet state is returned unchanged.

#### `GET /wallets/{startupId}`

- Description: get wallet by startup id.
- Response body: wrapper with `WalletResponseDto`.
- Status codes: `200`, `404`.

### Internal Dependencies

- Called by `payment-service`.

---

## Service Name: config-server

### Purpose

- Centralized config source for all Spring Cloud services.

### Key Responsibilities

- Serve externalized config from Git.

### Exposed APIs

- Spring Cloud Config endpoints are framework-provided.
- Project-specific controller APIs: `UNKNOWN` (none found in code).

### Internal Dependencies

- Git repo `https://github.com/aditya-7562/FounderLink_config`.
- Eureka registration.

---

## Service Name: eureka-server

### Purpose

- Service discovery.

### Key Responsibilities

- Register and resolve service instances.

### Exposed APIs

- Eureka dashboard/API on port `8761`.
- Project-specific controller APIs: `UNKNOWN` (none found in code).

### Internal Dependencies

- None beyond Eureka framework.

---

# 3. Authentication & Authorization

## Authentication mechanism

- Access tokens: JWT signed with shared `JWT_SECRET`.
- Refresh tokens: opaque random tokens, hashed with SHA-256 in DB.
- Refresh token max sessions per user: `5`; oldest active session is revoked when limit is exceeded.

## JWT structure

- `sub`: stringified `userId`.
- `role`: single role string.
- standard timestamps: `iat`, `exp`.
- Access token expiry: `15 minutes`.

## Login/signup flow

- Register:
  1. `auth-service` validates request.
  2. User is inserted in auth DB.
  3. `auth-service` syncs same identity into `user-service` through `/users/internal`.
  4. `auth-service` publishes `user.registered`.
- Login:
  1. Email/password verified via Spring Security `AuthenticationManager`.
  2. JWT returned in response body.
  3. refresh token returned as secure cookie.
- Refresh:
  1. Refresh token validated.
  2. New access token issued.
  3. refresh token rotated.
- Logout:
  1. Refresh token revoked if valid.
  2. refresh cookie cleared.

## Role model

- `FOUNDER`
- `INVESTOR`
- `COFOUNDER`
- `ADMIN`

## Protected vs public gateway paths

- Public in gateway config:
    - /auth/**
  - Eureka paths
  - Swagger/OpenAPI paths
- Secured in gateway:
  - all other routed business endpoints by default.

## Gateway RBAC summary

- Founders:
  - startup create/update/delete and founder-specific reads
  - team invite/cancel/startup invitation list/remove member
  - investment status updates and startup investment reads
- Investors:
  - create investments
  - read own investments
  - create/confirm/get payments
- Cofounders:
  - reject invitation
  - join team
  - read own invitations, history, active roles
- Shared role access:
  - users, startup list/search/details, messages, notifications, team startup view


---

# 4. API Gateway Behavior

## Routing rules

- Prefixes are forwarded as-is; there is no `StripPrefix`.
- Controllers own the full routed path (`/auth/...`, `/users/...`, etc).

## Filters

- Global filter `AuthenticationFilter`:
  - bypasses `OPTIONS`.
  - skips auth for configured public paths.
  - requires `Authorization: Bearer <jwt>` on secured paths.
  - validates JWT signature/expiry/role.
  - checks RBAC against method + path.
  - injects:
    - `X-User-Id: <jwt sub>`
    - `X-User-Role: ROLE_<ROLE>`
    - `X-Auth-Source: gateway`

## Error response format on gateway auth/RBAC failure

```json
{
  "timestamp": "<Instant>",
  "status": 401,
  "error": "Unauthorized | Forbidden | Internal Server Error",
  "message": "string",
  "path": "/requested/path",
  "method": "GET | POST | PUT | DELETE"
}
```

## CORS

- Allowed origins:
  - `http://localhost:4200`
  - `http://frontend:4200`
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- Allowed headers: `*`
- `allowCredentials: true`

---

# 5. Data Models (CRITICAL)

## Auth models

- `RegisterRequest`
  - `name: string` required
  - `email: string` required
  - `password: string` required
  - `role: "FOUNDER" | "INVESTOR" | "COFOUNDER" | "ADMIN"` required
- `AuthResponse`
  - `token: string`
  - `email: string`
  - `role: string`
  - `userId: number`
- `RefreshToken` entity
  - `id: number`
  - `token: string` hashed
  - `userId: number`
  - `expiryDate: Instant`
  - `revoked: boolean`
  - `createdAt: Instant`
  - `revokedAt: Instant | null`
- `PasswordResetPin`
  - `id: number`
  - `pin: string(6)`
  - `email: string`
  - `expiryDate: LocalDateTime`
  - `used: boolean`
  - `createdAt: LocalDateTime`

## User models

- `UserResponseDto`
  - `userId: number`
  - `name: string | null`
  - `email: string`
  - `role: "FOUNDER" | "INVESTOR" | "COFOUNDER" | "ADMIN"`
  - `skills: string | null`
  - `experience: string | null`
  - `bio: string | null`
  - `portfolioLinks: string | null`
- `User` entity
  - same profile fields
  - `updatedAt: LocalDateTime | null`

## Startup models

- `StartupRequestDto`
  - `name: string` required
  - `description: string` required
  - `industry: string` required
  - `problemStatement: string` required
  - `solution: string` required
  - `fundingGoal: number` required, min `1000`
  - `stage: "IDEA" | "MVP" | "EARLY_TRACTION" | "SCALING"` required
- `StartupResponseDto`
  - `id: number`
  - `name: string`
  - `description: string`
  - `industry: string`
  - `problemStatement: string`
  - `solution: string`
  - `fundingGoal: number`
  - `stage: StartupStage`
  - `founderId: number`
  - `createdAt: LocalDateTime`
- `Startup` entity
  - same fields
  - `isDeleted: boolean`

## Investment models

- `InvestmentRequestDto`
  - `startupId: number` required
  - `amount: number` required, min `1000`
- `InvestmentStatusUpdateDto`
  - `status: "APPROVED" | "REJECTED" | "COMPLETED"` required
- `InvestmentResponseDto`
  - `id: number`
  - `startupId: number`
  - `investorId: number`
  - `amount: number`
  - `status: "PENDING" | "APPROVED" | "REJECTED" | "COMPLETED" | "PAYMENT_FAILED" | "STARTUP_CLOSED"`
  - `createdAt: LocalDateTime`
- `Investment` entity
  - same fields

## Team models

- `InvitationRequestDto`
  - `startupId: number` required
  - `invitedUserId: number` required
  - `role: "CTO" | "CPO" | "MARKETING_HEAD" | "ENGINEERING_LEAD"` required
- `InvitationResponseDto`
  - `id: number`
  - `startupId: number`
  - `founderId: number`
  - `invitedUserId: number`
  - `role: TeamRole`
  - `status: "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED"`
  - `createdAt: LocalDateTime`
  - `updatedAt: LocalDateTime | null`
- `JoinTeamRequestDto`
  - `invitationId: number` required
- `TeamMemberResponseDto`
  - `id: number`
  - `startupId: number`
  - `userId: number`
  - `role: TeamRole`
  - `isActive: boolean`
  - `joinedAt: LocalDateTime`
  - `leftAt: LocalDateTime | null`
- Relationships
  - `Invitation.startupId` points to startup-service record.
  - `TeamMember.startupId` points to startup-service record.
  - `Invitation.invitedUserId` and `TeamMember.userId` point to user-service records.

## Messaging models

- `MessageRequestDTO`
  - `senderId: number` required
  - `receiverId: number` required
  - `content: string` required
- `MessageResponseDTO`
  - `id: number`
  - `senderId: number`
  - `receiverId: number`
  - `content: string`
  - `createdAt: LocalDateTime`
- `Message` entity
  - same fields

## Notification models

- `NotificationResponseDTO`
  - `id: number`
  - `userId: number`
  - `type: string`
  - `message: string`
  - `read: boolean`
  - `createdAt: LocalDateTime`
- `Notification` entity
  - same fields

## Payment models

- `CreateOrderRequest`
  - `investmentId: number` required
- `CreateOrderResponse`
  - `orderId: string`
  - `amount: number`
  - `currency: "INR"`
  - `investmentId: number`
- `ConfirmPaymentRequest`
  - `razorpayOrderId: string` required
  - `razorpayPaymentId: string` required
  - `razorpaySignature: string` required
- `ConfirmPaymentResponse`
  - `status: string`
  - `investmentId: number`
- `PaymentResponseDto`
  - `id: number`
  - `investmentId: number`
  - `investorId: number`
  - `startupId: number`
  - `founderId: number`
  - `amount: number`
  - `status: "PENDING" | "INITIATED" | "SUCCESS" | "FAILED"`
  - `externalPaymentId: string | null`
  - `failureReason: string | null`
  - `createdAt: LocalDateTime`
  - `updatedAt: LocalDateTime`
- `Payment` entity
  - same business ids and amount
  - `idempotencyKey: string`
  - `razorpayOrderId: string | null`
  - `razorpayPaymentId: string | null`
  - `razorpaySignature: string | null`
  - `walletCredited: boolean`
  - `failureReason: string | null`

## Wallet models

- `WalletDepositRequestDto`
  - `referenceId: number` required
  - `startupId: number` required
  - `amount: number` required, positive
  - `sourcePaymentId: number` required
  - `idempotencyKey: string` required
- `WalletResponseDto`
  - `id: number`
  - `startupId: number`
  - `balance: number`
  - `createdAt: LocalDateTime`
  - `updatedAt: LocalDateTime`
- `Wallet` entity
  - same fields
- `WalletTransaction`
  - `id: number`
  - `wallet: Wallet`
  - `referenceId: number` unique
  - `sourcePaymentId: number`
  - `idempotencyKey: string`
  - `amount: number`
  - `createdAt: LocalDateTime`

---

# 6. End-to-End Flows (VERY IMPORTANT)

## User Registration

1. Frontend calls `POST /auth/register`.
2. `auth-service` stores auth user.
3. `auth-service` calls `POST /users/internal` with `X-Auth-Source: gateway` and `X-Internal-Secret`.
4. `auth-service` publishes `user.registered`.
5. `notification-service` consumes `user.registered` and sends welcome email.
6. Final outcome: account exists in both auth and user databases.

## Login and Token Refresh

1. Frontend calls `POST /auth/login`.
2. Backend returns JWT in JSON body and refresh token in secure cookie.
3. Frontend uses JWT in `Authorization: Bearer <token>` for secured calls.
4. When token expires, frontend calls `POST /auth/refresh`.
5. Backend validates refresh token, rotates it, returns fresh JWT, and resets cookie.

## Forgot Password / Reset Password

1. Frontend calls `POST /auth/forgot-password`.
2. `auth-service` creates 6-digit PIN valid for 5 minutes and publishes `password.reset`.
3. `notification-service` consumes event and emails the PIN.
4. Frontend collects `email`, `pin`, and `newPassword`.
5. Frontend calls `POST /auth/reset-password`.
6. Backend validates email + PIN + expiry + used flag, then updates password.

## Founder Creates Startup

1. Founder frontend sends `POST /startup` with JWT.
2. Gateway injects founder id/role headers.
3. `startup-service` validates founder role and request DTO, saves startup.
4. `startup-service` publishes `startup.created`.
5. `notification-service` consumes `startup.created`, fetches investors from `user-service`, creates notifications, and sends bulk email.
6. Final outcome: startup exists and investors are asynchronously notified.

## Investor Discovers and Invests

1. Frontend calls `GET /startup` or `GET /startup/search`.
2. Investor chooses startup and calls `POST /investments`.
3. `investment-service` validates startup existence via `startup-service`.
4. Investment is saved as `PENDING`.
5. `investment-service` publishes `investment.created`.
6. `notification-service` consumes `investment.created` and notifies founder.
7. Final outcome: founder sees pending investment request.

## Founder Approves Investment -> Payment Record Creation

1. Founder calls `PUT /investments/{id}/status` with `{ "status": "APPROVED" }`.
2. `investment-service` validates founder owns the startup.
3. Investment status becomes `APPROVED`.
4. `investment-service` publishes `investment.approved`.
5. `payment-service` consumes `investment.approved` and creates a `Payment` row with `status=PENDING`.
6. `notification-service` consumes `investment.approved` and notifies investor.
7. Final outcome: investor can now start checkout.

## Investor Payment Flow

1. Frontend calls `POST /payments/create-order` with `investmentId`.
2. `payment-service` finds `Payment` row created asynchronously from approval.
3. `payment-service` creates or reuses Razorpay order, saves `razorpayOrderId`, sets status `INITIATED`.
4. Frontend launches Razorpay checkout using returned `orderId` plus frontend-supplied `RAZORPAY_KEY_ID`.
5. Razorpay returns `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`.
6. Frontend calls `POST /payments/confirm`.
7. `payment-service` verifies signature, marks payment `SUCCESS`.
8. `payment-service` calls `wallet-service` to create wallet and deposit funds.
9. If wallet credit succeeds, `walletCredited=true`; if not, scheduled retry job keeps retrying every 60s.
10. `payment-service` publishes `payment.completed`.
11. `investment-service` consumes `payment.completed` and changes investment `APPROVED -> COMPLETED`.
12. `notification-service` consumes `payment.completed` and notifies investor/founder.
13. Final outcome: wallet balance increases and investment becomes `COMPLETED`.

## Founder Rejects Investment

1. Founder calls `PUT /investments/{id}/status` with `{ "status": "REJECTED" }`.
2. `investment-service` marks investment `REJECTED`.
3. `investment-service` publishes `investment.rejected`.
4. `payment-service` consumes event:
   - if no payment row yet, it does nothing.
   - if payment exists and is not `SUCCESS`, it marks payment `FAILED`.
5. `notification-service` notifies investor.
6. Final outcome: investment is rejected and any non-success payment is failed.

## Team Invitation and Join

1. Founder calls `POST /teams/invite`.
2. `team-service` verifies founder owns startup and saves pending invitation.
3. `team-service` publishes `team.invite.sent`.
4. `notification-service` creates notification and emails invited user.
5. Cofounder sees invitation via `GET /teams/invitations/user`.
6. Cofounder accepts by calling `POST /teams/join`.
7. `team-service` creates active `TeamMember`, marks invitation `ACCEPTED`, publishes `team.member.accepted`.
8. `notification-service` notifies founder.
9. Final outcome: startup team now includes the cofounder in one active role.

## Team Invitation Reject

1. Cofounder calls `PUT /teams/invitations/{id}/reject`.
2. `team-service` marks invitation `REJECTED` and publishes `team.member.rejected`.
3. `notification-service` notifies founder.

## Messaging and Notification Flow

1. Frontend calls `POST /messages`.
2. `messaging-service` validates sender and receiver against `user-service`.
3. Message is stored.
4. `messaging-service` publishes `message.sent`.
5. `notification-service` consumes event and creates notification for receiver.

## Startup Deletion Cascade

1. Founder calls `DELETE /startup/{id}`.
2. `startup-service` soft-deletes startup and publishes `startup.deleted`.
3. `investment-service` consumes `startup.deleted` and changes `PENDING` and `APPROVED` investments to `STARTUP_CLOSED`.
4. `team-service` consumes `startup.deleted`, cancels pending invitations, and deactivates active team members.
5. Final outcome: deleted startup disappears from active lists and dependent records are closed/cancelled asynchronously.

---

# 7. Error Handling Strategy

## Gateway auth/RBAC errors

- JSON shape:
```json
{
  "timestamp": "<Instant>",
  "status": 401,
  "error": "string",
  "message": "string",
  "path": "string",
  "method": "string"
}
```

## Service-specific error formats

- `auth-service`
```json
{
  "timestamp": "<Instant>",
  "status": 400,
  "message": "string",
  "path": "/auth/..."
}
```
- `user-service`
```json
{
  "timestamp": "<LocalDateTime>",
  "status": 400,
  "message": "VALIDATION_ERROR | NOT_FOUND | CONFLICT | INTERNAL_SERVER_ERROR",
  "error": "human-readable detail"
}
```
- `startup-service`, `investment-service`, `team-service`, `payment-service`
```json
{
  "status": 400,
  "message": "string",
  "timestamp": "<LocalDateTime>"
}
```
- `messaging-service`, `notification-service`
```json
{
  "status": 400,
  "message": "string",
  "timestamp": "<LocalDateTime>"
}
```
- `wallet-service`
  - custom error handler not found in code.
  - exact runtime error body via default Spring Boot error handling is `UNKNOWN`.

## Validation error variants

- `auth-service`: single concatenated string in `message`.
- `user-service`: code-like `message`, human text in `error`.
- `startup-service`: stringified field-error map in `message`.
- `investment-service`, `team-service`, `payment-service`: plain JSON object map of field -> message for Bean Validation failures.
- `messaging-service`: one concatenated string like `"field: message, field2: message"`.

## Common domain errors

- `401`: invalid credentials, invalid/expired refresh token.
- `403`: RBAC denial, owner mismatch, disallowed role, invitation/member authorization failures.
- `404`: missing startup, investment, payment, notification, user, team member, invitation.
- `409`: duplicate email, duplicate investment, duplicate invitation, already-team-member, idempotency conflict.
- `502/503`: downstream startup/user service or payment gateway issues.

---

# 8. Environment & Configuration

## Core ports

- `api-gateway`: `8090`
- `user-service`: `8081`
- `startup-service`: `8083`
- `investment-service`: `8084`
- `team-service`: `8085`
- `messaging-service`: `8086`
- `notification-service`: `8087`
- `payment-service`: `8088`
- `auth-service`: `8089`
- `wallet-service`: `8091`
- `config-server`: `8888`
- `eureka-server`: `8761`
- `zipkin`: `9411`
- `prometheus`: `9090`
- `grafana`: `3000`

## Required environment variables

- Database:
  - `DB_ROOT_PASSWORD`
  - `DB_USERNAME`
  - `DB_PASSWORD`
- Security:
  - `JWT_SECRET`
  - `INTERNAL_SECRET`
- RabbitMQ:
  - `RABBITMQ_USERNAME`
  - `RABBITMQ_PASSWORD`
- Mail:
  - `MAIL_HOST`
  - `MAIL_PORT`
  - `MAIL_SSL_ENABLE`
  - `MAIL_SMTP_AUTH`
  - `MAIL_USERNAME`
  - `MAIL_PASSWORD`
- Auth admin seed:
  - `SEED_ADMIN_ENABLED`
  - `SEED_ADMIN_NAME`
  - `SEED_ADMIN_EMAIL`
  - `SEED_ADMIN_PASSWORD`
- Payment:
  - `RAZORPAY_KEY_ID`
  - `RAZORPAY_KEY_SECRET`
- Grafana:
  - `GRAFANA_ADMIN_USER`
  - `GRAFANA_ADMIN_PASSWORD`

## Docker setup overview

- `docker-compose.infra.yml`
  - MySQL instance per service
  - Redis
  - RabbitMQ
  - Zipkin
  - Eureka
  - Config server
- `docker-compose.services.yml`
  - application containers
  - gateway publishes `8090:8090`
  - all service logs mounted to shared `service-logs` volume
- `docker-compose.monitoring.yml`
  - Loki
  - Promtail
  - Prometheus
  - Grafana

## Config server usage

- Every business service local `application.yml` imports `configserver:http://config-server:8888`.
- Common settings in config include:
  - Redis host/port
  - JPA defaults
  - RabbitMQ host/credentials
  - Eureka registration
  - Actuator/Prometheus exposure
  - Zipkin tracing endpoint

---

# 9. Observability (Optional but useful)

- Metrics:
  - Actuator exposes `health`, `info`, `prometheus`, `metrics`.
  - Prometheus scrapes gateway and every business service on `/actuator/prometheus`.
- Tracing:
  - Zipkin endpoint configured as `http://zipkin:9411/api/v2/spans`.
  - shared config sets sampling probability `1.0`.
  - Rabbit templates are observation-enabled in several services.
- Logging:
  - service logs written to `/var/log/founderlink/*.log`.
  - Promtail ships them to Loki.
  - Grafana is pre-provisioned with Loki, Zipkin, and Prometheus datasources.

---

# 10. Frontend Integration Notes (CRITICAL)

## Base API URL

- Browser clients should target the gateway, not direct services:
  - `http://localhost:8090`

## Required headers

- For secured endpoints:
  - `Authorization: Bearer <accessToken>`
- Do **not** send `X-User-Id`, `X-User-Role`, or `X-Auth-Source` from the frontend.
- Those are gateway-injected trusted headers.

## Auth token usage

- Store/use access token from `/auth/login` or `/auth/refresh`.
- If browser refresh cookie is used, frontend requests to `/auth/refresh` and `/auth/logout` should enable credentials.
- Important local-dev quirk:
  - refresh cookie is `Secure=true`.
  - over plain `http://localhost`, browsers usually will not send it.
  - backend does support refresh token via `Authorization` header as fallback.

## Payment integration requirements

- Backend does **not** expose `RAZORPAY_KEY_ID`.
- Frontend must receive Razorpay key id from frontend environment/config, not from current APIs.
- Create order first, then open Razorpay checkout, then call `/payments/confirm`.

## Pagination, filtering, sorting

- No pagination endpoints found.
- No generic sorting found.
- Only explicit filter API:
  - `GET /startup/search?industry=&stage=&minFunding=&maxFunding=`

## Eventual consistency / async timing

- After founder approves investment, payment record is created asynchronously by RabbitMQ listener.
- Investor UI should tolerate short delay before `/payments/investment/{investmentId}` exists.
- Startup deletion side effects on investments and team data are also asynchronous.

## API inconsistencies and quirks

- `GET /startup/{id}` returns plain DTO, while `GET /startup/details/{id}` returns wrapped `{ message, data }`.
- `GET /users`, `GET /users/{id}`, `PUT /users/{id}`, and messaging/notification endpoints return bare DTOs/lists, not wrapped responses.

- Messaging API takes `senderId` in request body instead of deriving it from JWT.
- Notification read/list APIs take `userId` in path and do not enforce ownership in service code.
- User profile update API also does not enforce ownership in service code.
- Wallet create is effectively idempotent despite Swagger docs implying possible conflict.
- Wallet deposit idempotency is keyed by `referenceId`; `idempotencyKey` is stored but not used for duplicate detection.
- Messaging and notification read fallbacks may return empty arrays instead of surfacing backend failure.
- Founder can manually set investment status to `COMPLETED` through `/investments/{id}/status`, even though payment completion also sets `COMPLETED`. Frontend should prefer payment-driven completion flow.

## Practical frontend assumptions to follow

- Always use current logged-in user's id for path/body fields even where backend does not strictly enforce ownership.
- Prefer wrapped response parsing for startup/investment/team/payment/wallet APIs.
- Prefer bare-array/bare-object parsing for auth, user, messaging, and notification APIs.
- Treat date fields as strings on the wire and map them into frontend date handling explicitly.

---

# 11. API Normalization Layer

## Frontend canonical response contract

All Angular data services should expose a single normalized envelope to components/state stores:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": "string"
}
```

## Normalization rules

- Wrapped responses:
  - If backend returns `{ "message": "...", "data": ... }`, expose `{ success: true, data: <body.data>, error: null }`.
  - If wrapped response contains `data: null` for delete/remove operations, preserve `data: null`.
- Raw DTO responses:
  - If backend returns a bare object DTO, expose `{ success: true, data: <body>, error: null }`.
- Raw array responses:
  - If backend returns a bare array, expose `{ success: true, data: <body>, error: null }`.
- Empty success responses:
  - `POST /auth/logout` returns `204` with no body. Normalize to `{ success: true, data: null, error: null }`.
- Error responses:
  - Any non-2xx response must normalize to `{ success: false, data: null, error: <human-readable string> }`.
  - Frontend services may keep the raw `HttpErrorResponse` internally for debugging, but component-facing state should still use the canonical envelope above.

## Known backend success response patterns and transformation rules

### Pattern A: wrapped success `{ message, data }`

- Services/endpoints:
  - `startup-service` frontend-facing endpoints:
    - `POST /startup`
    - `GET /startup`
    - `GET /startup/details/{id}`
    - `GET /startup/founder`
    - `PUT /startup/{id}`
    - `DELETE /startup/{id}`
    - `GET /startup/search`
  - `investment-service`:
    - `POST /investments`
    - `GET /investments/startup/{startupId}`
    - `GET /investments/investor`
    - `PUT /investments/{id}/status`
    - `GET /investments/{id}`
  - `team-service`:
    - `POST /teams/invite`
    - `PUT /teams/invitations/{id}/cancel`
    - `PUT /teams/invitations/{id}/reject`
    - `GET /teams/invitations/user`
    - `GET /teams/invitations/startup/{startupId}`
    - `POST /teams/join`
    - `GET /teams/startup/{startupId}`
    - `DELETE /teams/{teamMemberId}`
    - `GET /teams/member/history`
    - `GET /teams/member/active`
  - `payment-service`:
    - `POST /payments/create-order`
    - `POST /payments/confirm`
    - `GET /payments/{paymentId}`
    - `GET /payments/investment/{investmentId}`
  - `wallet-service`:
    - `POST /wallets/{startupId}`
    - `POST /wallets/deposit`
    - `GET /wallets/{startupId}`
- Transform rule:
  - `normalized.data = body.data`
  - `normalized.success = true`
  - `normalized.error = null`

### Pattern B: plain DTO success

- Services/endpoints:
  - `auth-service`:
    - `POST /auth/register`
    - `POST /auth/login`
    - `POST /auth/refresh`
    - `POST /auth/forgot-password`
    - `POST /auth/reset-password`
  - `user-service`:
    - `POST /users/internal` only for backend internal usage, not browser usage
    - `GET /users/{id}`
    - `PUT /users/{id}`
  - `startup-service`:
    - `GET /startup/{id}` plain DTO endpoint; frontend should prefer `GET /startup/details/{id}` unless a plain DTO is explicitly needed
  - `messaging-service`:
    - `POST /messages`
    - `GET /messages/{id}`
  - `notification-service`:
    - `PUT /notifications/{id}/read`
- Transform rule:
  - `normalized.data = body`
  - `normalized.success = true`
  - `normalized.error = null`

### Pattern C: plain array success

- Services/endpoints:
  - `user-service`:
    - `GET /users`
    - `GET /users/role/{role}`
  - `messaging-service`:
    - `GET /messages/conversation/{user1}/{user2}`
    - `GET /messages/partners/{userId}`
  - `notification-service`:
    - `GET /notifications/{userId}`
    - `GET /notifications/{userId}/unread`
- Transform rule:
  - `normalized.data = Array.isArray(body) ? body : []`
  - `normalized.success = true`
  - `normalized.error = null`

### Pattern D: empty success body

- Services/endpoints:
  - `auth-service`:
    - `POST /auth/logout`
- Transform rule:
  - `normalized.data = null`
  - `normalized.success = true`
  - `normalized.error = null`

## Known backend error patterns and transformation rules

### Pattern E: gateway auth/RBAC error

Backend shape:

```json
{
  "timestamp": "<Instant>",
  "status": 401,
  "error": "Unauthorized",
  "message": "string",
  "path": "/requested/path",
  "method": "GET"
}
```

Transform rule:

- `error = body.message || body.error || "Request failed"`
- If `status` is `401`, frontend should attempt token refresh once before redirecting to login.
- If `status` is `403`, frontend should not retry; show authorization error state.

### Pattern F: auth-service error

Backend shape:

```json
{
  "timestamp": "<Instant>",
  "status": 400,
  "message": "string",
  "path": "/auth/..."
}
```

Transform rule:

- `error = body.message || "Authentication request failed"`

### Pattern G: user-service error

Backend shape:

```json
{
  "timestamp": "<LocalDateTime>",
  "status": 400,
  "message": "VALIDATION_ERROR | NOT_FOUND | CONFLICT | INTERNAL_SERVER_ERROR",
  "error": "human-readable detail"
}
```

Transform rule:

- `error = body.error || body.message || "User request failed"`

### Pattern H: generic service error object

Applies to:

- `startup-service`
- `investment-service`
- `team-service`
- `payment-service`
- `messaging-service`
- `notification-service`

Backend shape:

```json
{
  "status": 400,
  "message": "string",
  "timestamp": "<LocalDateTime>"
}
```

Transform rule:

- `error = body.message || "Request failed"`

### Pattern I: Bean Validation field map

Applies to:

- `investment-service`
- `team-service`
- `payment-service`

Backend shape:

```json
{
  "fieldName": "validation message",
  "otherField": "validation message"
}
```

Transform rule:

- Flatten into a deterministic string:
  - `"fieldName: validation message; otherField: validation message"`
- Components with field-level forms should additionally map each key back onto form controls before exposing the flattened envelope error.

### Pattern J: startup-service validation stringified map

Observed behavior:

- `startup-service` validation errors are described as a stringified field-error map inside `message`.

Transform rule:

- Do not attempt brittle parsing in components.
- Normalize with:
  - `error = body.message || "Startup validation failed"`
- If field-level UX is required, maintain a client-side validation schema in Angular instead of relying on backend string parsing.

### Pattern K: messaging-service concatenated validation string

Observed behavior:

- Messaging validation errors are returned as a single string such as `"field: message, field2: message"`.

Transform rule:

- `error = body.message || "Message request failed"`

### Pattern L: wallet-service unknown default error shape

Observed behavior:

- Exact runtime error body is unknown from code inspection.

Transform rule:

- Apply fallback extraction order:
  - `body.message`
  - `body.error`
  - `statusText`
  - `"Request failed with status <status>"`

## Angular implementation rule

- Implement normalization once in a shared API adapter/interceptor layer.
- No Angular component should branch on backend-specific response shapes.
- No component should parse `{ message, data }` directly.
- No component should assume arrays or DTOs are returned raw.
- Prefer `GET /startup/details/{id}` over `GET /startup/{id}` for browser-facing startup detail views to avoid mixing plain and wrapped startup contracts on the same screen.

---

# 12. Frontend State Machines

## State derivation rule

Frontend state must not rely on a single backend field for investment/payment screens.

- Investment UI state is a composite derived from:
  - `investment.status`
  - `payment.status` when a payment exists
- Payment UI state is derived from:
  - `payment.status`
  - plus a temporary frontend-only "record not yet available" loading state before the payment row exists

## Investment Lifecycle

### Canonical composite derivation

Use this precedence order:

1. If `investment.status === "STARTUP_CLOSED"`, UI state = `STARTUP_CLOSED`
2. Else if `investment.status === "REJECTED"`, UI state = `REJECTED`
3. Else if `investment.status === "COMPLETED"`, UI state = `COMPLETED`
4. Else if `payment.status === "SUCCESS"` and `investment.status === "APPROVED"`, UI state = `SUCCESS`
5. Else if `payment.status === "INITIATED"` and `investment.status === "APPROVED"`, UI state = `INITIATED`
6. Else if `investment.status === "APPROVED"`, UI state = `APPROVED`
7. Else UI state = `PENDING`

Important clarification:

- Backend `InvestmentResponseDto` lists `PAYMENT_FAILED`, but the current document does not define a stable REST flow that surfaces it as a first-class user journey state.
- Frontend should therefore treat payment failure primarily through `payment.status === "FAILED"` inside the payment state machine, not invent a separate top-level investment screen unless backend behavior is later confirmed.

### `PENDING`

- UI meaning:
  - Investor has submitted interest.
  - Founder has not yet approved or rejected.
- Allowed user actions:
  - Investor: view details only.
  - Founder/Admin: approve or reject.
- Transitions:
  - `PENDING -> APPROVED`
  - `PENDING -> REJECTED`
  - `PENDING -> STARTUP_CLOSED`

### `APPROVED`

- UI meaning:
  - Founder approved investment.
  - Payment row may or may not already exist.
  - No successful checkout has been confirmed yet.
- Allowed user actions:
  - Investor: begin payment flow.
  - Founder/Admin: backend technically allows `REJECTED` or `COMPLETED` transitions from `APPROVED`, but frontend should expose `REJECTED` only when business-approved and should not expose manual `COMPLETED` as the primary happy path.
- Transitions:
  - `APPROVED -> INITIATED`
  - `APPROVED -> SUCCESS`
  - `APPROVED -> COMPLETED`
  - `APPROVED -> REJECTED`
  - `APPROVED -> STARTUP_CLOSED`

### `INITIATED`

- UI meaning:
  - Payment record exists and Razorpay order has been created.
  - Checkout is resumable because `POST /payments/create-order` reuses the same order when status is already `INITIATED`.
- Allowed user actions:
  - Investor: resume checkout or retry opening checkout.
  - Founder/Admin: avoid status mutation from UI while checkout is in flight.
- Transitions:
  - `INITIATED -> SUCCESS` after successful `/payments/confirm`
  - `INITIATED -> APPROVED` if checkout fails before backend confirmation and latest payment fetch no longer reports `INITIATED`
  - `INITIATED -> REJECTED` only if founder rejects before successful payment and backend accepts that transition

### `SUCCESS`

- UI meaning:
  - Payment is confirmed in `payment-service`.
  - Investment may still show `APPROVED` until `payment.completed` is consumed by `investment-service`.
- Allowed user actions:
  - Investor: no payment retry action.
  - All roles: passive waiting state only.
- Transitions:
  - `SUCCESS -> COMPLETED`

### `COMPLETED`

- UI meaning:
  - Final successful investment state.
  - Payment completed and investment-service has reconciled.
- Allowed user actions:
  - Investor: view receipt/history.
  - Founder: treat as funded investment record.
- Transitions:
  - terminal

### `REJECTED`

- UI meaning:
  - Founder/admin explicitly rejected the investment.
- Allowed user actions:
  - view-only
- Transitions:
  - terminal

### `STARTUP_CLOSED`

- UI meaning:
  - Startup was deleted and async cleanup closed the investment.
- Allowed user actions:
  - view-only
- Transitions:
  - terminal

## Payment Lifecycle

Important clarification:

- Before a `Payment` row exists, the frontend is in a temporary loading/retry state, not yet in a backend payment state.
- Once `GET /payments/investment/{investmentId}` succeeds, use only the backend payment statuses below.

### `PENDING`

- UI meaning:
  - Payment row exists but Razorpay order has not been initiated yet.
- Button states:
  - Primary CTA enabled: `Start Payment`
  - Secondary CTA optional: `Refresh Status`
- Retry behavior:
  - No retry loop needed once payment row exists.
  - `POST /payments/create-order` is the next action.
- UI messaging:
  - `"Approval received. You can now start payment."`
- Transitions:
  - `PENDING -> INITIATED`
  - `PENDING -> FAILED` only if backend later reports failed compensation/rejection behavior

### `INITIATED`

- UI meaning:
  - Razorpay order exists and checkout can be resumed.
- Button states:
  - Primary CTA enabled: `Resume Payment`
  - Disable duplicate "Confirm" button until Razorpay callback payload is available.
- Retry behavior:
  - If checkout modal closes without success, allow retry by calling `POST /payments/create-order` again.
  - Backend may return the same order id; frontend should treat that as success, not duplication.
- UI messaging:
  - `"Payment started. Complete checkout to finish investment."`
- Transitions:
  - `INITIATED -> SUCCESS`
  - `INITIATED -> FAILED`

### `SUCCESS`

- UI meaning:
  - Razorpay signature verified and payment confirmed.
  - Wallet credit retry may still be happening asynchronously, but payment itself is successful.
- Button states:
  - All payment CTAs disabled.
- Retry behavior:
  - No retry.
- UI messaging:
  - `"Payment successful. Final investment status is being synchronized."`
- Transitions:
  - terminal in payment state machine

### `FAILED`

- UI meaning:
  - Payment is no longer confirmable via `/payments/confirm`.
- Button states:
  - Show `Retry Payment` only if a fresh `POST /payments/create-order` attempt succeeds.
  - Otherwise keep CTA disabled and show support/manual refresh guidance.
- Retry behavior:
  - Clicking retry should call `POST /payments/create-order`.
  - If backend returns `2xx`, move back to `INITIATED`.
  - If backend returns non-2xx, remain in `FAILED` and show normalized error.
- UI messaging:
  - `"Payment failed. Retry only after refreshing the latest payment status."`
- Transitions:
  - `FAILED -> INITIATED` only on successful new/reused order creation
  - otherwise terminal for current attempt

## Team Invitation Lifecycle

### `PENDING`

- UI meaning:
  - Invitation exists and cofounder has not acted.
- Allowed user actions:
  - Founder: cancel.
  - Invited cofounder: accept or reject.
- Transitions:
  - `PENDING -> ACCEPTED`
  - `PENDING -> REJECTED`
  - `PENDING -> CANCELLED`

### `ACCEPTED`

- UI meaning:
  - Invitation has been consumed and active team membership exists.
- Allowed user actions:
  - view-only from invitation screen; membership management moves to team endpoints
- Transitions:
  - terminal

### `REJECTED`

- UI meaning:
  - Invited cofounder declined.
- Allowed user actions:
  - view-only
- Transitions:
  - terminal

### `CANCELLED`

- UI meaning:
  - Founder cancelled the still-pending invitation.
- Allowed user actions:
  - view-only
- Transitions:
  - terminal

---

# 13. Async & Retry Strategy

## Deterministic frontend rule

- Use optimistic UI only for the resource directly returned by the current request.
- For any downstream event-driven side effect, keep prior confirmed data visible and mark it as reconciling instead of pretending the async chain already completed.

## Payment Creation Race Condition

Scenario:

- Founder approves investment.
- `investment-service` returns `APPROVED` immediately.
- `payment-service` creates the payment row asynchronously via RabbitMQ.
- Investor may reach the payment screen before `GET /payments/investment/{investmentId}` is available.

Required frontend behavior:

- Retry interval: `2 seconds`
- Max retries: `5`
- Total wait window: `10 seconds`
- Fallback: show explicit error state if payment record never appears

Recommended sequence:

1. When investment first becomes `APPROVED`, call `GET /payments/investment/{investmentId}`.
2. If response is `200`, stop polling and render payment state from returned `PaymentResponseDto`.
3. If response is `404`, treat it as eventual consistency during this specific approval-to-payment window and retry after `2 seconds`.
4. If response is `403`, `502`, `503`, or any normalized hard error, stop polling immediately and show error state.
5. After `5` consecutive `404` responses, stop polling and show:
   - `"Payment setup is taking longer than expected. Please refresh or try again later."`
6. While polling, keep investment card/detail visible with a non-blocking reconciling indicator rather than replacing the screen with a full-page spinner.

## Eventual consistency cases

### Investment approval -> payment availability

- Trigger:
  - immediately after `PUT /investments/{id}/status` returns `APPROVED`
  - and when investor opens an investment detail screen already showing `APPROVED`
- Poll:
  - `GET /payments/investment/{investmentId}`
- Show loading:
  - only for the payment section or CTA area
- Show stale data:
  - keep the investment itself rendered as `APPROVED`
- Refetch:
  - stop on first `200`
  - after payment appears, refetch `GET /investments/{id}` once after successful `/payments/confirm` to catch `COMPLETED`

### Payment success -> investment completion

- Trigger:
  - immediately after `POST /payments/confirm` returns success
- Poll/refetch:
  - refetch `GET /investments/{id}` every `2 seconds`, max `5` times, until status becomes `COMPLETED`
- Show loading:
  - use a localized "finalizing investment" indicator
- Show stale data:
  - keep payment success UI visible while investment still reads `APPROVED`
- Fallback:
  - if investment does not become `COMPLETED` within the retry window, keep payment marked successful and show investment as syncing instead of downgrading to failure

### Startup deletion -> investment/team updates

Scenario:

- `DELETE /startup/{id}` returns success immediately from `startup-service`.
- Investment and team cleanup occurs asynchronously in downstream consumers.

Required frontend behavior:

- On delete success:
  - immediately remove the startup from active startup listing screens
  - immediately block actions on the deleted startup detail page
- Refetch affected datasets:
  - founder startup list: immediate refetch once
  - startup investments: refetch after `2 seconds`, then again every `2 seconds` up to `5` total attempts if stale records still exist
  - team invitations/members for that startup: same refetch schedule
- Show loading:
  - only on the specific affected list or panel during first refetch
- Show stale data:
  - if downstream cleanup has not completed, keep the list visible but show a banner such as:
    - `"Startup deletion is still propagating. Some related records may update shortly."`
- Stop polling:
  - as soon as investment statuses become `STARTUP_CLOSED` and team lists reflect cancellation/deactivation
- Fallback:
  - after max retries, keep stale banner and surface manual refresh action

## When to poll vs refetch vs show stale data

- Poll when:
  - the current screen is blocked on an event-driven side effect for a single resource
  - examples:
    - payment row creation
    - investment completion after payment success
- Refetch when:
  - a mutation likely changed multiple lists or related resources
  - examples:
    - startup deletion
    - invitation accept/reject if list and team roster are both visible
- Show loading when:
  - user has no previously loaded data for that view
  - or the current action is synchronous and blocking
- Show stale data when:
  - previous data is still meaningful, but async reconciliation is pending
  - examples:
    - `APPROVED` investment waiting for payment record
    - deleted startup dependencies still updating

---

# 14. Security Warnings & Frontend Guards

Frontend must compensate for the following unsafe backend patterns. These are not optional UI niceties; they are mandatory guardrails.

## Messaging API accepts `senderId` from request body

- Risk:
  - A malicious client could submit a message as another user if the frontend passes arbitrary `senderId`.
- Mandatory frontend enforcement rule:
  - Never bind `senderId` to user-editable state.
  - Always overwrite `senderId` with the authenticated user's id at request construction time.
  - If route params or component inputs contain another sender id, ignore them.
- Angular implementation note:
  - `sendMessage(receiverId, content)` service method should derive `senderId` from the auth store only.

## Notification APIs accept arbitrary `userId`

- Risk:
  - A malicious client could request another user's notifications by changing the path param.
- Mandatory frontend enforcement rule:
  - Always derive notification `userId` from the authenticated session, not from route params or local component state.
  - Do not expose notification user id as an editable or navigable value in the UI.
  - If a route includes `/notifications/:userId`, validate that it matches the logged-in user and redirect otherwise.
- Angular implementation note:
  - Notification facade methods should have signatures like `loadMyNotifications()` and `loadMyUnreadNotifications()`, not `loadNotifications(userId)`.

## User update API does not enforce profile ownership

- Risk:
  - A malicious client could attempt to update another user's profile by changing `/users/{id}`.
- Mandatory frontend enforcement rule:
  - Always use the authenticated user's id for self-profile update screens unless an admin-specific UI is intentionally built.
  - Never trust a route param alone to decide which profile is editable.
  - For non-admin UI, disable edit mode if route id and session user id differ.
- Angular implementation note:
  - `updateMyProfile(payload)` should derive the id from auth state and call `PUT /users/{currentUserId}`.

## General frontend guard policy for unsafe ownership patterns

- Never trust `userId`, `senderId`, `founderId`, or `investorId` values stored in component state if the authenticated session already provides the authoritative id.
- Treat all user-identifying request fields as write-protected derived fields.
- Route guards must validate role-sensitive pages before rendering:
  - founder-only mutation pages
  - investor payment pages
  - cofounder invitation acceptance pages
- Hide backend-dangerous operations that exist but are not recommended:
  - manual founder-driven investment `COMPLETED` status update in normal UI

---

# 15. Frontend Data Mapping Layer

## Core transformation rules

### Date/time handling

- Backend commonly returns `LocalDateTime` strings without timezone offset.
- Frontend mapping rule:
  - keep the raw wire value as a string
  - convert it into a normalized ISO-like string through one shared adapter
  - create `Date` objects only in that adapter, not ad hoc in components
- Important limitation:
  - because `LocalDateTime` has no offset, timezone meaning is not explicit in the current backend contract
  - frontend must therefore avoid assuming UTC silently
  - if the Angular app needs exact cross-timezone semantics, backend should later emit `OffsetDateTime` or `Instant`

Recommended adapter contract:

```ts
export interface DateValue {
  raw: string | null;
  iso: string | null;
  date: Date | null;
}
```

Recommended mapping rule:

- If backend sends `null`, map to `{ raw: null, iso: null, date: null }`
- If backend sends a timezone-less `LocalDateTime`, store:
  - `raw = backend value`
  - `iso = backend value`
  - `date = new Date(iso)` only through one central parser so behavior is consistent across the app

### Enum to label mapping

Use explicit maps, not string manipulation scattered through templates.

Recommended labels:

- Startup stage:
  - `IDEA -> "Idea"`
  - `MVP -> "MVP"`
  - `EARLY_TRACTION -> "Early Traction"`
  - `SCALING -> "Scaling"`
- Investment lifecycle labels:
  - `PENDING -> "Pending Review"`
  - `APPROVED -> "Approved"`
  - `INITIATED -> "Payment Started"`
  - `SUCCESS -> "Payment Successful"`
  - `COMPLETED -> "Completed"`
  - `REJECTED -> "Rejected"`
  - `STARTUP_CLOSED -> "Startup Closed"`
- Payment status:
  - `PENDING -> "Ready for Payment"`
  - `INITIATED -> "Checkout Started"`
  - `SUCCESS -> "Paid"`
  - `FAILED -> "Payment Failed"`
- Team invitation status:
  - `PENDING -> "Awaiting Response"`
  - `ACCEPTED -> "Accepted"`
  - `REJECTED -> "Rejected"`
  - `CANCELLED -> "Cancelled"`
- Team role:
  - `CTO -> "CTO"`
  - `CPO -> "CPO"`
  - `MARKETING_HEAD -> "Marketing Head"`
  - `ENGINEERING_LEAD -> "Engineering Lead"`

### Monetary values

- Treat all monetary fields as `number` in the domain layer:
  - `fundingGoal`
  - `amount`
  - `balance`
- Format only at the view layer.
- Default formatter for current payment flows:

```ts
export const inrCurrency = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});
```

### Null handling strategy

- Preserve backend `null` in the domain layer.
- Do not eagerly coerce `null` to empty string except when binding form controls.
- Display strategy:
  - optional profile fields: show placeholder such as `"Not provided"`
  - optional dates: show `"--"`
  - optional ids such as `externalPaymentId`: hide the row if null
- Form strategy:
  - map `null -> ""` on form patching
  - map empty form string back to `null` only if the backend field is optional

### Backend-to-frontend field clarifications

- `PaymentResponseDto.externalPaymentId` conceptually contains Razorpay payment id data, because mapper source is `razorpayPaymentId`.
- Frontend should therefore label it for users as payment reference / transaction id, not as an internal generic external id.

## TypeScript-ready interfaces

```ts
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: string | null;
}

export type UserRole = 'FOUNDER' | 'INVESTOR' | 'COFOUNDER' | 'ADMIN';
export type StartupStage = 'IDEA' | 'MVP' | 'EARLY_TRACTION' | 'SCALING';
export type InvestmentStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'PAYMENT_FAILED'
  | 'STARTUP_CLOSED';
export type PaymentStatus = 'PENDING' | 'INITIATED' | 'SUCCESS' | 'FAILED';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
export type TeamRole = 'CTO' | 'CPO' | 'MARKETING_HEAD' | 'ENGINEERING_LEAD';

export interface AuthSession {
  token: string;
  email: string;
  role: UserRole;
  userId: number;
}

export interface UserProfile {
  userId: number;
  name: string | null;
  email: string;
  role: UserRole;
  skills: string | null;
  experience: string | null;
  bio: string | null;
  portfolioLinks: string | null;
}

export interface Startup {
  id: number;
  name: string;
  description: string;
  industry: string;
  problemStatement: string;
  solution: string;
  fundingGoal: number;
  stage: StartupStage;
  founderId: number;
  createdAt: string;
}

export interface Investment {
  id: number;
  startupId: number;
  investorId: number;
  amount: number;
  status: InvestmentStatus;
  createdAt: string;
}

export interface Invitation {
  id: number;
  startupId: number;
  founderId: number;
  invitedUserId: number;
  role: TeamRole;
  status: InvitationStatus;
  createdAt: string;
  updatedAt: string | null;
}

export interface TeamMember {
  id: number;
  startupId: number;
  userId: number;
  role: TeamRole;
  isActive: boolean;
  joinedAt: string;
  leftAt: string | null;
}

export interface Message {
  id: number;
  senderId: number;
  receiverId: number;
  content: string;
  createdAt: string;
}

export interface AppNotification {
  id: number;
  userId: number;
  type: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface Payment {
  id: number;
  investmentId: number;
  investorId: number;
  startupId: number;
  founderId: number;
  amount: number;
  status: PaymentStatus;
  externalPaymentId: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Wallet {
  id: number;
  startupId: number;
  balance: number;
  createdAt: string;
  updatedAt: string;
}
```

## Angular mapping rule

- Raw API DTOs should be transformed inside dedicated mapper functions before reaching components.
- Components should consume domain models and UI view-models, not raw backend DTOs.
- Recommended layering:
  - `api client -> normalizer -> dto mapper -> facade/store -> component`

---

# 16. UI Flow Mapping (User Journeys)

## Investor Investment Flow

### 1. Discover startups

- UI steps:
  - open startup discovery page
  - optionally apply search filters
- API calls:
  - `GET /startup`
  - `GET /startup/search?industry=&stage=&minFunding=&maxFunding=`
- State transitions:
  - `startupList: idle -> loading -> loaded | error`

### 2. View startup details

- UI steps:
  - open startup detail page
  - render business details and investment CTA
- API calls:
  - preferred: `GET /startup/details/{id}`
- State transitions:
  - `startupDetail: loading -> loaded | error`

### 3. Submit investment

- UI steps:
  - enter amount
  - confirm submission
- API calls:
  - `POST /investments`
- State transitions:
  - `investmentSubmit: idle -> submitting -> success | error`
  - investment domain state becomes `PENDING`

### 4. Wait for approval

- UI steps:
  - investor views portfolio / investment detail
  - UI shows passive waiting state
- API calls:
  - `GET /investments/investor`
  - `GET /investments/{id}` for detail refresh
- State transitions:
  - `PENDING -> APPROVED | REJECTED | STARTUP_CLOSED`

### 5. Trigger payment

- UI steps:
  - when UI state becomes `APPROVED`, show payment section
  - if payment row not yet available, run the payment creation race-condition polling strategy
  - once payment row exists, user clicks `Start Payment`
  - frontend opens Razorpay checkout using `orderId` from backend and `RAZORPAY_KEY_ID` from frontend config
- API calls:
  - `GET /payments/investment/{investmentId}`
  - `POST /payments/create-order`
- State transitions:
  - composite investment state:
    - `APPROVED -> INITIATED`
  - payment state:
    - `PENDING -> INITIATED`

### 6. Confirm success

- UI steps:
  - receive Razorpay callback payload
  - submit payment confirmation
  - keep success UI visible while investment catches up asynchronously
- API calls:
  - `POST /payments/confirm`
  - `GET /payments/investment/{investmentId}` for final payment state if needed
  - `GET /investments/{id}` until investment becomes `COMPLETED`
- State transitions:
  - payment:
    - `INITIATED -> SUCCESS | FAILED`
  - composite investment:
    - `INITIATED -> SUCCESS -> COMPLETED`
    - or `INITIATED -> APPROVED` if payment must be retried and investment has not completed

## Founder Flow

### 1. Create startup

- UI steps:
  - open create-startup form
  - submit validated startup fields
- API calls:
  - `POST /startup`
- State transitions:
  - `startupCreate: idle -> submitting -> success | error`
  - startup roster/list should refetch after success

### 2. Manage investments

- UI steps:
  - open founder dashboard
  - load owned startups
  - choose a startup
  - load investments for that startup
- API calls:
  - `GET /startup/founder`
  - `GET /investments/startup/{startupId}`
- State transitions:
  - `dashboard: loading -> loaded`
  - per-investment state reflects backend statuses from list response

### 3. Approve or reject investment

- UI steps:
  - founder reviews pending investment
  - chooses approve or reject
  - UI updates returned investment immediately
- API calls:
  - `PUT /investments/{id}/status`
- State transitions:
  - `PENDING -> APPROVED`
  - `PENDING -> REJECTED`
  - possible later async transition:
    - `APPROVED -> COMPLETED` after payment success
- Frontend restriction:
  - do not surface manual `COMPLETED` as the default founder action even though backend technically permits it

## Cofounder Flow

### 1. Receive invite

- UI steps:
  - open invitation center or notifications area
  - load pending invitations for current cofounder
- API calls:
  - `GET /teams/invitations/user`
  - optionally `GET /notifications/{currentUserId}` for cross-surface entry points
- State transitions:
  - `invitationList: loading -> loaded | error`
  - each invitation starts at `PENDING`

### 2. Accept or reject

- UI steps:
  - accept invitation to join team
  - or reject invitation
- API calls:
  - accept:
    - `POST /teams/join`
  - reject:
    - `PUT /teams/invitations/{id}/reject`
- State transitions:
  - `PENDING -> ACCEPTED`
  - `PENDING -> REJECTED`

### 3. View team

- UI steps:
  - after acceptance, open current active roles and team view
  - optionally inspect startup team roster if authorized
- API calls:
  - `GET /teams/member/active`
  - `GET /teams/member/history`
  - `GET /teams/startup/{startupId}` when user is an active member for that startup
- State transitions:
  - accepted invitation becomes membership-based UI
  - invitation state remains terminal `ACCEPTED`

## UI composition rules for Angular generation

- Build pages around user tasks, not around service boundaries.
- Keep async reconciliation visible in the UI:
  - approved investment waiting for payment record
  - successful payment waiting for completed investment
  - deleted startup waiting for downstream cleanup
- Co-locate API calls with the state machines above:
  - a page should know its blocking synchronous call
  - a facade/store should own retry and polling behavior
- Avoid exposing raw backend implementation quirks directly in UI wording:
  - do not show `INITIATED` to users; label it as `Payment Started`
  - do not show raw validation map strings without frontend formatting
