# E1 MaaS Prediction Explanation Implementation Plan

> Inline superpowers:executing-plans, one final readonly fresh reviewer and one Critical/Important TDD fix pass.
Goal: owned successful predictions obtain persisted, grounded MaaS explanations without changing numerical forecasts.
Architecture: MS03 context/templates and lease-backed ownDB operations; bounded Huawei client; gateway and Vue panel; hidden local config.
Tech Stack: Java17/MySQL8/Jdbc/Java HttpClient/Vue3 TypeScript/Compose.
Spec: docs/superpowers/specs/2026-10-07-sdd005e1-maas-prediction-explanation-design.md
Base: bad62b0, fixed existing codex workspace.

## Global Constraints
User-confirmed V2 URL/model, verified TLS, keys only ignored.env; Actor+CSRF+ownership; immutable forecast/current evidence references; 50slease/35sHTTP/2sconnect/128KiB/noredirect; exactly3 kinds, max500chars/paragraph, refs placeholders exact; one global slot; no auto retry storm or trading instructions.
## Review Focus
Owner before context/call; old account responses; active lease/replay/unknown network; changing current version retains old explanation; malformed provider/numbers/prompt injection and unconfigured failure leave forecast usable.

### Task 1 Context and template boundary
Files: explanation/ExplanationFacts,ExplanationTemplate,README; tests; scripts configure-maas plus cmd.
- [x] RED facts/number/ref/trade/malformed cases, implement deterministic facts and rendering; GREEN/commit.
### Task 2 Persistence, client and API
Files: MS03 V5,explanation repository/service/config/controller/client; gateway whitelist; guarded MySQL/security/HTTP tests; .env.example/config/compose.
- [x] RED identity/dedup/expiry/slot/replay/version/context transitions and bounded HTTP.
- [x] Implement network outside transactions, lease/state preserved, GET and POST exact auth route; GREEN/commit.
### Task 3 Panel and deployment
Files: frontend/api/explanations,ExplanationPanel and tests, PredictionResult, README; evidence/status/ledger.
- [x] RED auto-generation/failed/no retry flood/refresh/new context/old account isolation; implement accessible pure-text UI, GREEN.
- [x] Full backend/frontend/build, one review and one fix pass, local commit.
- [x] Configure local Key, deploy, few real MaaS requests and consistency/ownership/failure/snapshot unchanged checks; record honest limitations and commit.

Verification boundary: real client/templates and isolated owned/security APIs verified; signed-in browser UI not observed, listed in verification document.
