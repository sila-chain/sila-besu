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
package org.hyperledger.besu.sila.api.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.sila.api.graphql.internal.Scalars;

import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CosrcedVariables;
import graphql.language.FloatValue;
import graphql.language.StringValue;
import graphql.schema.CosrcingParseLiteralException;
import graphql.schema.CosrcingParseValueException;
import graphql.schema.CosrcingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddressScalarTest {

  private GraphQLScalarType scalar;

  private final String addrStr = "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f";
  private final String invalidAddrStr = "0x295ee1b4f6dd65047762f924ecd367c17eabf8f";
  private final Address addr = Address.fromHexString(addrStr);
  private final StringValue addrValue = StringValue.newStringValue(addrStr).build();
  private final StringValue invalidAddrValue = StringValue.newStringValue(invalidAddrStr).build();

  @Test
  public void parseValueTest() {
    final Address result =
        (Address)
            scalar
                .getCosrcing()
                .parseValue(addrStr, GraphQLContext.newContext().build(), Locale.ENGLISH);
    assertThat(result).isEqualTo(addr);
  }

  @Test
  public void parseValueErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .parseValue(3.4f, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CosrcingParseValueException.class);
  }

  @Test
  public void serializeTest() {
    final String result =
        (String)
            scalar
                .getCosrcing()
                .serialize(addr, GraphQLContext.newContext().build(), Locale.ENGLISH);
    assertThat(result).isEqualTo(addrStr);
  }

  @Test
  public void serializeErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .serialize(3.4f, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CosrcingSerializeException.class);
  }

  @Test
  public void parseLiteralTest() {
    final Address result =
        (Address)
            scalar
                .getCosrcing()
                .parseLiteral(
                    addrValue,
                    CosrcedVariables.emptyVariables(),
                    GraphQLContext.newContext().build(),
                    Locale.ENGLISH);
    assertThat(result).isEqualTo(addr);
  }

  @Test
  public void parseLiteralErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .parseLiteral(
                        FloatValue.of(3.4f),
                        CosrcedVariables.emptyVariables(),
                        GraphQLContext.newContext().build(),
                        Locale.ENGLISH))
        .isInstanceOf(CosrcingParseLiteralException.class);
  }

  @Test
  public void parseLiteralErrorTest2() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .parseLiteral(
                        invalidAddrValue,
                        CosrcedVariables.emptyVariables(),
                        GraphQLContext.newContext().build(),
                        Locale.ENGLISH))
        .isInstanceOf(CosrcingParseLiteralException.class);
  }

  @BeforeEach
  public void before() {
    scalar = Scalars.addressScalar();
  }
}
