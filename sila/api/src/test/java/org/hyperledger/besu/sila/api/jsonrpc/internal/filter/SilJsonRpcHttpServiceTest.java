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
package org.hyperledger.besu.sila.api.jsonrpc.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.api.jsonrpc.AbstractJsonRpcHttpServiceTest;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Transaction;

import java.io.IOException;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

public class SilJsonRpcHttpServiceTest extends AbstractJsonRpcHttpServiceTest {

  private Hash recordPendingTransaction(final int blockNumber, final int transactionIndex) {
    final Block block = blockchainSetupUtil.getBlock(blockNumber);
    final Transaction transaction = block.getBody().getTransactions().get(transactionIndex);
    filterManager.recordPendingTransactionEvent(transaction);
    return transaction.getHash();
  }

  @Test
  public void getFilterChanges_noBlocks() throws Exception {
    startService();
    final String expectedRespBody = String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":[]}");
    final ResponseBody body = silNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = silGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_oneBlock() throws Exception {
    BlockchainSetupUtil blockchainSetupUtil = startServiceWithEmptyChain(DataStorageFormat.FOREST);
    final String expectedRespBody =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":[\"0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9\"]}");
    final ResponseBody body = silNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();

    // Import genesis + first block
    blockchainSetupUtil.importFirstBlocks(2);
    final Response resp = silGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_noTransactions() throws Exception {
    startService();
    final String expectedRespBody = String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":[]}");
    final ResponseBody body = silNewPendingTransactionFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = silGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void getFilterChanges_oneTransaction() throws Exception {
    startService();
    final ResponseBody body = silNewPendingTransactionFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Hash transactionHash = recordPendingTransaction(1, 0);

    final Response resp = silGetFilterChanges(2, result);
    assertThat(resp.code()).isEqualTo(200);
    final String expectedRespBody =
        String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":[\"" + transactionHash + "\"]}");
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  @Test
  public void uninstallFilter() throws Exception {
    startService();
    final String expectedRespBody = String.format("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":true}");
    final ResponseBody body = silNewBlockFilter(1).body();
    final String result = getResult(body);
    body.close();
    final Response resp = silUninstallFilter(2, result);
    assertThat(resp.code()).isEqualTo(200);
    assertThat(resp.body().string()).isEqualTo(expectedRespBody);
  }

  private String getResult(final ResponseBody body) throws IOException {
    final JsonObject json = new JsonObject(body.string());
    return json.getString("result");
  }

  private Response jsonRpcRequest(final int id, final String method, final String params)
      throws Exception {
    final RequestBody body =
        RequestBody.create(
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"params\": "
                + params
                + ",\"method\":\""
                + method
                + "\"}",
            JSON);
    final Request request = new Request.Builder().post(body).url(baseUrl).build();
    return client.newCall(request).execute();
  }

  private Response silNewBlockFilter(final int id) throws Exception {
    return jsonRpcRequest(id, "sil_newBlockFilter", "[]");
  }

  private Response silNewPendingTransactionFilter(final int id) throws Exception {
    return jsonRpcRequest(id, "sil_newPendingTransactionFilter", "[]");
  }

  private Response silGetFilterChanges(final int id, final String filterId) throws Exception {
    return jsonRpcRequest(id, "sil_getFilterChanges", "[\"" + filterId + "\"]");
  }

  private Response silUninstallFilter(final int id, final String filterId) throws Exception {
    return jsonRpcRequest(id, "sil_uninstallFilter", "[\"" + filterId + "\"]");
  }
}
