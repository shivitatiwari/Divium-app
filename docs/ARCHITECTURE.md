# Divium architecture

Divium keeps UI and Android lifecycle code separate from tooling implementation.

Core boundaries:

- Workspace service: project storage and document-tree synchronization.
- Runtime service: embedded Linux-like userspace and language runtimes.
- Process service: executable lifecycle and environment handling.
- Terminal service: PTY-backed interactive sessions.
- Project intelligence: detects frameworks/configuration and exposes safe actions such as Run, Build and Test.
- Git/GitHub: source-control operations and account integration.
- Extension host: capability-mediated workspace and runtime access.
- LSP/DAP: language intelligence and debugging protocols.

Extensions operate against Divium capability APIs. They can receive broad project permissions without gaining unrestricted access to unrelated device data.
