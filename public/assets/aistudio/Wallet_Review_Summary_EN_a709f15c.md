# Summary of Serious Issues in the Wallet Project

**Repository:** https://github.com/byteblazers2-del/Wallet\
**Review type:** Static Code Review\
**Project scope:** Non-custodial Android wallet for the TON network

> This report summarizes issues identified during the source review. The
> project was not built or tested on a physical device, and no
> independent security audit was performed. Items requiring runtime
> validation should be confirmed before release.

## Critical and High-Priority Issues --- Before Handling Real Assets

1.  **No independent security audit:** The security of seed generation,
    key derivation, transaction signing, and BOC construction has not
    been established through an independent audit.
2.  **Insufficiently verified protection of sensitive screens:**
    Explicit use of `FLAG_SECURE` was not observed in the reviewed
    Activity to prevent screenshots or screen recording. Seed phrase and
    other sensitive screens should be protected.
3.  **Android Keystore security level is unclear:** Using Android
    Keystore alone does not guarantee hardware-backed key protection on
    every device. The actual key security level should be detected and
    appropriate policies applied.
4.  **Chain state may be stale at transaction confirmation:** Relying on
    the `seqno` stored in a transaction Preview may result in stale,
    rejected, or uncertain transactions if the chain state changes. The
    state should be revalidated immediately before signing.
5.  **Insufficiently visible CI and release process:** No official
    release or visible CI workflow was found in the repository to
    demonstrate reliable build and test automation. Releases should be
    reproducible and signed using secure key management.

## Medium-Priority Issues

6.  **Reliance on public RPC providers:** Public TON Center/TonAPI
    services can introduce rate limits, outages, and privacy risks.
    Dedicated API keys, exponential backoff, and multiple providers are
    recommended.
7.  **Documentation/code mismatch for USDT Testnet:** The architecture
    documentation describes USDT support on Testnet, while the code's
    allowlist returns `null` for Testnet. Documentation and actual
    functionality must be aligned.
8.  **Approximate transaction-history matching:** Matching by recipient,
    amount, and time window when a hash is unavailable may incorrectly
    identify similar transactions. Stable on-chain identifiers should be
    used.
9.  **Outdated OkHttp dependency:** Version `4.10.0` is old and should
    be upgraded after compatibility and vulnerability review. This
    review did not establish a specific confirmed vulnerability in that
    exact version.
10. **Insufficient test coverage for a financial product:** Existing
    tests require independent execution and should be supplemented with
    integration tests, Testnet transaction tests, network interruption
    tests, duplicate transaction tests, seed recovery tests, and
    malformed-input tests.

## Lower-Priority, but Worth Fixing

11. **Seed handling in memory:** Sensitive data is held in strings and
    lists within UI state. Clearing references does not guarantee
    immediate memory erasure. Seed lifetime and unnecessary copies
    should be minimized.
12. **Incomplete cleanup on an error path:** In one decryption path, if
    seed extraction fails before entering `try/finally`, cleanup of the
    decrypted byte array is not guaranteed.
13. **Oversized classes and files:** Core components such as
    `TonCenterClient`, `WalletViewModel`, `WalletCore`, and `TxManager`
    have substantial size and multiple responsibilities. Splitting them
    would improve testability and maintainability.
14. **Missing ecosystem features:** TON Connect, dApp connection
    management, multi-account support, and other features may be needed
    for a competitive full-featured TON wallet. These are product gaps,
    not necessarily direct security vulnerabilities.

## Overall Assessment

The project has a viable technical foundation for a TON wallet, but
**the reviewed version should not yet be considered ready for public
release for storing real assets**. High-priority issues should be
addressed, comprehensive tests should be run, and an independent
security audit should be completed before launch.

## Review Limitations

This report is based on static review of the public source code.
Compilation, APK execution, physical-device testing, network traffic
analysis, and a formal security audit were not performed. Findings
should be validated through practical testing.
