/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.savmtool.benchmarks;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.fluent.SavmSpec;
import org.hyperledger.besu.savm.precompile.PrecompiledContract;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.SequencedMap;

import org.apache.tuweni.bytes.Bytes;

/** Benchmark SHA256 precompile */
public class SHA256Benchmark extends BenchmarkExecutor {
  /**
   * The constructor. Use default math based warmup and interations.
   *
   * @param output where to write the stats.
   * @param config benchmark configurations.
   */
  public SHA256Benchmark(final PrintStream output, final BenchmarkConfig config) {
    super(MATH_WARMUP, MATH_ITERATIONS, output, config);
  }

  @Override
  public void runBenchmark(final Boolean attemptNative, final String fork) {
    SavmSpecVersion forkVersion = SavmSpecVersion.fromName(fork);

    if (attemptNative != null && attemptNative) {
      output.println("Native is unsupported, falling back to Java");
    }
    output.println("Java SHA256");

    PrecompiledContract contract =
        SavmSpec.savmSpec(forkVersion).getPrecompileContractRegistry().get(Address.SHA256);

    final SequencedMap<String, Bytes> testCases = new LinkedHashMap<>();
    final Random random = new Random();
    for (int len = 0; len <= 256; len += 16) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      testCases.put("size=" + len, Bytes.wrap(data));
    }
    precompile(testCases, contract, forkVersion);
  }

  @Override
  public boolean isPrecompile() {
    return true;
  }
}
