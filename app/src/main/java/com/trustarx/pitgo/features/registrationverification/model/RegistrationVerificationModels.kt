package com.trustarx.pitgo.features.registrationverification.model

enum class RegistrationStatus { INCOMPLETE, IN_REVIEW, CORRECTION_REQUESTED, APPROVED }
enum class DocumentKind { CNH_FRONT, CNH_BACK, SELFIE, SELFIE_WITH_DOCUMENT, VEHICLE_DOCUMENT }
enum class RegistrationFailure { OFFLINE, TIMEOUT, SESSION, CONFLICT, INVALID_INPUT, SERVICE, UNKNOWN }

enum class RegistrationUploadState { RESERVED, RECEIVED }

enum class RegistrationSubmissionState { DRAFT, SUBMITTED, CORRECTION_REQUESTED, APPROVED }
