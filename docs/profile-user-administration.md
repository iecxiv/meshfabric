# Native user administration

Creation requires email, repeated matching passwords, and a username unless serverinfo advertises email identity. removeEvents and resetNextLogin default false. Previous-event removal carries an irreversible-history warning.

Lock/unlock changes only bit 32 of the current rights mask. Own-account locking and full-administrator masks are prohibited. Editing uses edituser.id, not userid; email/real-name drafts include local concurrency checks. Domain, identifier, advanced rights, consent, realms and security methods are READ-ONLY in the detail view. Cross-domain user administration is not implemented.

Sessions, user groups and visible nodes are queried explicitly. Missing/unsupported detail replies remain unavailable, not zero or none. Common-access lists describe received visible records, including direct and received user-group links, not a guaranteed complete server inventory. Device assignment grants selected direct rights to an existing visible device; it is not agent installation and does not revoke inherited rights.

Password changes preserve MFA and require a repeated password and explicit confirmation. A matching accountchange event with msgid 75 reports an observed password change, not a transactionally correlated acknowledgement. Edit readbacks and device-link observations verify the requested visible state; legacy events have no request-id guarantee. Writes are single-flight, never automatically retried, and an unconfirmed write times out by closing the channel. Secrets are not logged or persisted; dialogs use SecureOn.

Five policy tests are included but NOT executed. Android compilation, JUnit, lint, live-server mutations and physical-device UI behavior have NOT been validated here.
