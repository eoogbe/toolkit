/* Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.configgen.mergers;

import com.google.api.codegen.config.MethodModel;
import com.google.api.codegen.configgen.ListTransformer;
import com.google.api.codegen.configgen.NodeFinder;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.DefaultComment;
import com.google.api.codegen.configgen.nodes.metadata.FixmeComment;
import com.google.api.codegen.configgen.nodes.metadata.Source;
import com.google.common.collect.ImmutableList;
import io.grpc.Status;
import java.util.List;

/** Merges retry properties from an API interface into a ConfigNode. */
public class RetryMerger {
  private static final String RETRY_CODES_IDEMPOTENT_NAME = "idempotent";
  private static final String RETRY_CODES_NON_IDEMPOTENT_NAME = "non_idempotent";
  private static final String RETRY_PARAMS_DEFAULT_NAME = "default";

  public ConfigNode generateRetryDefinitionsNode(ConfigNode prevNode) {
    FieldConfigNode retryCodesDefNode =
        new FieldConfigNode(NodeFinder.getNextSourceLine(prevNode), "retry_codes_def")
            .setComment(new DefaultComment("Definition for retryable codes."));
    prevNode.insertNext(retryCodesDefNode);
    generateRetryCodesDefValueNode(retryCodesDefNode);
    FieldConfigNode retryParamsDefNode =
        new FieldConfigNode(NodeFinder.getNextSourceLine(retryCodesDefNode), "retry_params_def")
            .setComment(new DefaultComment("Definition for retry/backoff parameters."));
    retryCodesDefNode.insertNext(retryParamsDefNode);
    generateRetryParamsDefValueNode(retryParamsDefNode);
    return retryParamsDefNode;
  }

  private void generateRetryCodesDefValueNode(ConfigNode parentNode) {
    ConfigNode idempotentNode =
        generateRetryCodeDefNode(
            NodeFinder.getNextSourceLine(parentNode),
            RETRY_CODES_IDEMPOTENT_NAME,
            ImmutableList.of(Status.Code.UNAVAILABLE.name(), Status.Code.DEADLINE_EXCEEDED.name()));
    parentNode.setChild(idempotentNode);
    ConfigNode nonIdempotentNode =
        generateRetryCodeDefNode(
            NodeFinder.getNextSourceLine(idempotentNode),
            RETRY_CODES_NON_IDEMPOTENT_NAME,
            ImmutableList.<String>of());
    idempotentNode.insertNext(nonIdempotentNode);
  }

  private ConfigNode generateRetryCodeDefNode(Source source, String name, List<String> codes) {
    ConfigNode retryCodeDefNode = new ListItemConfigNode(source);
    ConfigNode nameNode = FieldConfigNode.createStringPair(source, "name", name);
    retryCodeDefNode.setChild(nameNode);
    ConfigNode retryCodesNode =
        new FieldConfigNode(NodeFinder.getNextSourceLine(nameNode), "retry_codes");
    nameNode.insertNext(retryCodesNode);
    ListTransformer.generateStringList(codes, retryCodesNode);
    return retryCodeDefNode;
  }

  private void generateRetryParamsDefValueNode(ConfigNode parentNode) {
    ConfigNode defaultNode =
        generateRetryParamDefNode(
            NodeFinder.getNextSourceLine(parentNode), RETRY_PARAMS_DEFAULT_NAME);
    parentNode.setChild(defaultNode);
  }

  private ConfigNode generateRetryParamDefNode(Source source, String name) {
    ConfigNode retryParamDefNode = new ListItemConfigNode(source);
    ConfigNode nameNode = FieldConfigNode.createStringPair(source, "name", name);
    retryParamDefNode.setChild(nameNode);
    ConfigNode initialRetryDelayMillisNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(nameNode), "initial_retry_delay_millis", "100");
    nameNode.insertNext(initialRetryDelayMillisNode);
    ConfigNode retryDelayMultiplierNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(initialRetryDelayMillisNode),
            "retry_delay_multiplier",
            "1.3");
    initialRetryDelayMillisNode.insertNext(retryDelayMultiplierNode);
    ConfigNode maxRetryDelayMillisNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(retryDelayMultiplierNode),
            "max_retry_delay_millis",
            "60000");
    retryDelayMultiplierNode.insertNext(maxRetryDelayMillisNode);
    ConfigNode initialRpcTimeoutMillisNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(maxRetryDelayMillisNode),
            "initial_rpc_timeout_millis",
            "20000");
    maxRetryDelayMillisNode.insertNext(initialRpcTimeoutMillisNode);
    ConfigNode rpcTimeoutMultiplierNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(initialRpcTimeoutMillisNode),
            "rpc_timeout_multiplier",
            "1");
    initialRpcTimeoutMillisNode.insertNext(rpcTimeoutMultiplierNode);
    ConfigNode maxRpcTimeoutMillisNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(rpcTimeoutMultiplierNode),
            "max_rpc_timeout_millis",
            "20000");
    rpcTimeoutMultiplierNode.insertNext(maxRpcTimeoutMillisNode);
    ConfigNode totalTimeoutMillisNode =
        FieldConfigNode.createStringPair(
            NodeFinder.getNextSourceLine(maxRpcTimeoutMillisNode),
            "total_timeout_millis",
            "600000");
    maxRpcTimeoutMillisNode.insertNext(totalTimeoutMillisNode);
    return retryParamDefNode;
  }

  public ConfigNode generateRetryNamesNode(ConfigNode prevNode, MethodModel method) {
    String retryCodesName =
        method.isIdempotent() ? RETRY_CODES_IDEMPOTENT_NAME : RETRY_CODES_NON_IDEMPOTENT_NAME;
    ConfigNode retryCodesNameNode =
        FieldConfigNode.createStringPair(
                NodeFinder.getNextSourceLine(prevNode), "retry_codes_name", retryCodesName)
            .setComment(new FixmeComment("Configure the retryable codes for this method."));
    prevNode.insertNext(retryCodesNameNode);
    ConfigNode retryParamsNameNode =
        FieldConfigNode.createStringPair(
                NodeFinder.getNextSourceLine(retryCodesNameNode),
                "retry_params_name",
                RETRY_PARAMS_DEFAULT_NAME)
            .setComment(new FixmeComment("Configure the retryable params for this method."));
    retryCodesNameNode.insertNext(retryParamsNameNode);
    return retryParamsNameNode;
  }
}
