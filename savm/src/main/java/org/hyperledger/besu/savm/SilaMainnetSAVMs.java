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
package org.hyperledger.besu.savm;

import org.hyperledger.besu.savm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.savm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaCancunGasCalculator;
import org.hyperledger.besu.savm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.savm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.gascalculator.HomesteadGasCalculator;
import org.hyperledger.besu.savm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.savm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaOsakaGasCalculator;
import org.hyperledger.besu.savm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaPragueGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaShanghaiGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.savm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.log.SIP7708TransferLogEmitter;
import org.hyperledger.besu.savm.operation.AddModOperation;
import org.hyperledger.besu.savm.operation.AddModOperationOptimized;
import org.hyperledger.besu.savm.operation.AddOperation;
import org.hyperledger.besu.savm.operation.AddOperationOptimized;
import org.hyperledger.besu.savm.operation.AddressOperation;
import org.hyperledger.besu.savm.operation.AndOperation;
import org.hyperledger.besu.savm.operation.AndOperationOptimized;
import org.hyperledger.besu.savm.operation.BalanceOperation;
import org.hyperledger.besu.savm.operation.BaseFeeOperation;
import org.hyperledger.besu.savm.operation.BlobBaseFeeOperation;
import org.hyperledger.besu.savm.operation.BlobHashOperation;
import org.hyperledger.besu.savm.operation.BlockHashOperation;
import org.hyperledger.besu.savm.operation.ByteOperation;
import org.hyperledger.besu.savm.operation.CallCodeOperation;
import org.hyperledger.besu.savm.operation.CallDataCopyOperation;
import org.hyperledger.besu.savm.operation.CallDataLoadOperation;
import org.hyperledger.besu.savm.operation.CallDataSizeOperation;
import org.hyperledger.besu.savm.operation.CallOperation;
import org.hyperledger.besu.savm.operation.CallValueOperation;
import org.hyperledger.besu.savm.operation.CallerOperation;
import org.hyperledger.besu.savm.operation.ChainIdOperation;
import org.hyperledger.besu.savm.operation.CodeCopyOperation;
import org.hyperledger.besu.savm.operation.CodeSizeOperation;
import org.hyperledger.besu.savm.operation.CoinbaseOperation;
import org.hyperledger.besu.savm.operation.CountLeadingZerosOperation;
import org.hyperledger.besu.savm.operation.Create2Operation;
import org.hyperledger.besu.savm.operation.CreateOperation;
import org.hyperledger.besu.savm.operation.DelegateCallOperation;
import org.hyperledger.besu.savm.operation.DifficultyOperation;
import org.hyperledger.besu.savm.operation.DivOperation;
import org.hyperledger.besu.savm.operation.DivOperationOptimized;
import org.hyperledger.besu.savm.operation.DupNOperation;
import org.hyperledger.besu.savm.operation.DupOperation;
import org.hyperledger.besu.savm.operation.EqOperation;
import org.hyperledger.besu.savm.operation.ExchangeOperation;
import org.hyperledger.besu.savm.operation.ExpOperation;
import org.hyperledger.besu.savm.operation.ExtCodeCopyOperation;
import org.hyperledger.besu.savm.operation.ExtCodeHashOperation;
import org.hyperledger.besu.savm.operation.ExtCodeSizeOperation;
import org.hyperledger.besu.savm.operation.GasLimitOperation;
import org.hyperledger.besu.savm.operation.GasOperation;
import org.hyperledger.besu.savm.operation.GasPriceOperation;
import org.hyperledger.besu.savm.operation.GtOperation;
import org.hyperledger.besu.savm.operation.InvalidOperation;
import org.hyperledger.besu.savm.operation.IsZeroOperation;
import org.hyperledger.besu.savm.operation.JumpDestOperation;
import org.hyperledger.besu.savm.operation.JumpOperation;
import org.hyperledger.besu.savm.operation.JumpiOperation;
import org.hyperledger.besu.savm.operation.Keccak256Operation;
import org.hyperledger.besu.savm.operation.LogOperation;
import org.hyperledger.besu.savm.operation.LtOperation;
import org.hyperledger.besu.savm.operation.MCopyOperation;
import org.hyperledger.besu.savm.operation.MLoadOperation;
import org.hyperledger.besu.savm.operation.MSizeOperation;
import org.hyperledger.besu.savm.operation.MStore8Operation;
import org.hyperledger.besu.savm.operation.MStoreOperation;
import org.hyperledger.besu.savm.operation.ModOperation;
import org.hyperledger.besu.savm.operation.ModOperationOptimized;
import org.hyperledger.besu.savm.operation.MulModOperation;
import org.hyperledger.besu.savm.operation.MulModOperationOptimized;
import org.hyperledger.besu.savm.operation.MulOperation;
import org.hyperledger.besu.savm.operation.NotOperation;
import org.hyperledger.besu.savm.operation.NotOperationOptimized;
import org.hyperledger.besu.savm.operation.NumberOperation;
import org.hyperledger.besu.savm.operation.OperationRegistry;
import org.hyperledger.besu.savm.operation.OrOperation;
import org.hyperledger.besu.savm.operation.OrOperationOptimized;
import org.hyperledger.besu.savm.operation.OriginOperation;
import org.hyperledger.besu.savm.operation.PCOperation;
import org.hyperledger.besu.savm.operation.PayOperation;
import org.hyperledger.besu.savm.operation.PopOperation;
import org.hyperledger.besu.savm.operation.PrevRanDaoOperation;
import org.hyperledger.besu.savm.operation.Push0Operation;
import org.hyperledger.besu.savm.operation.PushOperation;
import org.hyperledger.besu.savm.operation.ReturnDataCopyOperation;
import org.hyperledger.besu.savm.operation.ReturnDataSizeOperation;
import org.hyperledger.besu.savm.operation.ReturnOperation;
import org.hyperledger.besu.savm.operation.RevertOperation;
import org.hyperledger.besu.savm.operation.SDivOperation;
import org.hyperledger.besu.savm.operation.SDivOperationOptimized;
import org.hyperledger.besu.savm.operation.SGtOperation;
import org.hyperledger.besu.savm.operation.SLoadOperation;
import org.hyperledger.besu.savm.operation.SLtOperation;
import org.hyperledger.besu.savm.operation.SModOperation;
import org.hyperledger.besu.savm.operation.SModOperationOptimized;
import org.hyperledger.besu.savm.operation.SStoreOperation;
import org.hyperledger.besu.savm.operation.SarOperation;
import org.hyperledger.besu.savm.operation.SarOperationOptimized;
import org.hyperledger.besu.savm.operation.SelfBalanceOperation;
import org.hyperledger.besu.savm.operation.SelfDestructOperation;
import org.hyperledger.besu.savm.operation.ShlOperation;
import org.hyperledger.besu.savm.operation.ShlOperationOptimized;
import org.hyperledger.besu.savm.operation.ShrOperation;
import org.hyperledger.besu.savm.operation.ShrOperationOptimized;
import org.hyperledger.besu.savm.operation.SignExtendOperation;
import org.hyperledger.besu.savm.operation.SlotNumOperation;
import org.hyperledger.besu.savm.operation.StaticCallOperation;
import org.hyperledger.besu.savm.operation.StopOperation;
import org.hyperledger.besu.savm.operation.SubOperation;
import org.hyperledger.besu.savm.operation.SwapNOperation;
import org.hyperledger.besu.savm.operation.SwapOperation;
import org.hyperledger.besu.savm.operation.TLoadOperation;
import org.hyperledger.besu.savm.operation.TStoreOperation;
import org.hyperledger.besu.savm.operation.TimestampOperation;
import org.hyperledger.besu.savm.operation.XorOperation;
import org.hyperledger.besu.savm.operation.XorOperationOptimized;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Provides SAVMs supporting the appropriate operations for sila-mainnet hard forks. */
public class SilaMainnetSAVMs {

  /** The constant DEV_NET_CHAIN_ID. */
  public static final BigInteger DEV_NET_CHAIN_ID = BigInteger.valueOf(1337);

  private SilaMainnetSAVMs() {
    // utility class
  }

  /**
   * Frontier savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM frontier(final SavmConfiguration savmConfiguration) {
    return frontier(new FrontierGasCalculator(), savmConfiguration);
  }

  /**
   * Frontier savm.
   *
   * @param gasCalculator the gas calculator
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM frontier(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    return new SAVM(
        frontierOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.FRONTIER);
  }

  /**
   * Operation registry for frontier's operations.
   *
   * @param gasCalculator the gas calculator
   * @return the operation registry
   */
  private static OperationRegistry frontierOperations(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerFrontierOperations(operationRegistry, gasCalculator, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register frontier operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  private static void registerFrontierOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration) {
    for (int i = 0; i < 255; i++) {
      registry.put(new InvalidOperation(i, gasCalculator));
    }
    registry.put(new MulOperation(gasCalculator));
    registry.put(new SubOperation(gasCalculator));
    if (savmConfiguration.enableOptimizedOpcodes()) {
      registry.put(new AddOperationOptimized(gasCalculator));
      registry.put(new ModOperationOptimized(gasCalculator));
      registry.put(new SModOperationOptimized(gasCalculator));
      registry.put(new AddModOperationOptimized(gasCalculator));
      registry.put(new MulModOperationOptimized(gasCalculator));
      registry.put(new AndOperationOptimized(gasCalculator));
      registry.put(new XorOperationOptimized(gasCalculator));
      registry.put(new OrOperationOptimized(gasCalculator));
      registry.put(new NotOperationOptimized(gasCalculator));
      registry.put(new DivOperationOptimized(gasCalculator));
      registry.put(new SDivOperationOptimized(gasCalculator));
    } else {
      registry.put(new AddOperation(gasCalculator));
      registry.put(new ModOperation(gasCalculator));
      registry.put(new SModOperation(gasCalculator));
      registry.put(new AddModOperation(gasCalculator));
      registry.put(new MulModOperation(gasCalculator));
      registry.put(new AndOperation(gasCalculator));
      registry.put(new XorOperation(gasCalculator));
      registry.put(new OrOperation(gasCalculator));
      registry.put(new NotOperation(gasCalculator));
      registry.put(new DivOperation(gasCalculator));
      registry.put(new SDivOperation(gasCalculator));
    }
    registry.put(new ExpOperation(gasCalculator));
    registry.put(new SignExtendOperation(gasCalculator));
    registry.put(new LtOperation(gasCalculator));
    registry.put(new GtOperation(gasCalculator));
    registry.put(new SLtOperation(gasCalculator));
    registry.put(new SGtOperation(gasCalculator));
    registry.put(new EqOperation(gasCalculator));
    registry.put(new IsZeroOperation(gasCalculator));
    registry.put(new ByteOperation(gasCalculator));
    registry.put(new Keccak256Operation(gasCalculator));
    registry.put(new AddressOperation(gasCalculator));
    registry.put(new BalanceOperation(gasCalculator));
    registry.put(new OriginOperation(gasCalculator));
    registry.put(new CallerOperation(gasCalculator));
    registry.put(new CallValueOperation(gasCalculator));
    registry.put(new CallDataLoadOperation(gasCalculator));
    registry.put(new CallDataSizeOperation(gasCalculator));
    registry.put(new CallDataCopyOperation(gasCalculator));
    registry.put(new CodeSizeOperation(gasCalculator));
    registry.put(new CodeCopyOperation(gasCalculator));
    registry.put(new GasPriceOperation(gasCalculator));
    registry.put(new ExtCodeCopyOperation(gasCalculator));
    registry.put(new ExtCodeSizeOperation(gasCalculator));
    registry.put(new BlockHashOperation(gasCalculator));
    registry.put(new CoinbaseOperation(gasCalculator));
    registry.put(new TimestampOperation(gasCalculator));
    registry.put(new NumberOperation(gasCalculator));
    registry.put(new DifficultyOperation(gasCalculator));
    registry.put(new GasLimitOperation(gasCalculator));
    registry.put(new PopOperation(gasCalculator));
    registry.put(new MLoadOperation(gasCalculator));
    registry.put(new MStoreOperation(gasCalculator));
    registry.put(new MStore8Operation(gasCalculator));
    registry.put(new SLoadOperation(gasCalculator));
    registry.put(new SStoreOperation(gasCalculator, SStoreOperation.FRONTIER_MINIMUM));
    registry.put(new JumpOperation(gasCalculator));
    registry.put(new JumpiOperation(gasCalculator));
    registry.put(new PCOperation(gasCalculator));
    registry.put(new MSizeOperation(gasCalculator));
    registry.put(new GasOperation(gasCalculator));
    registry.put(new JumpDestOperation(gasCalculator));
    registry.put(new ReturnOperation(gasCalculator));
    registry.put(new InvalidOperation(gasCalculator));
    registry.put(new StopOperation(gasCalculator));
    registry.put(new SelfDestructOperation(gasCalculator));
    registry.put(new CreateOperation(gasCalculator));
    registry.put(new CallOperation(gasCalculator));
    registry.put(new CallCodeOperation(gasCalculator));

    // Register the PUSH1, PUSH2, ..., PUSH32 operations.
    for (int i = 1; i <= 32; ++i) {
      registry.put(new PushOperation(i, gasCalculator));
    }

    // Register the DUP1, DUP2, ..., DUP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new DupOperation(i, gasCalculator));
    }

    // Register the SWAP1, SWAP2, ..., SWAP16 operations.
    for (int i = 1; i <= 16; ++i) {
      registry.put(new SwapOperation(i, gasCalculator));
    }

    // Register the LOG0, LOG1, ..., LOG4 operations.
    for (int i = 0; i < 5; ++i) {
      registry.put(new LogOperation(i, gasCalculator));
    }
  }

  /**
   * Homestead savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM homestead(final SavmConfiguration savmConfiguration) {
    return homestead(new HomesteadGasCalculator(), savmConfiguration);
  }

  /**
   * Homestead savm.
   *
   * @param gasCalculator the gas calculator
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM homestead(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    return new SAVM(
        homesteadOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.HOMESTEAD);
  }

  /**
   * Operation registry for homestead's operations.
   *
   * @param gasCalculator the gas calculator
   * @return the operation registry
   */
  private static OperationRegistry homesteadOperations(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerHomesteadOperations(operationRegistry, gasCalculator, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register homestead operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  private static void registerHomesteadOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration) {
    registerFrontierOperations(registry, gasCalculator, savmConfiguration);
    registry.put(new DelegateCallOperation(gasCalculator));
  }

  /**
   * Spurious dragon savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM spuriousDragon(final SavmConfiguration savmConfiguration) {
    GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
    return new SAVM(
        homesteadOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.SPURIOUS_DRAGON);
  }

  /**
   * Tangerine whistle savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM tangerineWhistle(final SavmConfiguration savmConfiguration) {
    GasCalculator gasCalculator = new TangerineWhistleGasCalculator();
    return new SAVM(
        homesteadOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.TANGERINE_WHISTLE);
  }

  /**
   * Byzantium savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM byzantium(final SavmConfiguration savmConfiguration) {
    return byzantium(new ByzantiumGasCalculator(), savmConfiguration);
  }

  /**
   * Byzantium savm.
   *
   * @param gasCalculator the gas calculator
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM byzantium(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    return new SAVM(
        byzantiumOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.BYZANTIUM);
  }

  /**
   * Operation registry for byzantium's operations.
   *
   * @param gasCalculator the gas calculator
   * @return the operation registry
   */
  private static OperationRegistry byzantiumOperations(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerByzantiumOperations(operationRegistry, gasCalculator, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register byzantium operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  private static void registerByzantiumOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration) {
    registerHomesteadOperations(registry, gasCalculator, savmConfiguration);
    registry.put(new ReturnDataCopyOperation(gasCalculator));
    registry.put(new ReturnDataSizeOperation(gasCalculator));
    registry.put(new RevertOperation(gasCalculator));
    registry.put(new StaticCallOperation(gasCalculator));
  }

  /**
   * Constantinople savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM constantinople(final SavmConfiguration savmConfiguration) {
    return constantinople(new ConstantinopleGasCalculator(), savmConfiguration);
  }

  /**
   * Constantinople savm.
   *
   * @param gasCalculator the gas calculator
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM constantinople(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    var version = SavmSpecVersion.CONSTANTINOPLE;
    return constantiNOPEl(gasCalculator, savmConfiguration, version);
  }

  private static SAVM constantiNOPEl(
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration,
      final SavmSpecVersion version) {
    return new SAVM(
        constantinopleOperations(gasCalculator, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        version);
  }

  /**
   * Operation registry for constantinople's operations.
   *
   * @param gasCalculator the gas calculator
   * @return the operation registry
   */
  private static OperationRegistry constantinopleOperations(
      final GasCalculator gasCalculator, final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerConstantinopleOperations(operationRegistry, gasCalculator, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register constantinople operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  private static void registerConstantinopleOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration) {
    registerByzantiumOperations(registry, gasCalculator, savmConfiguration);
    registry.put(new Create2Operation(gasCalculator));
    if (savmConfiguration.enableOptimizedOpcodes()) {
      registry.put(new ShlOperationOptimized(gasCalculator));
      registry.put(new ShrOperationOptimized(gasCalculator));
      registry.put(new SarOperationOptimized(gasCalculator));
    } else {
      registry.put(new ShlOperation(gasCalculator));
      registry.put(new ShrOperation(gasCalculator));
      registry.put(new SarOperation(gasCalculator));
    }
    registry.put(new ExtCodeHashOperation(gasCalculator));
  }

  /**
   * Petersburg savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM petersburg(final SavmConfiguration savmConfiguration) {
    return constantiNOPEl(
        new PetersburgGasCalculator(), savmConfiguration, SavmSpecVersion.PETERSBURG);
  }

  /**
   * Istanbul savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM istanbul(final SavmConfiguration savmConfiguration) {
    return istanbul(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Istanbul savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM istanbul(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return istanbul(new IstanbulGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Istanbul savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM istanbul(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        istanbulOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.ISTANBUL);
  }

  /**
   * Operation registry for istanbul's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry istanbulOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerIstanbulOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register istanbul operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   */
  static void registerIstanbulOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    registerConstantinopleOperations(registry, gasCalculator, savmConfiguration);
    registry.put(
        new ChainIdOperation(gasCalculator, Bytes32.leftPad(Bytes.of(chainId.toByteArray()))));
    registry.put(new SelfBalanceOperation(gasCalculator));
    registry.put(new SStoreOperation(gasCalculator, SStoreOperation.SIP_1706_MINIMUM));
  }

  /**
   * Berlin savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM berlin(final SavmConfiguration savmConfiguration) {
    return berlin(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Berlin savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM berlin(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return berlin(new BerlinGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Berlin savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM berlin(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        istanbulOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.BERLIN);
  }

  /**
   * London savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM london(final SavmConfiguration savmConfiguration) {
    return london(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * London savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM london(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return london(new LondonGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * London savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM london(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        londonOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.LONDON);
  }

  /**
   * Operation registry for london's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry londonOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerLondonOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register london operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   */
  private static void registerLondonOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    registerIstanbulOperations(registry, gasCalculator, chainId, savmConfiguration);
    registry.put(new BaseFeeOperation(gasCalculator));
  }

  /**
   * SilaParis savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM paris(final SavmConfiguration savmConfiguration) {
    return paris(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * SilaParis savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM paris(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return paris(new LondonGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * SilaParis savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM paris(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        parisOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.PARIS);
  }

  /**
   * Operation registry for paris's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry parisOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaParisOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register paris operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaParisOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerLondonOperations(registry, gasCalculator, chainID, savmConfiguration);
    registry.put(new PrevRanDaoOperation(gasCalculator));
  }

  /**
   * SilaShanghai savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM shanghai(final SavmConfiguration savmConfiguration) {
    return shanghai(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * SilaShanghai savm
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM shanghai(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return shanghai(new SilaShanghaiGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * shanghai savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM shanghai(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        shanghaiOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.SHANGHAI);
  }

  /**
   * shanghai operations registry.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry shanghaiOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaShanghaiOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register SilaShanghai operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaShanghaiOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaParisOperations(registry, gasCalculator, chainID, savmConfiguration);
    registry.put(new Push0Operation(gasCalculator));
  }

  /**
   * SilaCancun savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM cancun(final SavmConfiguration savmConfiguration) {
    return cancun(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * SilaCancun savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM cancun(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return cancun(new SilaCancunGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * SilaCancun savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM cancun(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        cancunOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.CANCUN);
  }

  /**
   * Operation registry for cancun's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry cancunOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaCancunOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register cancun operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaCancunOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaShanghaiOperations(registry, gasCalculator, chainID, savmConfiguration);

    // SIP-1153 TSTORE/TLOAD
    registry.put(new TStoreOperation(gasCalculator));
    registry.put(new TLoadOperation(gasCalculator));

    // SIP-4844 BLOBHASH
    registry.put(new BlobHashOperation(gasCalculator));

    // SIP-5656 MCOPY
    registry.put(new MCopyOperation(gasCalculator));

    // SIP-6780 nerf self destruct
    registry.put(new SelfDestructOperation(gasCalculator, true));

    // SIP-7516 BLOBBASEFEE
    registry.put(new BlobBaseFeeOperation(gasCalculator));
  }

  /**
   * SilaPrague savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM prague(final SavmConfiguration savmConfiguration) {
    return prague(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * SilaPrague savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM prague(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return prague(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * SilaPrague savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM prague(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        pragueOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.PRAGUE);
  }

  /**
   * Operation registry for prague's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry pragueOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaPragueOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register prague operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaPragueOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaCancunOperations(registry, gasCalculator, chainID, savmConfiguration);
  }

  /**
   * SilaOsaka savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM osaka(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return osaka(new SilaOsakaGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * SilaOsaka savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM osaka(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        osakaOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.OSAKA);
  }

  /**
   * Operation registry for SilaOsaka's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry osakaOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaOsakaOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register SilaOsaka's operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaOsakaOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaPragueOperations(registry, gasCalculator, chainID, savmConfiguration);

    // SIP-7939: CLZ opcode
    registry.put(new CountLeadingZerosOperation(gasCalculator));
  }

  /**
   * SilaAmsterdam savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM amsterdam(final SavmConfiguration savmConfiguration) {
    return amsterdam(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * SilaAmsterdam savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM amsterdam(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return amsterdam(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * SilaAmsterdam savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM amsterdam(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        amsterdamOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.AMSTERDAM);
  }

  /**
   * Operation registry for amsterdam's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry amsterdamOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerSilaAmsterdamOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register amsterdam operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerSilaAmsterdamOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaOsakaOperations(registry, gasCalculator, chainID, savmConfiguration);

    // SIP-7708: SelfDestruct with transfer log emission
    registry.put(
        new SelfDestructOperation(gasCalculator, true, SIP7708TransferLogEmitter.INSTANCE));

    // SIP-7843 SLOTNUM opcode
    registry.put(new SlotNumOperation(gasCalculator));

    // SIP-8024: DUPN, SWAPN, EXCHANGE
    registry.put(new DupNOperation(gasCalculator));
    registry.put(new SwapNOperation(gasCalculator));
    registry.put(new ExchangeOperation(gasCalculator));
  }

  /**
   * Bogota savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bogota(final SavmConfiguration savmConfiguration) {
    return bogota(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Bogota savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bogota(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return bogota(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Bogota savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bogota(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        bogotaOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.BOGOTA);
  }

  /**
   * Bogota operation registry.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry bogotaOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerBogotaOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register bogota operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerBogotaOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerSilaAmsterdamOperations(registry, gasCalculator, chainID, savmConfiguration);
  }

  /**
   * Polis savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM polis(final SavmConfiguration savmConfiguration) {
    return polis(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Polis savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM polis(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return polis(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Polis savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM polis(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        polisOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.POLIS);
  }

  /**
   * Operation registry for Polis's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry polisOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerPolisOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register polis operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerPolisOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerBogotaOperations(registry, gasCalculator, chainID, savmConfiguration);
  }

  /**
   * Bangkok savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bangkok(final SavmConfiguration savmConfiguration) {
    return bangkok(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Bangkok savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bangkok(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return bangkok(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Bangkok savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM bangkok(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        bangkokOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.BANGKOK);
  }

  /**
   * Operation registry for bangkok's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry bangkokOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerBangkokOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register bangkok operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerBangkokOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerPolisOperations(registry, gasCalculator, chainID, savmConfiguration);
  }

  /**
   * Future sips savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM futureSips(final SavmConfiguration savmConfiguration) {
    return futureSips(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Future sips savm.
   *
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM futureSips(final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return futureSips(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Future sips savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM futureSips(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        futureSipsOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.FUTURE_SIPS);
  }

  /**
   * Future Operation registry for eIPs's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry futureSipsOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerFutureSipsOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register FutureSIPs operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerFutureSipsOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerBogotaOperations(registry, gasCalculator, chainID, savmConfiguration);

    // SIP-5920 PAY opcode
    registry.put(new PayOperation(gasCalculator));
  }

  /**
   * Experimental sips savm.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM experimentalSips(final SavmConfiguration savmConfiguration) {
    return experimentalSips(DEV_NET_CHAIN_ID, savmConfiguration);
  }

  /**
   * Experimental sips savm.
   *
   * @param chainId the chain Id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM experimentalSips(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    return experimentalSips(new SilaPragueGasCalculator(), chainId, savmConfiguration);
  }

  /**
   * Experimental sips savm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param savmConfiguration the savm configuration
   * @return the savm
   */
  public static SAVM experimentalSips(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return new SAVM(
        experimentalSipsOperations(gasCalculator, chainId, savmConfiguration),
        gasCalculator,
        savmConfiguration,
        SavmSpecVersion.EXPERIMENTAL_SIPS);
  }

  /**
   * Operation registry for experimental's operations.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  private static OperationRegistry experimentalSipsOperations(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    OperationRegistry operationRegistry = new OperationRegistry();
    registerExperimentalSipsOperations(operationRegistry, gasCalculator, chainId, savmConfiguration);
    return operationRegistry;
  }

  /**
   * Register experimental sips operations.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   * @param chainID the chain id
   */
  private static void registerExperimentalSipsOperations(
      final OperationRegistry registry,
      final GasCalculator gasCalculator,
      final BigInteger chainID,
      final SavmConfiguration savmConfiguration) {
    registerFutureSipsOperations(registry, gasCalculator, chainID, savmConfiguration);
  }
}
