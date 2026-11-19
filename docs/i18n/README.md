# Localization audit and review status

English | [Español](../es/i18n/README.md)

Run `python3 tools/i18n/audit.py` from the repository. The audit writes summary.json, resource-issues.json and candidates.csv here. Missing explicit English/Spanish resource declarations, duplicate resources, array shape differences and format-signature differences block this maintenance publication; they are not filled with untranslated English fallback.

Candidate scanning covers main Kotlin/Java literals, non-vendored JS/HTML literals and literal XML UI attributes. HTML text, interpolated/composed text, accessibility, runtime errors, aliases, identical translations and excluded assets require manual review. Candidates include protocol keys, test tags, proper names, technical data and already bilingual helpers; they are not automatically classified as untranslated UI. Never blindly replace commands, identifiers or backend data.

Resource parity does not prove translation meaning or complete localization. The report deliberately leaves overall UI completeness unverified. Review remaining candidates, both languages, font scaling and accessibility on device. Server/agent output and user data are not automatically translated. No runtime clipboard, credentials, terminal history or network content is collected.

App-provided YubiKit overrides remain subject to the same review. A completed key response must not be described as a verified authenticated session. Build/test/lint results belong to the publishing job and its artifacts, not to a blanket visual-validation claim.

ONLINE and OFFLINE are intentional shared labels in both languages. They are not missing translations. CSV reports use LF line endings, quote every field and display embedded CR/LF as escape sequences.
