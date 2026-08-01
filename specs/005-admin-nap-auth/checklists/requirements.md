# Specification Quality Checklist: Admin sign-in with a Nostr key

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-01
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**All items pass.** 5 user stories, 23 functional requirements, 10 success criteria.

**Iteration 1 — content quality fixes**

- *No implementation details*: the first draft named the authentication protocol and the signed
  event kind in requirements. Reworded to "prove control of a Nostr private key", so the spec
  states the outcome rather than the mechanism. The protocol name survives only in the feature
  title and the verbatim Input line, both the user's own words.
- *Scope is clearly bounded*: the request contained two efforts. The documentation effort is now
  excluded in an explicit "Out of scope" section rather than silently dropped, and recorded again
  in Assumptions.
- *Success criteria are technology-agnostic*: SC-004 originally referenced request payloads and
  header names. Rewritten as "appears in zero requests reaching the deployment and in zero log
  entries", verifiable without knowing the transport.

**Iteration 2 — both clarifications resolved**

- *Key storage (was FR-016)*: answered — the key is supplied once per device, held encrypted on
  that device, and unlocked by a passphrase thereafter. This added US4 (returning administrator),
  US5 (sign-out erases the key), FR-016 through FR-023, and SC-008 through SC-010.
- *Number of administrators (was FR-015)*: resolved to exactly one key per deployment, taken from
  "the npub" and "the master nsec", both singular in the request. Recorded in Assumptions with its
  consequence stated: two operators would have to share a private key, which is the shared secret
  this feature exists to remove, so a multi-administrator deployment needs a follow-up feature.

**Consequences worth carrying into planning**

- Sign-out is a deliberate erase, not merely a session end. FR-022 makes ending the session and
  removing the key one action, matching the client where signing out already erases the stored key.
- The passphrase protects the key at rest and nothing else. The deployment never sees it and cannot
  check it, so it is not a second authentication factor and must not be described as one.
- Session expiry and sign-out differ deliberately: expiry leaves the key in place so the passphrase
  is enough to resume, while sign-out removes it so the key is needed again.
