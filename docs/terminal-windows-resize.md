# Windows terminal row growth

## Evidence and scope

The IME reproduction changed xterm from 29 to 55 rows, with 60 columns unchanged. Its normal buffer kept 207 lines while baseY moved from 178 to 152 and cursorY from 28 to 54. The next delivered block moved cursorY back to 28. The copied result omitted MF-LINE-148 through MF-LINE-173 (26 lines). This supports a row-growth/redraw mismatch; metadata alone does not prove the exact erase sequence or identify the PTY backend.

## Candidate compatibility change

Load terminal-windows-pty.js after xterm and before terminal-runtime. The script only installs its open wrapper when WindowsTerminalResizeDiagnostics.snapshot exists. The current native renderer exposes that bridge only for a node positively identified as DevicePlatform.WINDOWS at renderer creation. Linux, macOS and unknown/missing platform metadata do not expose it and do not get a wrapper. This dependency on the native Windows-only marker is intentional for this scoped candidate; removing or changing that diagnostic bridge requires updating the compatibility gate too.

Before open, configure the public windowsPty option only if supported and neither backend nor buildNumber is already specified. buildNumber: 0 is an unknown-build sentinel used to select xterm's Windows row-growth branch (defined buildNumber), not a claim about the remote OS. Do not infer conpty/winpty or parse the terminal banner. Existing precise PTY metadata takes precedence. No xterm private buffer mutations, filtering of remote escape sequences, scrollback replay or forced scroll are introduced. Existing resize scheduling and remote messages remain unchanged.

The upstream Buffer.resize implementation's Windows branch appends blank rows on growth rather than bringing historical rows back into the mutable viewport. This is the targeted mechanism. The bundled runtime must still be verified by device reproduction; unsupported windowsPty is intentionally left unchanged.

## Automated checks

Run:

    node app/src/test/js/terminal-windows-pty.test.cjs

These are routing and open-delegation tests with a mock Terminal, not a full bundled-xterm regression or Android integration test. They cover absent/invalid bridges, unknown-build configuration, preserving explicit backend/build metadata, unsupported options, idempotent installation and preserving open arguments, receiver and return value.

## Device acceptance

1. Open a new Windows terminal renderer with native platform metadata available.
2. Start Logcat capture for MeshTerminalWinResize.
3. In CMD run: for /L %i in (1,1,200) do @echo MF-LINE-%i
4. Wait for completion, then hide and show the keyboard without further output.
5. Verify all MF-LINE-1 through MF-LINE-200 remain in order in both scrollback and Select/Copy All. Repeat several cycles.
6. On growth from 29 to 55 rows with sufficient history and no trimming, verify the local resize does not decrease baseY by 26 to recover history; additional blank buffer rows should be available for the remote redraw. Check subsequent writes as well.
7. Test rotation, font changes, normal/alternate-screen applications and disconnect/reopen.
8. Repeat on Linux and a node with unknown platform; the compatibility wrapper must not be installed and prior resize behavior must remain unchanged.

APK compilation, bundled-xterm execution and device acceptance are not established by the mock tests.
