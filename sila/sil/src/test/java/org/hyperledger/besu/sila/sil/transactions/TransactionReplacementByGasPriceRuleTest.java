/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.sila.sil.transactions;

import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.util.number.Percentage;

import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TransactionReplacementByGasPriceRuleTest extends AbstractTransactionReplacementTest {

  public static Collection<Object[]> data() {
    return asList(
        new Object[][] {

          //   basefee absent
          {frontierTx(5L), frontierTx(6L), empty(), 0, true},
          {frontierTx(5L), frontierTx(5L), empty(), 0, true},
          {frontierTx(5L), frontierTx(4L), empty(), 0, false},
          {frontierTx(100L), frontierTx(105L), empty(), 10, false},
          {frontierTx(100L), frontierTx(110L), empty(), 10, true},
          {frontierTx(100L), frontierTx(111L), empty(), 10, true},
          //   basefee present
          {frontierTx(5L), frontierTx(6L), Optional.of(Wei.of(3L)), 0, true},
          {frontierTx(5L), frontierTx(5L), Optional.of(Wei.of(3L)), 0, true},
          {frontierTx(5L), frontierTx(4L), Optional.of(Wei.of(3L)), 0, false},
          {frontierTx(100L), frontierTx(105L), Optional.of(Wei.of(3L)), 10, false},
          {frontierTx(100L), frontierTx(110L), Optional.of(Wei.of(3L)), 10, true},
          {frontierTx(100L), frontierTx(111L), Optional.of(Wei.of(3L)), 10, true},
          //  sip1559 replacing frontier
          {frontierTx(5L), sip1559Tx(3L, 6L), Optional.of(Wei.of(1L)), 0, false},
          {frontierTx(5L), sip1559Tx(3L, 5L), Optional.of(Wei.of(3L)), 0, false},
          {frontierTx(5L), sip1559Tx(3L, 6L), Optional.of(Wei.of(3L)), 0, false},
          //  frontier replacing 1559
          {sip1559Tx(3L, 8L), frontierTx(7L), Optional.of(Wei.of(4L)), 0, false},
          {sip1559Tx(3L, 8L), frontierTx(8L), Optional.of(Wei.of(4L)), 0, false},
          //  sip1559 replacing sip1559
          {sip1559Tx(3L, 6L), sip1559Tx(3L, 6L), Optional.of(Wei.of(3L)), 0, false},
          {sip1559Tx(3L, 6L), sip1559Tx(3L, 7L), Optional.of(Wei.of(3L)), 0, false},
          {sip1559Tx(3L, 6L), sip1559Tx(3L, 7L), Optional.of(Wei.of(4L)), 0, false},
          {sip1559Tx(10L, 200L), sip1559Tx(10L, 200L), Optional.of(Wei.of(90L)), 10, false},
          {sip1559Tx(10L, 200L), sip1559Tx(15L, 200L), Optional.of(Wei.of(90L)), 10, false},
          {sip1559Tx(10L, 200L), sip1559Tx(21L, 200L), Optional.of(Wei.of(90L)), 10, false},
          //  pathological, priority fee > max fee
          {sip1559Tx(8L, 6L), sip1559Tx(3L, 7L), Optional.of(Wei.of(3L)), 0, false},
          {sip1559Tx(8L, 6L), sip1559Tx(3L, 7L), Optional.of(Wei.of(4L)), 0, false},
          //  pathological, sip1559 without basefee
          {sip1559Tx(8L, 6L), sip1559Tx(3L, 7L), Optional.empty(), 0, false},
          {sip1559Tx(8L, 6L), sip1559Tx(3L, 7L), Optional.empty(), 0, false},
        });
  }

  @ParameterizedTest
  @MethodSource("data")
  public void shouldReplace(
      final PendingTransaction oldTx,
      final PendingTransaction newTx,
      final Optional<Wei> baseFee,
      final int priceBump,
      final boolean expected) {

    assertThat(
            new TransactionReplacementByGasPriceRule(Percentage.fromInt(priceBump))
                .shouldReplace(oldTx, newTx, baseFee))
        .isEqualTo(expected);
  }
}
