# Repository Instructions

## Coding Style And Local Simplification

Follow a pragmatic coding style in new development and when repairing existing code. Optimize for human understanding and maintainability, with low conceptual overhead and visible control flow. Apply design principles pragmatically; minimum line count and conformity to named principles are not goals in themselves.

- Keep coherent operations together so readers can follow their execution without unnecessary navigation between helpers. Long methods are acceptable when their steps belong to one operation. Method length alone does not justify extraction.
- Keep single-use implementation logic inline. Extract helper methods for actual reuse at multiple call sites or to enforce a required interface, callback, synchronization, or resource-lifetime boundary. Do not extract merely to name steps, shorten methods, satisfy complexity metrics, or make code easier to mock.
- Introduce abstractions for demonstrated shared behavior or real interchangeable implementations. Avoid unnecessary wrappers, configuration, boilerplate, dependencies, and speculative features or extension points.
- Accept small, straightforward duplication when sharing it would complicate the implementation. Share substantial logic that must remain consistent across callers.
- Prefer explicit loops, intermediate variables, and direct calls when they make execution and costs easy to inspect.
- Treat allocation, copying, memory use, I/O, and contention as concrete engineering concerns. Make these costs visible and preserve intentional performance properties.
- Use ordinary data structures and straightforward mutable state where appropriate. Make ownership, lifetime, and concurrency requirements understandable.
- Require strong typing: use meaningful types and properly parameterized collections so data structures and their relationships are understandable and easy to extend. Avoid raw types and unstructured `Object` containers when a concrete type can express the data.
- Use `final` for fields, parameters, and local variables wherever reassignment is unnecessary and it makes intent clearer. Mark classes or methods `final` when preventing inheritance or overriding is intentional.
- Follow the surrounding naming and formatting conventions. Established domain abbreviations and compact guard clauses are acceptable. Keep formatting, renaming, and modernization changes purposeful and within the task's commit file set.
- Write comments for constraints, representations, algorithms, and non-obvious decisions. Use short comments to mark stages inside longer operations when useful.
- Proactively simplify obvious existing overengineering throughout the affected files and classes in the file set intended to be committed for the task. Apply these rules beyond the directly changed lines; modest refactoring of an entire affected class is encouraged. Remove redundant structure and unnecessary indirection, regardless of who wrote the code.
- Keep style cleanup and refactoring within the task's defined commit file set. Do not expand that file set solely to spread cleanup into unrelated files or classes.
- Preserve required behavior, public contracts, security checks, error-handling guarantees, and concurrency boundaries while simplifying.
- Verify changed behavior proportionately and follow the focused testing requirements below. Do not restructure production code solely for testing convenience or introduce unrelated test scaffolding or coverage work.

## Web UI, API, Help, And Localization

When changing YaCy web pages or API endpoints, update all matching user-facing and tool-facing artifacts in the same change.

- For `htroot/**/*.html` changes, update the corresponding localization files under `locales/` when visible text, labels, form controls, messages, or navigation text changes.
- For `htroot/**/*.html` changes, update the corresponding Markdown help file under `help/`.
- For API or servlet behavior changes under `source/net/yacy/htroot/**`, update the related `help/**/*.md` file with changed endpoints, access requirements, parameters, side effects, response fields, and automation guidance.
- Treat `locales` and `help` updates as required checklist items for HTML, servlet, and API changes. Do not leave them for a follow-up unless the change is explicitly internal and has no user-visible page, request parameter, response, or behavior impact.

## Tests During Code Reviews

Do not treat the repository-wide test backlog as a separate mass-rewrite project unless explicitly requested. Improve tests incrementally in the context of individual code reviews.

- During each code review, identify the behavior affected by the reviewed code and add, update, or repair focused tests where they provide useful regression coverage.
- Keep test work scoped to the reviewed area and the changes needed to verify it.
- Report unrelated existing test failures, but do not expand the review into a repository-wide test cleanup solely because those failures exist.
- Over time, use successive reviews to bring the test suite up to date alongside the production code.
