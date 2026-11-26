# SDD005D3B1 implementation plan
Spec: docs/superpowers/specs/2026-10-05-sdd005d3b1-verification-ui-design.md
Base: 9b76cf6; existing codex branch, fixed workspace authorized.

Global constraints: own DB, trusted session ownership, append-only source/result, no fabricated actual data, no secrets; D3B2 automatic tasks remain pending.

- [x] Task 1: write failing MySQL/query and HTTP boundary tests; implement readonly query repository and controller, verify targeted tests; commit API.
- [x] Task 2: write failing panel tests; implement typed API, verification panel, history integration and README; frontend tests/build; commit UI.
- [x] Task 3: full regression, one fresh readonly review and one Critical/Important TDD fix pass; deploy prediction/frontend, record honest evidence and remaining D3B2; commit proof.

Interfaces: Task1 emits current/detail immutable evidence/result and expected fixed points; Task2 consumes exact string prices and explicit current pointer, null metrics; pagination20 and same auth identity. Task3 deploys verified artifacts only, no scheduler changes. Review package covers this base through final code.

Task3 Ruling: 实际部署暴露网关白名单遗漏，新增两个GET精确路径/两项回归并部署网关；最终完整243项与运行探针通过。唯一审查仅一轮，无重复派审。自动任务仍归D3B2。
