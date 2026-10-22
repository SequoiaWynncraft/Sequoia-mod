# Java TLS trust

Standalone JVMs need the certificate blocklist update from the [OpenJDK advisory](https://openjdk.org/groups/vulnerability/advisories/2026-10-21).
Java 21.0.13 and Java 25.0.5 alone do not contain this fix.

`blocked.certs` contains the SHA-256 entries published at
<https://ops.java/unix/blocked.certs> and <https://ops.java/windows/blocked.certs>.
The published entry sets are identical. The installer preserves existing vendor
and operator blocks and requires SHA-256 format.

Before starting a standalone build, server or Minecraft launcher JVM, apply:

```sh
bash scripts/install-java-blocklist.sh "$JAVA_HOME"
```

Use the JVM home actually selected by the service or launcher, not just the shell
Java. Restart it after applying the update. This does not change a custom
application trust manager or another launcher-managed Java installation. Do not
edit a distribution-managed Java store; use that distribution's security update
and Java trust-store synchronization instead.
