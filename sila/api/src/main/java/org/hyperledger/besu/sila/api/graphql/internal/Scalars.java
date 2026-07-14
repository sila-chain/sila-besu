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
package org.hyperledger.besu.sila.api.graphql.internal;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.Quantity;

import java.math.BigInteger;
import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CosrcedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Cosrcing;
import graphql.schema.CosrcingParseLiteralException;
import graphql.schema.CosrcingParseValueException;
import graphql.schema.CosrcingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The Scalars class provides methods for creating GraphQLScalarType objects. These objects
 * represent the scalar types used in GraphQL, such as Address, BigInt, Bytes, Bytes32, and Long.
 * Each method in this class returns a GraphQLScalarType object that has been configured with a
 * specific Cosrcing implementation. The Cosrcing implementation defines how that type is
 * serialized, deserialized and validated.
 */
public class Scalars {

  private Scalars() {}

  private static final Cosrcing<Address, String> ADDRESS_COSRCING =
      new Cosrcing<>() {
        private Address convertImpl(final Object input) {
          if (input instanceof Address address) {
            return address;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            try {
              return Address.fromHexStringStrict(string);
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          Address result = convertImpl(input);
          if (result != null) {
            return result.getBytes().toHexString();
          } else {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as an Address");
          }
        }

        @Override
        public Address parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          Address result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as an Address");
          }
        }

        @Override
        public Address parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          Address result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseLiteralException("Value is not any Address : '" + input + "'");
          }
        }
      };

  private static final Cosrcing<LogTopic, String> LOG_TOPIC_COSRCING =
      new Cosrcing<>() {

        private LogTopic convertImpl(final Object input) {
          if (input instanceof LogTopic logTopic) {
            return logTopic;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CosrcingParseLiteralException(
                  "LogTopic value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return LogTopic.wrap(Bytes32.fromHexStringLenient(string));
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as a LogTopic");
          } else {
            return result.getBytes().toHexString();
          }
        }

        @Override
        public LogTopic parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as a LogTopic");
          } else {
            return result;
          }
        }

        @Override
        public LogTopic parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseLiteralException("Value is not any LogTopic : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Cosrcing<VersionedHash, String> VERSIONED_HASH_COSRCING =
      new Cosrcing<>() {
        private VersionedHash convertImpl(final Object input) {
          if (input instanceof VersionedHash versionedHash) {
            return versionedHash;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CosrcingParseLiteralException(
                  "VersionedHash value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return new VersionedHash(Bytes32.fromHexStringLenient(string));
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingSerializeException(
                "Unable to serialize " + input + " as a VersionedHash");
          } else {
            return result.getBytes().toHexString();
          }
        }

        @Override
        public VersionedHash parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as a VersionedHash");
          } else {
            return result;
          }
        }

        @Override
        public VersionedHash parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseLiteralException(
                "Value is not any VersionedHash : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Cosrcing<String, String> BIG_INT_COSRCING =
      new Cosrcing<>() {
        private String convertImpl(final Object input) {
          if (input instanceof String string) {
            try {
              return Bytes.fromHexStringLenient(string).toShortHexString();
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else if (input instanceof Bytes bytes) {
            return bytes.toShortHexString();
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof IntValue intValue) {
            return UInt256.valueOf(intValue.getValue()).toShortHexString();
          } else if (input instanceof BigInteger bigInteger) {
            return "0x" + bigInteger.toString(16);
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as an BigInt");
          }
        }

        @Override
        public String parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as an BigInt");
          }
        }

        @Override
        public String parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseLiteralException("Value is not any BigInt : '" + input + "'");
          }
        }
      };

  private static final Cosrcing<Bytes, String> BYTES_COSRCING =
      new Cosrcing<>() {
        private Bytes convertImpl(final Object input) {
          if (input instanceof Bytes bytes) {
            return bytes;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CosrcingParseLiteralException(
                  "Bytes value '" + input + "' is not prefixed with 0x");
            }
            try {
              return Bytes.fromHexStringLenient((String) input);
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result.toHexString();
          } else {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CosrcingParseLiteralException("Value is not any Bytes : '" + input + "'");
          }
        }
      };

  private static final Cosrcing<Hash, String> HASH_COSRCING =
      new Cosrcing<>() {
        private Hash convertImpl(final Object input) {
          if (input instanceof Hash hash) {
            return hash;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CosrcingParseLiteralException(
                  "Hash value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return Hash.wrap(Bytes32.fromHexStringLenient(string));
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as a Hash");
          } else {
            return result.getBytes().toHexString();
          }
        }

        @Override
        public Hash parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as a Hash");
          } else {
            return result;
          }
        }

        @Override
        public Hash parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseLiteralException("Value is not any Hash : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Cosrcing<Bytes32, String> BYTES32_COSRCING =
      new Cosrcing<>() {
        private Bytes32 convertImpl(final Object input) {
          if (input instanceof Bytes32 bytes32) {
            return bytes32;
          } else if (input instanceof Bytes bytes) {
            if (bytes.size() <= 32) {
              return Bytes32.leftPad((Bytes) input);
            } else {
              return null;
            }
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CosrcingParseLiteralException(
                  "Bytes32 value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return Bytes32.fromHexStringLenient((String) input);
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingSerializeException("Unable to serialize " + input + " as an Bytes32");
          } else {
            return result.toHexString();
          }
        }

        @Override
        public Bytes32 parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes32");
          } else {
            return result;
          }
        }

        @Override
        public Bytes32 parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CosrcingParseLiteralException("Value is not any Bytes32 : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Cosrcing<Number, String> LONG_COSRCING =
      new Cosrcing<>() {
        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingSerializeException {
          if (input instanceof Number number) {
            return Bytes.ofUnsignedLong(number.longValue()).toQuantityHexString();
          } else if (input instanceof String string) {
            if (string.startsWith("0x")) {
              return string;
            } else {
              return "0x" + string;
            }
          }
          throw new CosrcingSerializeException("Unable to serialize " + input + " as an Long");
        }

        @Override
        public Number parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CosrcingParseValueException {
          if (input instanceof Number number) {
            return number;
          } else if (input instanceof String string) {
            final String value = string.toLowerCase(Locale.ROOT);
            if (value.startsWith("0x")) {
              return Bytes.fromHexStringLenient(value).toLong();
            } else {
              return Long.parseLong(value);
            }
          }
          throw new CosrcingParseValueException(
              "Unable to parse variable value " + input + " as an Long");
        }

        @Override
        public Number parseLiteral(
            final Value<?> input,
            final CosrcedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CosrcingParseLiteralException {
          try {
            if (input instanceof IntValue intValue) {
              return intValue.getValue().longValue();
            } else if (input instanceof StringValue stringValue) {
              final String value = stringValue.getValue().toLowerCase(Locale.ROOT);
              if (value.startsWith("0x")) {
                return Bytes.fromHexStringLenient(value).toLong();
              } else {
                return Long.parseLong(value);
              }
            }
          } catch (final NumberFormatException e) {
            // fall through
          }
          throw new CosrcingParseLiteralException("Value is not any Long : '" + input + "'");
        }
      };

  /**
   * Creates a new GraphQLScalarType object for an Address.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the
   * Address type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for an Address.
   */
  public static GraphQLScalarType addressScalar() {
    return GraphQLScalarType.newScalar()
        .name("Address")
        .description("Address scalar")
        .cosrcing(ADDRESS_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a BigInt.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the BigInt
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a BigInt.
   */
  public static GraphQLScalarType bigIntScalar() {
    return GraphQLScalarType.newScalar()
        .name("BigInt")
        .description("A BigInt (UInt256) scalar")
        .cosrcing(BIG_INT_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for Bytes.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the Bytes
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for Bytes.
   */
  public static GraphQLScalarType bytesScalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes")
        .description("A Bytes scalar")
        .cosrcing(BYTES_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for Bytes32.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the
   * Bytes32 type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for Bytes32.
   */
  public static GraphQLScalarType bytes32Scalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes32")
        .description("A Bytes32 scalar")
        .cosrcing(BYTES32_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a Long.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the Long
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a Long.
   */
  public static GraphQLScalarType longScalar() {
    return GraphQLScalarType.newScalar()
        .name("Long")
        .description("A Long (UInt64) scalar")
        .cosrcing(LONG_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a Hash.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the Hash
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a Hash.
   */
  public static GraphQLScalarType hashScalar() {
    return GraphQLScalarType.newScalar()
        .name("Hash")
        .description("A Hash (32 byte keccak256 hash) scalar")
        .cosrcing(HASH_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a LogTopic.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the
   * LogTopic type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a LogTopic.
   */
  public static GraphQLScalarType logTopicScalar() {
    return GraphQLScalarType.newScalar()
        .name("LogTopic")
        .description("A LogTopic (32 byte log topic) scalar")
        .cosrcing(LOG_TOPIC_COSRCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a VersionedHash.
   *
   * <p>The object is configured with a specific Cosrcing implementation that defines how the
   * VersionedHash type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a VersionedHash.
   */
  public static GraphQLScalarType versionedHashScalar() {
    return GraphQLScalarType.newScalar()
        .name("VersionedHash")
        .description("A VersionedHash (32 byte versioned hash) scalar")
        .cosrcing(VERSIONED_HASH_COSRCING)
        .build();
  }
}
