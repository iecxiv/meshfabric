# Registro / Log

Events queries the server with a unique tag, selected event-action filter and selected count. UI choices are 60 (default), 120, 250, 500, 1000 and Unlimited. Unlimited omits limit in the server request; the app rejects rather than truncates responses over 10000 records or 2000000 characters. Text fields remain bounded to 4096 characters. Legacy helper calls retain 200 for compatibility, not as a UI ceiling.

Changing selection starts a new bound request and retires prior replies. Search filters received records locally. Account/server changes, pause and observed access changes clear data. Server authorization remains authoritative; the history is not a complete audit log. Numeric msg values retain their literal representation. Unknown event types remain visible with a generic icon.

Read-only history; no event deletion or automatic mutation retries. Decode errors, explicit denial, timeout and empty results remain distinct. Automated checks do not replace live-server compatibility or physical-device visual review. Management-channel placement and previous-login dialogs are outside this change. Full validation is required before final dev integration.
