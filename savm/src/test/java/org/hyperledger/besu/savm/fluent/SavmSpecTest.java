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
package org.hyperledger.besu.savm.fluent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.internal.SavmConfiguration;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class SavmSpecTest {
  @SuppressWarnings({"removal", "InlineMeInliner"})
  @Test
  void defaultChainIdAPIs() {
    Bytes32 defaultChainId = Bytes32.leftPad(Bytes.of(1));

    SavmSpec istanbulSAVM = SavmSpec.istanbul(SavmConfiguration.DEFAULT);
    assertThat(istanbulSAVM.getChainId()).contains(defaultChainId);

    SavmSpec berlinSAVM = SavmSpec.berlin(SavmConfiguration.DEFAULT);
    assertThat(berlinSAVM.getChainId()).contains(defaultChainId);

    SavmSpec londonSAVM = SavmSpec.london(SavmConfiguration.DEFAULT);
    assertThat(londonSAVM.getChainId()).contains(defaultChainId);

    SavmSpec parisSAVM = SavmSpec.paris(SavmConfiguration.DEFAULT);
    assertThat(parisSAVM.getChainId()).contains(defaultChainId);

    SavmSpec shanghaiSAVM = SavmSpec.shanghai(SavmConfiguration.DEFAULT);
    assertThat(shanghaiSAVM.getChainId()).contains(defaultChainId);

    SavmSpec cancunSAVM = SavmSpec.cancun(SavmConfiguration.DEFAULT);
    assertThat(cancunSAVM.getChainId()).contains(defaultChainId);

    SavmSpec pragueSAVM = SavmSpec.prague(defaultChainId.toBigInteger(), SavmConfiguration.DEFAULT);
    assertThat(pragueSAVM.getChainId()).contains(defaultChainId);

    SavmSpec osakaSAVM = SavmSpec.osaka(defaultChainId.toBigInteger(), SavmConfiguration.DEFAULT);
    assertThat(osakaSAVM.getChainId()).contains(defaultChainId);

    SavmSpec futureSipsVM = SavmSpec.futureSips(SavmConfiguration.DEFAULT);
    assertThat(futureSipsVM.getChainId()).contains(defaultChainId);
  }

  @Test
  void nullSavmSpec() {
    assertThrows(
        NullPointerException.class, () -> new SAVMExecutor(SavmSpec.savmSpec((SAVM) null)));
  }

  @Test
  void currentSAVM() {
    var subject = SavmSpec.savmSpec();
    assertThat(subject.getSAVMVersion()).isEqualTo(SavmSpecVersion.OSAKA);
  }

  @ParameterizedTest
  @EnumSource(SavmSpecVersion.class)
  void savmByRequest(final SavmSpecVersion version) {
    var subject = SavmSpec.savmSpec(version);
    assertThat(subject.getSAVMVersion()).isEqualTo(version);
  }

  @ParameterizedTest
  @EnumSource(SavmSpecVersion.class)
  void savmWithChainIDByRequest(final SavmSpecVersion version) {
    var subject = SavmSpec.savmSpec(version, BigInteger.TEN);
    assertThat(subject.getSAVMVersion()).isEqualTo(version);
    if (SavmSpecVersion.ISTANBUL.compareTo(version) <= 0) {
      assertThat(subject.getChainId()).map(Bytes::trimLeadingZeros).map(Bytes::toInt).contains(10);
    } else {
      assertThat(subject.getChainId()).isEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(SavmSpecVersion.class)
  void savmWithChainIDByBytes(final SavmSpecVersion version) {
    var subject = SavmSpec.savmSpec(version, Bytes.fromHexString("0xc4a1201d"));
    assertThat(subject.getSAVMVersion()).isEqualTo(version);
    if (SavmSpecVersion.ISTANBUL.compareTo(version) <= 0) {
      assertThat(subject.getChainId())
          .map(Bytes::trimLeadingZeros)
          .map(Bytes::toInt)
          .contains(0xc4a1201d);
    } else {
      assertThat(subject.getChainId()).isEmpty();
    }
  }
}
