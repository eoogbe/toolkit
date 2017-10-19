/* Copyright 2017 Google Inc
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

import com.google.api.codegen.configgen.ConfigHelper;
import com.google.api.codegen.configgen.ListTransformer;
import com.google.api.codegen.configgen.MissingFieldTransformer;
import com.google.api.codegen.configgen.NodeFinder;
import com.google.api.codegen.configgen.StringPairTransformer;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.ScalarConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.FixmeComment;
import com.google.api.codegen.configgen.nodes.metadata.RejectInitialComment;
import com.google.api.codegen.configgen.nodes.metadata.RejectRefreshComment;
import com.google.api.codegen.configgen.nodes.metadata.Rule;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.model.Method;
import com.google.common.collect.ImmutableList;
import io.grpc.Status;
import java.util.List;

public class RetryMerger {
  private static final String RETRY_CODES_IDEMPOTENT_NAME = "idempotent";
  private static final String RETRY_CODES_NON_IDEMPOTENT_NAME = "non_idempotent";
  private static final String RETRY_PARAMS_DEFAULT_NAME = "default";

  public ConfigNode generateRetryDefinitionsNode(ConfigNode prevNode) {
    FieldConfigNode retryCodesDefNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "retry_codes_def");
    retryCodesDefNode.setComment(new RejectRefreshComment("Definition for retryable codes."));
    prevNode.insertNext(retryCodesDefNode);
    generateRetryCodesDefValueNode(retryCodesDefNode);
    FieldConfigNode retryParamsDefNode =
        new FieldConfigNode(NodeFinder.getNextLine(retryCodesDefNode), "retry_params_def");
    retryParamsDefNode.setComment(
        new RejectRefreshComment("Definition for retry/backoff parameters."));
    retryCodesDefNode.insertNext(retryParamsDefNode);
    generateRetryParamsDefValueNode(retryParamsDefNode);
    return retryParamsDefNode;
  }

  public ConfigNode generateRetryNamesNode(ConfigNode prevNode, Method method) {
    String retryCodesName =
        isIdempotent(method) ? RETRY_CODES_IDEMPOTENT_NAME : RETRY_CODES_NON_IDEMPOTENT_NAME;
    ConfigNode retryCodesNameNode =
        StringPairTransformer.generateStringPair(
                NodeFinder.getNextLine(prevNode), "retry_codes_name", retryCodesName)
            .setComment(new FixmeComment("Configure the retryable codes for this method."));
    prevNode.insertNext(retryCodesNameNode);
    ConfigNode retryParamsNameNode =
        StringPairTransformer.generateStringPair(
                NodeFinder.getNextLine(retryCodesNameNode),
                "retry_params_name",
                RETRY_PARAMS_DEFAULT_NAME)
            .setComment(new FixmeComment("Configure the retryable params for this method."));
    retryCodesNameNode.insertNext(retryParamsNameNode);
    return retryParamsNameNode;
  }

  /**
   * Returns true if the method is idempotent according to the http method kind (GET, PUT, DELETE).
   */
  private boolean isIdempotent(Method method) {
    HttpAttribute httpAttr = method.getAttribute(HttpAttribute.KEY);
    if (httpAttr == null) {
      return false;
    }
    MethodKind methodKind = httpAttr.getMethodKind();
    return methodKind.isIdempotent();
  }

  public void checkRetryDefinitions(ConfigNode interfaceNode, ConfigHelper helper) {
    ConfigNode prevNode = NodeFinder.findByValue(interfaceNode, "collections");
    if (!prevNode.isPresent()) {
      prevNode = NodeFinder.findByValue(interfaceNode, "name");
    }

    FieldConfigNode retryCodesDefNode =
        MissingFieldTransformer.insert("retry_codes_def", interfaceNode, prevNode).generate();

    if (NodeFinder.hasChild(retryCodesDefNode)) {
      for (ConfigNode retryCodeDefNode : NodeFinder.getChildren(retryCodesDefNode)) {
        FieldConfigNode nameNode =
            MissingFieldTransformer.prepend("name", retryCodeDefNode).generate();
        if (!NodeFinder.hasChild(nameNode)) {
          nameNode
              .setChild(new ScalarConfigNode(nameNode.getStartLine(), ""))
              .setComment(
                  new RejectInitialComment("Fill-in a 'name' for this retry code definition"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.RETRY)
                      .comment("Missing 'name' in retry code definition")
                      .location(helper.getLocation(nameNode.getStartLine()))
                      .build());
        }
      }
    } else {
      generateRetryCodesDefValueNode(retryCodesDefNode);
      retryCodesDefNode.setAdvice(
          Advice.newBuilder()
              .rule(Rule.RETRY)
              .comment("Missing 'retry_codes_def'")
              .location(helper.getLocation(retryCodesDefNode.getStartLine()))
              .hasSuggestion(true)
              .build());
    }

    FieldConfigNode retryParamsDefNode =
        MissingFieldTransformer.insert("retry_params_def", interfaceNode, retryCodesDefNode)
            .generate();

    if (NodeFinder.hasChild(retryParamsDefNode)) {
      for (ConfigNode retryParamDefNode : NodeFinder.getChildren(retryParamsDefNode)) {
        FieldConfigNode nameNode =
            MissingFieldTransformer.prepend("name", retryParamDefNode).generate();
        if (!NodeFinder.hasChild(nameNode)) {
          nameNode
              .setChild(new ScalarConfigNode(nameNode.getStartLine(), ""))
              .setComment(
                  new RejectInitialComment("Fill-in a 'name' for this retry param definition"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.RETRY)
                      .comment("Missing 'name' in retry param definition")
                      .location(helper.getLocation(nameNode.getStartLine()))
                      .build());
        }
      }
    } else {
      generateRetryParamsDefValueNode(retryParamsDefNode);
      retryParamsDefNode.setAdvice(
          Advice.newBuilder()
              .rule(Rule.RETRY)
              .comment("Missing 'retry_params_def'")
              .location(helper.getLocation(retryParamsDefNode.getStartLine()))
              .hasSuggestion(true)
              .build());
    }
  }

  public void checkRetryNames(
      ConfigNode methodNode, ConfigNode interfaceNode, ConfigHelper helper) {
    ConfigNode retryCodesDefNode = NodeFinder.findByValue(interfaceNode, "retry_codes_def");
    ConfigNode retryCodesNameNode = NodeFinder.findByValue(methodNode, "retry_codes_name");
    String retryCodesName = retryCodesNameNode.getChild().getText();
    if (!NodeFinder.findByName(retryCodesDefNode, retryCodesName).isPresent()) {
      String apiName = NodeFinder.findByValue(interfaceNode, "name").getChild().getText();
      String methodName = NodeFinder.findByValue(methodNode, "name").getChild().getText();
      ((FieldConfigNode) retryCodesNameNode)
          .setComment(
              new FixmeComment(
                  String.format(
                      "Remove extra retry codes name '%s' or define it in the 'retry_codes_def'",
                      retryCodesName)))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.RETRY)
                  .comment(
                      String.format(
                          "Retry codes name '%s' not found in 'retry_codes_def' for the API "
                              + "interface '%s'",
                          retryCodesName, apiName))
                  .location(helper.getLocation(retryCodesNameNode.getStartLine()))
                  .elementName(String.format("%s.%s", apiName, methodName))
                  .build());
    }
    ConfigNode retryParamsDefNode = NodeFinder.findByValue(interfaceNode, "retry_params_def");
    ConfigNode retryParamsNameNode = NodeFinder.findByValue(methodNode, "retry_params_name");
    String retryParamsName = retryParamsNameNode.getChild().getText();
    if (!NodeFinder.findByName(retryParamsDefNode, retryParamsName).isPresent()) {
      String apiName = NodeFinder.findByValue(interfaceNode, "name").getChild().getText();
      String methodName = NodeFinder.findByValue(methodNode, "name").getChild().getText();
      ((FieldConfigNode) retryParamsNameNode)
          .setComment(
              new FixmeComment(
                  String.format(
                      "Remove extra retry params name '%s' or define it in the 'retry_params_def'",
                      retryParamsName)))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.RETRY)
                  .comment(
                      String.format(
                          "Retry params name '%s' not found in 'retry_params_def' for the API "
                              + "interface '%s'",
                          retryParamsName, apiName))
                  .location(helper.getLocation(retryCodesNameNode.getStartLine()))
                  .elementName(String.format("%s.%s", apiName, methodName))
                  .build());
    }
  }

  private void generateRetryCodesDefValueNode(ConfigNode parentNode) {
    ConfigNode idempotentNode =
        generateRetryCodeDefNode(
            NodeFinder.getNextLine(parentNode),
            RETRY_CODES_IDEMPOTENT_NAME,
            ImmutableList.of(Status.Code.UNAVAILABLE.name(), Status.Code.DEADLINE_EXCEEDED.name()));
    parentNode.setChild(idempotentNode);
    ConfigNode nonIdempotentNode =
        generateRetryCodeDefNode(
            NodeFinder.getNextLine(idempotentNode),
            RETRY_CODES_NON_IDEMPOTENT_NAME,
            ImmutableList.<String>of());
    idempotentNode.insertNext(nonIdempotentNode);
  }

  private ConfigNode generateRetryCodeDefNode(int startLine, String name, List<String> codes) {
    ConfigNode retryCodeDefNode = new ListItemConfigNode(startLine);
    ConfigNode nameNode = StringPairTransformer.generateStringPair(startLine, "name", name);
    retryCodeDefNode.setChild(nameNode);
    ConfigNode retryCodesNode =
        new FieldConfigNode(NodeFinder.getNextLine(nameNode), "retry_codes");
    nameNode.insertNext(retryCodesNode);
    ListTransformer.generateStringList(codes, retryCodesNode);
    return retryCodeDefNode;
  }

  private void generateRetryParamsDefValueNode(ConfigNode parentNode) {
    ConfigNode defaultNode =
        generateRetryParamDefNode(NodeFinder.getNextLine(parentNode), RETRY_PARAMS_DEFAULT_NAME);
    parentNode.setChild(defaultNode);
  }

  private ConfigNode generateRetryParamDefNode(int startLine, String name) {
    ConfigNode retryParamDefNode = new ListItemConfigNode(startLine);
    ConfigNode nameNode = StringPairTransformer.generateStringPair(startLine, "name", name);
    retryParamDefNode.setChild(nameNode);
    ConfigNode initialRetryDelayMillisNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(nameNode), "initial_retry_delay_millis", "100");
    nameNode.insertNext(initialRetryDelayMillisNode);
    ConfigNode retryDelayMultiplierNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(initialRetryDelayMillisNode), "retry_delay_multiplier", "1.3");
    initialRetryDelayMillisNode.insertNext(retryDelayMultiplierNode);
    ConfigNode maxRetryDelayMillisNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(retryDelayMultiplierNode), "max_retry_delay_millis", "60000");
    retryDelayMultiplierNode.insertNext(maxRetryDelayMillisNode);
    ConfigNode initialRpcTimeoutMillisNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(maxRetryDelayMillisNode), "initial_rpc_timeout_millis", "20000");
    maxRetryDelayMillisNode.insertNext(initialRpcTimeoutMillisNode);
    ConfigNode rpcTimeoutMultiplierNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(initialRpcTimeoutMillisNode), "rpc_timeout_multiplier", "1");
    initialRpcTimeoutMillisNode.insertNext(rpcTimeoutMultiplierNode);
    ConfigNode maxRpcTimeoutMillisNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(rpcTimeoutMultiplierNode), "max_rpc_timeout_millis", "20000");
    rpcTimeoutMultiplierNode.insertNext(maxRpcTimeoutMillisNode);
    ConfigNode totalTimeoutMillisNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(maxRpcTimeoutMillisNode), "total_timeout_millis", "600000");
    maxRpcTimeoutMillisNode.insertNext(totalTimeoutMillisNode);
    return retryParamDefNode;
  }
}
