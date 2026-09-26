/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.sila.core.feemarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.TransactionType.ACCESS_LIST;
import static org.hyperledger.besu.datatypes.TransactionType.FRONTIER;
import static org.hyperledger.besu.datatypes.TransactionType.SIP1559;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.core.Transaction;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TransactionPriceCalculatorTest {

  private static final TransactionPriceCalculator FRONTIER_CALCULATOR =
      TransactionPriceCalculator.frontier();
  private static final TransactionPriceCalculator SIP_1559_CALCULATOR =
      TransactionPriceCalculator.sip1559();

  public static Stream<Arguments> data() {
    return Arrays.stream(
            new Object[][] {
              // legacy transaction must return gas price
              {
                FRONTIER_CALCULATOR,
                FRONTIER,
                Wei.of(578L),
                null,
                null,
                Optional.empty(),
                Wei.of(578L)
              },
              // legacy transaction zero price
              {FRONTIER_CALCULATOR, FRONTIER, Wei.ZERO, null, null, Optional.empty(), Wei.ZERO},
              // ACCESSLIST transaction must return gas price
              {
                FRONTIER_CALCULATOR,
                ACCESS_LIST,
                Wei.of(578L),
                null,
                null,
                Optional.empty(),
                Wei.of(578L)
              },
              // legacy transaction must return gas price
              {
                SIP_1559_CALCULATOR,
                FRONTIER,
                Wei.of(578L),
                null,
                null,
                Optional.of(Wei.of(150L)),
                Wei.of(578L)
              },
              // london legacy transaction zero price
              {
                SIP_1559_CALCULATOR, FRONTIER, Wei.ZERO, null, null, Optional.of(Wei.ZERO), Wei.ZERO
              },
              // ACCESSLIST transaction must return gas price
              {
                SIP_1559_CALCULATOR,
                ACCESS_LIST,
                Wei.of(578L),
                null,
                null,
                Optional.of(Wei.of(150L)),
                Wei.of(578L)
              },
              // SIP-1559 must return maxPriorityFeePerGas + base fee
              {
                SIP_1559_CALCULATOR,
                SIP1559,
                null,
                Wei.of(100L),
                Wei.of(300L),
                Optional.of(Wei.of(150L)),
                Wei.of(250L)
              },
              // SIP-1559 must return fee cap
              {
                SIP_1559_CALCULATOR,
                SIP1559,
                null,
                Wei.of(100L),
                Wei.of(300L),
                Optional.of(Wei.of(250L)),
                Wei.of(300L)
              },
              // SIP-1559 transaction zero price
              {
                SIP_1559_CALCULATOR,
                SIP1559,
                null,
                Wei.ZERO,
                Wei.ZERO,
                Optional.of(Wei.ZERO),
                Wei.ZERO
              }
            })
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void assertThatCalculatorWorks(
      final TransactionPriceCalculator transactionPriceCalculator,
      final TransactionType transactionType,
      final Wei gasPrice,
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final Optional<Wei> baseFee,
      final Wei expectedPrice) {
    assertThat(
            transactionPriceCalculator.price(
                Transaction.builder()
                    .type(transactionType)
                    .accessList(transactionType == ACCESS_LIST ? Collections.emptyList() : null)
                    .gasPrice(gasPrice)
                    .maxPriorityFeePerGas(maxPriorityFeePerGas)
                    .maxFeePerGas(maxFeePerGas)
                    .chainId(BigInteger.ONE)
                    .build(),
                baseFee))
        .isEqualByComparingTo(expectedPrice);
  }
}
