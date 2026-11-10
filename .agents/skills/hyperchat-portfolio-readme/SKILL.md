---
name: hyperchat-portfolio-readme
description: Write or review Hyperchat's public README, release notes, and portfolio evidence. Use when documenting what Hyperchat does, how to reproduce it, and which Android or model checks actually passed.
---

# Hyperchat portfolio README

Help an Android or edge-AI reviewer understand the capability and reproduce the smallest useful run. Read the [README](../../../README.md), [architecture](../../../docs/architecture.md), build files, current source, and available test evidence before changing a claim.

1. Lead with four short statements in this order: **function → how to use → problem → solution**. Put a copyable code block directly below it, with no more than three nonblank lines. Include the actual GitHub URL, dependency setup, and the real entry point or smallest run. Keep unverified commands and fake demo media out of the introduction.
2. For each substantive claim, distinguish code present, local build/unit verification, actual target device verification, and roadmap. A screenshot, mock, simulator, or imported file alone does not prove an actual Galaxy action or local inference. List the revision, device/app/model version, and assertion when a real run is available; mark missing or failed evidence plainly.
3. Make the smallest confirmed workflow easy to try. Follow it with only the design tradeoff, evidence, limitations, and links a reader needs. Edit prose for clarity without rewriting code, commands, versions, metrics, quotations, or licenses as a style change.
4. Before a public handoff, check clone/build/run instructions against the current revision and verify local Markdown links. Check that no internal tracker URLs, private code or data, tokens, raw screen/log captures, or model files without redistribution rights would be published. Summarize the public review outcome and remaining blockers. Documentation work alone does not authorize publishing or changing external project state.

Preserve the distinction between observing or acting on a foreground app and accessing its internals. Keep a deterministic fallback, synthetic test, and real model run separate in the evidence table. Update the [changelog](../../../docs/CHANGELOG.md) when the supported workflow or its evidence changes.
