# UI localization inventory - not a completed localization

Version name is 1.1.1; version code remains 1. Four reviewed English-default terminal resource modules are now explicit values-en resources. Default translatable resource keys have explicit English coverage and placeholders are checked. This verifies resource structure, not translation meaning.

Hardcoded UI text still exists, notably management states, operation labels, errors and terminal labels. candidates.csv inventories literals across main Kotlin/Java and non-vendored JS/HTML plus literal XML UI attributes. It is heuristic: comments, protocol keys, technical data and non-UI strings may appear; interpolated/composed text and runtime/server errors require manual review. Candidate content/context are bounded. Never blindly replace protocol, command or backend data strings. No runtime clipboard, terminal history, credentials or network content is read.

This inventory is not proof that every UI phrase was found or translated. all_ui_externalized is deliberately false. Remaining migration needs reviewed resource mappings and lifecycle-safe resolution outside composables. No automatic language guessing or Spanish-as-English fallback is introduced.

Compilation and JVM/Node checks gate publication. Lint is collected separately and its outcome is recorded below; failed lint is not treated as clean. Device English/Spanish layout and accessibility need review. Focus, keyboard, special keys, scrolling and networking are unchanged.

Lint outcome: success
