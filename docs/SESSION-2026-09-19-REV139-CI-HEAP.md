# Rev139 full-suite CI follow-up

CI run #631 (`35454388679`), commit `4ac89d90f7a174100c25d2855cfbf67fa3c74f81`,
compiled all production and test sources and remapped the mod successfully. All twelve new
`Presentation139RegressionTest` methods passed. The complete test worker then failed with
`java.lang.OutOfMemoryError: Java heap space` during the remaining aggregate suite; this is
not a green full-build result and its artifact was not published.

Set a bounded 2 GiB heap and one parallel fork for Gradle `Test` tasks. No assertion is removed,
no additional test is excluded, and no production source, game memory setting, runtime library
or client launch argument changes. The existing checksum-pinned external-corpus policy remains.
Converter revision remains `2026-09-19.139` because candidate/runtime behavior is unchanged.

The replacement full CI result must be checked independently before distributing the new mod.
