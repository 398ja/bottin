# Feature Specification: Nostr Profile Reach Stats

**Feature Branch**: `001-nostr-profile-reach`  
**Created**: 2026-06-25  
**Status**: Draft  
**Input**: User description: "add a new controller for nostr profile stats. I would like to calculate the reach for any npub. The reach is the total number of followers they have, i.e. how many users have them in their nip-02 list? The controller relies on a service for this calculation. The service runs on a predefined schedule, e.g. every 6 hours, it computes the reach for each users, and store the result in a database. The controller returns the information from the database, for any given npub. For the computation, we use the default application relays, but also, the npubs nip-65 relays."

## Clarifications

### Session 2026-06-25

- Q: Who should be able to call the reach lookup endpoint? → A: Public access without credentials, with per-client rate limiting (consistent with existing public NIP-05 endpoints).
- Q: Roughly how many registered profiles must each scheduled run process? → A: Up to ~10,000 profiles per run.
- Q: When a calculation could only gather followers from some relays (others failed), what should a lookup return? → A: Return the best-effort figure flagged as "partial/degraded", distinct from a complete figure.
- Q: Should a lookup enforce a maximum age beyond which a stored figure is treated as unusable? → A: No maximum age — always return the latest stored figure with its calculation timestamp; the consumer judges freshness.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Look up the reach of a profile (Priority: P1)

A consumer of the registry (an integrator, application, or operator) wants to know how influential a given Nostr profile is. They provide an `npub` and receive that profile's reach — the total number of distinct followers who currently list it in their contact list — together with the time the figure was last calculated.

**Why this priority**: This is the entire point of the feature. Without the ability to read a reach figure for a profile, none of the computation work delivers any value. It is the minimum viable slice: as soon as a single profile's reach is stored, this story is demonstrable.

**Independent Test**: With at least one profile's reach already stored, request the reach for that profile's `npub` and confirm a follower count and a "last calculated" timestamp are returned. Request a profile that has never been calculated and confirm a clear "not available" result.

**Acceptance Scenarios**:

1. **Given** a profile whose reach has been calculated and stored, **When** a consumer requests the reach for that profile's `npub`, **Then** the system returns the follower count and the timestamp of when it was calculated.
2. **Given** an `npub` that has never had its reach calculated, **When** a consumer requests its reach, **Then** the system responds that no reach figure is available for that profile rather than returning zero or an error implying a system fault.
3. **Given** a malformed or invalid `npub`, **When** a consumer requests its reach, **Then** the system rejects the request with a clear validation message.

---

### User Story 2 - Reach is kept current automatically (Priority: P2)

An operator wants reach figures to stay reasonably fresh without any manual action. On a recurring schedule (by default every 6 hours), the system recalculates the reach for every tracked profile and replaces the stored figures, so that consumers always read a recent value.

**Why this priority**: Lookups (Story 1) are usable with a single seeded value, but the feature only stays useful over time if figures refresh on their own. This story turns a one-off number into a continuously maintained statistic.

**Independent Test**: Trigger the scheduled calculation, observe that stored reach figures and their "last calculated" timestamps update for tracked profiles, then read a profile's reach via Story 1 and confirm the timestamp reflects the recent run.

**Acceptance Scenarios**:

1. **Given** the recurring schedule is active, **When** the scheduled interval elapses, **Then** the system recalculates reach for every tracked profile and updates the stored figures with a new "last calculated" timestamp.
2. **Given** a scheduled run is in progress, **When** a consumer requests a profile's reach, **Then** the system still returns the most recently completed stored figure without waiting for the run to finish.
3. **Given** a scheduled run cannot complete for a particular profile (for example, no follower data could be gathered), **When** the run finishes, **Then** the previously stored figure for that profile is preserved and the failure is recorded rather than overwriting the figure with an incorrect value.

---

### User Story 3 - Comprehensive follower coverage across relays (Priority: P3)

When calculating a profile's reach, the system gathers follower information from a broad set of sources: the application's default relays plus the relays the profile itself advertises in its NIP-65 relay list. This widens coverage so the count reflects followers visible on the networks the profile actually uses.

**Why this priority**: The feature still functions using only default relays (Stories 1 and 2 work without NIP-65), but incorporating each profile's own advertised relays materially improves the accuracy and completeness of the figure. It is an enhancement to correctness, not a prerequisite for a usable result.

**Independent Test**: For a profile that advertises additional relays via NIP-65 where extra followers are only discoverable, calculate its reach and confirm the resulting count includes followers found via the advertised relays, with no follower counted more than once across sources.

**Acceptance Scenarios**:

1. **Given** a profile with a published NIP-65 relay list, **When** its reach is calculated, **Then** the system consults both the default application relays and the profile's advertised relays.
2. **Given** the same follower is visible on more than one relay, **When** reach is calculated, **Then** that follower is counted exactly once.
3. **Given** a profile that has no NIP-65 relay list, **When** its reach is calculated, **Then** the system falls back to the default application relays only and still produces a figure.

---

### Edge Cases

- **Never calculated**: A profile that has not yet been processed by any scheduled run returns a "not available" result, distinct from a profile genuinely calculated to have zero followers.
- **Zero followers**: A tracked profile with no followers returns a reach of 0 with a valid "last calculated" timestamp.
- **Unreachable relays**: When some relays are unreachable or time out, the calculation proceeds with the sources that did respond, and the partial nature of the gathering is recorded so a degraded figure is not silently presented as authoritative.
- **All sources fail**: When no follower data can be gathered for a profile during a run, the previously stored figure is retained rather than replaced.
- **Duplicate follow entries**: A follower appearing on multiple relays, or with multiple contact-list events, is counted once based on its most recent contact list.
- **Stale follower / unfollow**: A user who has removed the profile from their latest contact list is not counted, even if older events still reference it.
- **Invalid identifier**: A request with a malformed `npub` (or unsupported identifier format) is rejected with a validation error before any lookup occurs.
- **Large follower sets**: Profiles with very large follower counts are calculated and stored without truncating the figure.
- **Schedule has never run**: Before the first scheduled run completes, all lookups return "not available".

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a way for consumers to retrieve the reach of a profile identified by its `npub`.
- **FR-002**: System MUST define reach as the number of distinct users whose current contact list (NIP-02) includes the target profile.
- **FR-003**: System MUST return, alongside the reach figure, the timestamp at which that figure was last calculated.
- **FR-004**: System MUST return a clear "not available" response when a requested profile has no stored reach figure, distinguishable from a calculated reach of zero.
- **FR-005**: System MUST validate the supplied identifier and reject malformed or unsupported identifiers with a clear error before performing a lookup.
- **FR-006**: System MUST serve reach lookups exclusively from previously stored figures and MUST NOT perform live calculation in response to a lookup request.
- **FR-007**: System MUST recalculate reach on a recurring schedule whose interval is configurable and defaults to every 6 hours.
- **FR-008**: System MUST, on each scheduled run, calculate reach for every tracked profile.
- **FR-009**: System MUST persist each calculated reach figure together with its calculation timestamp so it survives restarts and is available to later lookups.
- **FR-010**: System MUST gather follower information from the application's default relays for every calculation.
- **FR-011**: System MUST additionally gather follower information from each profile's advertised NIP-65 relays when such a relay list is available.
- **FR-012**: System MUST count each follower exactly once, even when the follower is observed across multiple relays or multiple events.
- **FR-013**: System MUST count a follower only when the target profile appears in that follower's most recent contact list.
- **FR-014**: System MUST preserve the previously stored figure for a profile when a scheduled run cannot gather any follower data for it, rather than overwriting it with an inaccurate value.
- **FR-015**: System MUST record the outcome of each scheduled run (including profiles processed, profiles skipped, and gathering failures) so operators can assess data quality and freshness.
- **FR-016**: System MUST continue serving the most recently completed figures while a scheduled recalculation is in progress.
- **FR-017**: System MUST allow reach lookups without authentication and MUST apply per-client rate limiting to protect against abuse, consistent with the project's existing public endpoints.
- **FR-018**: System MUST always return the latest stored figure for a tracked profile regardless of its age; it MUST NOT impose a maximum-age threshold that would withhold an otherwise-available figure. Freshness is communicated solely via the calculation timestamp (FR-003).
- **FR-019**: System MUST distinguish, in both storage and the lookup response, a figure gathered from a complete set of sources from one gathered from only a partial set of sources (because some relays failed or timed out), so consumers can tell that a partial figure may undercount.
- **FR-020**: System MUST be able to process up to approximately 10,000 tracked profiles within a single scheduled run, completing within the configured interval.

### Key Entities *(include if feature involves data)*

- **Profile Reach Stat**: The stored result for one tracked profile. Represents the profile's identity (its public key / `npub`), its most recently calculated reach (follower count), the timestamp of that calculation, and an indication of whether the figure was complete or gathered from a partial set of sources.
- **Tracked Profile**: A profile the system is responsible for calculating reach for. Corresponds to the set of profiles the registry already knows about (its registered identities). Drives which profiles each scheduled run processes.
- **Follower Relationship (transient)**: The relationship asserted by a user's current contact list indicating they follow the target profile. Not necessarily stored long-term; gathered during a run, de-duplicated, and reduced to the reach count.
- **Calculation Run Record**: A summary of a single scheduled execution, capturing when it ran, how many profiles were processed and skipped, and any gathering failures encountered.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A consumer can retrieve a profile's reach and its "last calculated" timestamp in a single request, with results returned effectively instantly (perceived as immediate, well under one second under normal load).
- **SC-002**: At any time after the first successful scheduled run, every tracked profile has a stored reach figure calculated within the last 6 hours (or within the configured interval).
- **SC-003**: For a profile that advertises additional relays carrying followers not visible on the default relays, the stored reach includes those additional followers, demonstrably higher than a default-relays-only count.
- **SC-004**: No follower is double-counted: a follower present on multiple relays contributes exactly one to the reach total.
- **SC-005**: When a scheduled run fails to gather data for a profile, that profile's previously stored figure and timestamp remain unchanged after the run.
- **SC-006**: Requests for never-calculated profiles and requests with invalid identifiers each return distinct, clear responses, with zero such requests being mistaken for a successful reach of 0.
- **SC-007**: Lookups continue to succeed and return prior figures throughout the duration of a scheduled recalculation run.
- **SC-008**: A scheduled run covering up to approximately 10,000 tracked profiles completes within the configured interval (default 6 hours).
- **SC-009**: Every lookup whose stored figure was gathered from an incomplete set of sources is returned with a partial/degraded indicator, distinguishable from a complete figure in 100% of cases.
- **SC-010**: Reach lookups are reachable without credentials, and a single client exceeding the configured request rate is throttled rather than allowed unlimited requests.

## Assumptions

- **Tracked profiles = registered identities**: "Each user" refers to the profiles already registered in the Bottin registry (its NIP-05 identity records). The scheduled job calculates reach for this known set; the feature does not introduce a separate registration mechanism for arbitrary external profiles.
- **Lookups are restricted to tracked profiles**: Because figures are only ever calculated for tracked profiles, a lookup for an `npub` outside that set returns "not available". Calculating reach on demand for arbitrary, untracked `npub`s is out of scope for this version.
- **Identifier format**: Consumers supply the profile as an `npub` (bech32) identifier; the system may also accept the equivalent hex public key for convenience. Other identifier forms are out of scope.
- **Default relays are an application-wide configuration**: A configurable set of default application relays exists (or will be introduced) and is shared across all calculations.
- **Live relay connectivity is a new capability**: The registry currently stores relay URLs but does not connect to relays. This feature introduces the ability to read follower (NIP-02) and relay-list (NIP-65) events from relays for the purpose of calculation.
- **Schedule default and configurability**: The recalculation interval defaults to every 6 hours and is operator-configurable, consistent with the existing scheduled-job configuration approach in the project.
- **Freshness over real-time accuracy**: Consumers accept that reach reflects the last completed scheduled run and may be up to one interval old; the feature does not promise real-time follower counts.
- **Single result per profile**: The system stores only the latest reach figure per profile (overwriting on each successful run); historical trend tracking of reach over time is out of scope for this version.
- **Registry scale**: The tracked profile set is expected to number up to ~10,000 profiles, which bounds the per-run workload and storage sizing.
- **Public, rate-limited access**: Reach is treated as non-sensitive, derived public data; the lookup is open to anonymous consumers but throttled per client, mirroring the existing public NIP-05 endpoints.
