# AI Code Change Rules

1. **Understand before editing** – confirm the user request, inspect relevant files, and avoid assumptions.
2. **Follow repository conventions** – mirror existing formatting, naming, and architectural patterns unless the user asks for a change.
3. **Avoid inserting comments or explanatory text** unless the change explicitly needs documentation; rely on clean, self-explanatory code.
4. **Keep changes minimal** – edit only the files required to satisfy the request and leave unrelated files untouched.
5. **Preserve tooling scripts** (Gradle wrappers, shell scripts, batch files) by not removing legally required headers or metadata unless explicitly requested.
6. **Run relevant checks/tests** whenever feasible, or clearly note when they were skipped.
7. **Summarize results** – mention what was done, which files changed, and any follow-ups the user should take (e.g., tests/builds).
