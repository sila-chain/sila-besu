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

import org.hyperledger.besu.sila.api.graphql.internal.Scalars;

import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CosrcedVariables;
import graphql.language.StringValue;
import graphql.schema.CosrcingParseLiteralException;
import graphql.schema.CosrcingParseValueException;
import graphql.schema.CosrcingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LongScalarTest {

  private GraphQLScalarType scalar;

  private final String str = "0xf4240";
  private final Long value = Long.decode(str);
  private final StringValue strValue = StringValue.newStringValue(str).build();
  private final StringValue invalidStrValue = StringValue.newStringValue("gh").build();

  @Test
  public void parseLongValueTest() {
    assertThat(
            scalar
                .getCosrcing()
                .parseValue(value, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isEqualTo(value);
  }

  @Test
  public void parseStringValueTest() {
    assertThat(
            scalar
                .getCosrcing()
                .parseValue(str, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isEqualTo(value);
  }

  @Test
  public void parseValueErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .parseValue(
                        invalidStrValue, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CosrcingParseValueException.class);
  }

  @Test
  public void serializeLongTest() {
    assertThat(
            scalar
                .getCosrcing()
                .serialize(value, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isEqualTo(str);
  }

  @Test
  public void serializeStringTest() {
    assertThat(
            scalar
                .getCosrcing()
                .serialize(str, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isEqualTo(str);
  }

  @Test
  public void serializeErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .serialize(
                        invalidStrValue, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CosrcingSerializeException.class);
  }

  @Test
  public void parseLiteralTest() {
    final Long result =
        (Long)
            scalar
                .getCosrcing()
                .parseLiteral(
                    strValue,
                    CosrcedVariables.emptyVariables(),
                    GraphQLContext.newContext().build(),
                    Locale.ENGLISH);
    assertThat(result).isEqualTo(value);
  }

  @Test
  public void parseLiteralStringTest() {
    final Long result =
        (Long)
            scalar
                .getCosrcing()
                .parseLiteral(
                    strValue,
                    CosrcedVariables.emptyVariables(),
                    GraphQLContext.newContext().build(),
                    Locale.ENGLISH);
    assertThat(result).isEqualTo(value);
  }

  @Test
  public void parseLiteralErrorTest2() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCosrcing()
                    .parseLiteral(
                        invalidStrValue,
                        CosrcedVariables.emptyVariables(),
                        GraphQLContext.newContext().build(),
                        Locale.ENGLISH))
        .isInstanceOf(CosrcingParseLiteralException.class);
  }

  @BeforeEach
  public void before() {
    scalar = Scalars.longScalar();
  }
}
