# Reference Test Execution and Tracing Guide

This document explains how to run Sila reference tests in Besu and how to enable JSON tracing during test and block execution. This is useful for debugging SAVM behavior, inspecting opcode execution, and verifying correctness against official test vectors.

## Running the Reference Tests

To run the Sila reference tests included in the Besu codebase, use the following Gradle task:

```bash
./gradlew referenceTests
```

This will execute the available test suites (such as GeneralStateTests and execution-spec-tests) and validate Besu's SAVM behavior.

> **Note:**
> - Out-of-memory (OOM) errors are common due to the size and number of tests. You may need to increase the heap size using `-Xmx` (e.g., `./gradlew referenceTests -Dorg.gradle.jvmargs="-Xmx8g"`)

## Filtering Execution Spec Tests by Hardfork or SIP

Execution-spec-tests are generated with class names that reflect their hardfork and SIP directory structure. This allows targeted test execution using standard Gradle `--tests` filters.

### By hardfork

```bash
# Run all SilaPrague execution spec tests (blockchain + state)
./gradlew referenceTests --tests "*ExecutionSpec*_prague_*"

# Run only SilaAmsterdam state tests
./gradlew referenceTests --tests "*ExecutionSpecStateTest_amsterdam_*"

# Run all SilaCancun blockchain tests
./gradlew referenceTests --tests "*ExecutionSpecBlockchainTest_cancun_*"
```

### By SIP

```bash
# Run only SIP-7702 tests
./gradlew referenceTests --tests "*sip7702*"

# Run only SIP-4844 blob tests
./gradlew referenceTests --tests "*sip4844*"
```

### By hardfork + SIP

```bash
# Run SilaPrague SIP-2537 BLS precompile tests specifically
./gradlew referenceTests --tests "*_prague_sip2537_*"
```

### Static (legacy) tests

```bash
# Run all static legacy tests
./gradlew referenceTests --tests "*ExecutionSpec*_static_*"

# Run a specific static test category
./gradlew referenceTests --tests "*_static_stCreate2_*"
```

### Generated class name format

Test classes follow the pattern:
```
ExecutionSpec{Blockchain,State}Test_{hardfork}_{sip_or_topic}_{batch_index}
```

For example:
- `ExecutionSpecBlockchainTest_prague_sip7702_set_code_tx_0`
- `ExecutionSpecStateTest_cancun_sip4844_blobs_2`
- `ExecutionSpecBlockchainTest_static_stCreate2_1`
- `ExecutionSpecBlockchainTest_frontier_opcodes_0`

> **Note:** These hardfork/SIP filters apply only to execution-spec-tests. The legacy `GeneralStateReferenceTest` and `BlockchainReferenceTest` classes still use sequential numbering. For those, use the runtime system properties `test.sila.state.sips` and `test.sila.include` instead.

## Devnet / Pre-release Execution Spec Tests

In addition to the stable execution-spec-tests fixtures, Besu supports a second set of **pre-release (devnet) fixtures** from upstream. These contain tests for upcoming hardforks (e.g., SilaAmsterdam).

### Running devnet tests

```bash
# Run all devnet/pre-release reference tests
./gradlew referenceTestsDevnet

# Run only SilaAmsterdam devnet tests
./gradlew referenceTestsDevnet --tests "*_amsterdam_*"

# Run both stable + devnet
./gradlew referenceTests referenceTestsDevnet
```

The default `referenceTests` task excludes devnet tests, so CI is unaffected.

### Generated class name format

Devnet test classes follow the same pattern as stable ones, but with an `ExecutionSpecDevnet` prefix:

```
ExecutionSpecDevnet{Blockchain,State}Test_{hardfork}_{sip_or_topic}_{batch_index}
```

### Bumping the pre-release version

1. Update the `version` in the `devnetTarConfig` dependency in `sila/referencetests/build.gradle`
2. Make any required infrastructure changes (new header fields, etc.)
3. Run `./gradlew --write-verification-metadata sha256` to update checksums
4. Commit all changes together

### Configuration

The devnet fixtures are resolved from the same GitHub Ivy repository as stable fixtures. The dependency is declared separately via the `devnetTarConfig` configuration in `sila/referencetests/build.gradle`.

## Enabling JSON Tracing

Besu supports detailed opcode-level JSON tracing. You can enable it using either a JVM system property or an environment variable.

### Option 1: JVM System Property

```bash
-Dbesu.debug.traceBlocks=true
```

### Option 2: Environment Variable

```bash
export BESU_TRACE_BLOCKS=true
```

This enables a fallback implementation of `BlockAwareOperationTracer` if no plugin is configured. The default tracer used is `BlockAwareJsonTracer`.

JSON trace output does not appear in the console. To view it, open the associated Gradle test report (usually located in `build/reports/tests/test/index.html`) and find the specific test case output.

## Trace Contents

When enabled, tracing includes:

- Opcode execution and names
- Stack state
- Gas remaining and gas cost
- Memory size
- Precompile execution
- Contract creation and call frames
- Transaction lifecycle events (start, prepare, end)
- Exceptional halts

Each traced operation emits structured JSON data representing the SAVM state at that point.

## Output Format

The tracer prints a complete JSON trace of each block’s execution to standard output at the end of the block:

```
==== JSON Trace for Block <BLOCK_NUMBER> (<BLOCK_HASH>) ====
<trace entries>
```

Example:

```json
{
  "pc": 0,
  "op": "0x60",
  "opName": "PUSH1",
  "gas": 999999,
  "gasCost": 3,
  "stack": [],
  "memSize": 0,
  "depth": 1,
  "refund": 0
}
```

## Tracer Implementation

The tracer is implemented in:

```
org.hyperledger.besu.sila.sila-mainnet.BlockAwareJsonTracer
```

It uses a `StringWriter` and a `StandardJsonTracer` to collect and format execution traces. Output is flushed during the `traceEndBlock(...)` callback.

The `BlockAwareJsonTracer` is enabled automatically when no plugin provides a custom tracer and one of the tracing flags is set:

```java
if (Boolean.getBoolean("besu.debug.traceBlocks")
    || "true".equalsIgnoreCase(System.getenv("BESU_TRACE_BLOCKS"))) {
  return new BlockAwareJsonTracer();
}
```

## Notes

- Tracing is for debugging purposes only and should not be enabled in production environments.
- Trace output can become large, especially for blocks with many transactions.
- Tracing does not affect SAVM execution semantics.

## Resources

- [Sila Execution Spec Tests (sila/execution-spec-tests)](https://github.com/sila-chain/execution-spec-tests)
- [Sila Reference Tests (sila/tests)](https://github.com/sila-chain/tests)
- [SAVM Opcodes Reference](https://www.savm.codes/)