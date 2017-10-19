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
import com.google.api.codegen.configgen.MissingFieldTransformer;
import com.google.api.codegen.configgen.NodeFinder;
import com.google.api.codegen.configgen.StringPairTransformer;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.NullConfigNode;
import com.google.api.codegen.configgen.nodes.ScalarConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.FixmeComment;
import com.google.api.codegen.configgen.nodes.metadata.RejectInitialComment;
import com.google.api.codegen.configgen.nodes.metadata.Rule;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Method;

public class PageStreamingMerger {
  private static final String PARAMETER_PAGE_TOKEN = "page_token";
  private static final String PARAMETER_PAGE_SIZE = "page_size";
  private static final String PARAMETER_NEXT_PAGE_TOKEN = "next_page_token";

  public void checkPageStreaming(ConfigNode methodNode, Method method, ConfigHelper helper) {
    ConfigNode pageStreamingNode = NodeFinder.findByValue(methodNode, "page_streaming");
    if (pageStreamingNode instanceof FieldConfigNode) {
      checkPageStreamingRequest((FieldConfigNode) pageStreamingNode, method, helper);
      checkPageStreamingResponse((FieldConfigNode) pageStreamingNode, method, helper);
    }
  }

  private void checkPageStreamingRequest(
      FieldConfigNode pageStreamingNode, Method method, ConfigHelper helper) {
    ConfigNode requestNode =
        MissingFieldTransformer.prepend("request", pageStreamingNode).generate();

    if (!NodeFinder.hasChild(requestNode)) {
      generatePageStreamingRequestValueNode(
          requestNode, NodeFinder.getNextLine(requestNode), method);
      if (!NodeFinder.hasChild(requestNode)) {
        pageStreamingNode
            .setChild(new NullConfigNode())
            .setComment(new FixmeComment("Remove extra 'page_streaming'"))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.PAGE_STREAMING)
                    .comment("Erroneous and potentially unnecessary 'page_streaming' detected")
                    .location(helper.getLocation(pageStreamingNode.getStartLine()))
                    .elementName(method.getFullName())
                    .build());
      }

      return;
    }

    ConfigNode tokenFieldNode = NodeFinder.findByValue(requestNode, "token_field");
    if (NodeFinder.hasChild(tokenFieldNode)) {
      return;
    }

    ConfigNode pageSizeFieldNode = NodeFinder.findByValue(requestNode, "page_size_field");
    if (NodeFinder.hasChild(pageSizeFieldNode)) {
      return;
    }

    ConfigNode nextNode = requestNode.getChild();
    int startLine = nextNode.isPresent() ? nextNode.getStartLine() : requestNode.getStartLine() + 1;
    ConfigNode requestValueNode =
        generatePageStreamingRequestValueNode(requestNode, startLine, method);
    if (!requestValueNode.isPresent()) {
      pageStreamingNode
          .setChild(new NullConfigNode())
          .setComment(new FixmeComment("Remove extra 'page_streaming'"))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.PAGE_STREAMING)
                  .comment("Erroneous and potentially unnecessary 'page_streaming' detected")
                  .location(helper.getLocation(pageStreamingNode.getStartLine()))
                  .elementName(method.getFullName())
                  .build());
      return;
    }

    ((FieldConfigNode) requestValueNode)
        .setComment(
            new RejectInitialComment(String.format("Check added '%s'", requestValueNode.getText())))
        .setAdvice(
            Advice.newBuilder()
                .rule(Rule.PAGE_STREAMING)
                .comment(
                    String.format(
                        "Missing '%s' in page streaming request of method '%s'",
                        requestValueNode.getText(), method.getFullName()))
                .location(helper.getLocation(requestValueNode.getStartLine()))
                .elementName(method.getFullName())
                .hasSuggestion(true)
                .build());
    ConfigNode nextValueNode = requestValueNode.getNext();
    if (nextValueNode instanceof FieldConfigNode) {
      ((FieldConfigNode) nextValueNode)
          .setComment(
              new RejectInitialComment(String.format("Check added '%s'", nextValueNode.getText())))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.PAGE_STREAMING)
                  .comment(
                      String.format(
                          "Missing '%s' in page streaming request of method '%s'",
                          nextValueNode.getText(), method.getFullName()))
                  .location(helper.getLocation(nextValueNode.getStartLine()))
                  .elementName(method.getFullName())
                  .hasSuggestion(true)
                  .build());
    }

    requestNode.setChild(requestValueNode.insertNext(nextNode));
  }

  private void checkPageStreamingResponse(
      FieldConfigNode parentNode, Method method, ConfigHelper helper) {
    ConfigNode responseNode = MissingFieldTransformer.prepend("response", parentNode).generate();

    if (!NodeFinder.hasChild(responseNode)) {
      generatePageStreamingResponseValueNode(responseNode, method, helper);
      if (!NodeFinder.hasChild(responseNode)) {
        parentNode
            .setChild(new NullConfigNode())
            .setComment(new FixmeComment("Remove extra 'page_streaming'"))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.PAGE_STREAMING)
                    .comment("Erroneous and potentially unnecessary 'page_streaming' detected")
                    .location(helper.getLocation(parentNode.getStartLine()))
                    .elementName(method.getFullName())
                    .build());
      }
      return;
    }

    FieldConfigNode tokenFieldNode =
        MissingFieldTransformer.prepend("token_field", responseNode).generate();

    if (!NodeFinder.hasChild(tokenFieldNode)) {
      if (hasResponseTokenField(method)) {
        tokenFieldNode
            .setChild(
                new ScalarConfigNode(tokenFieldNode.getStartLine(), PARAMETER_NEXT_PAGE_TOKEN))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.PAGE_STREAMING)
                    .comment("Missing 'token_field'")
                    .location(helper.getLocation(tokenFieldNode.getStartLine()))
                    .elementName(method.getFullName())
                    .hasSuggestion(true)
                    .build());
      }
    }

    FieldConfigNode resourcesFieldNode =
        MissingFieldTransformer.insert("resources_field", responseNode, tokenFieldNode).generate();
    if (!NodeFinder.hasChild(resourcesFieldNode)) {
      String resourcesFieldName = getResourcesFieldName(method, helper);
      if (resourcesFieldName != null) {
        resourcesFieldNode
            .setChild(new ScalarConfigNode(resourcesFieldNode.getStartLine(), resourcesFieldName))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.PAGE_STREAMING)
                    .comment("Missing 'resources_field'")
                    .location(helper.getLocation(resourcesFieldNode.getStartLine()))
                    .elementName(method.getFullName())
                    .hasSuggestion(true)
                    .build());
      }
    }
  }

  public ConfigNode generatePageStreamingNode(
      ConfigNode prevNode, Method method, ConfigHelper helper) {
    ConfigNode pageStreamingNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "page_streaming");
    ConfigNode requestNode = generatePageStreamingRequestNode(pageStreamingNode, method);
    if (requestNode == null) {
      return prevNode;
    }

    ConfigNode responseNode = generatePageStreamingResponseNode(requestNode, method, helper);
    if (responseNode == null) {
      return prevNode;
    }

    prevNode.insertNext(pageStreamingNode);
    return pageStreamingNode;
  }

  private ConfigNode generatePageStreamingRequestNode(ConfigNode parentNode, Method method) {
    ConfigNode requestNode = new FieldConfigNode(NodeFinder.getNextLine(parentNode), "request");
    parentNode.setChild(requestNode);
    ConfigNode requestValueNode =
        generatePageStreamingRequestValueNode(
            requestNode, NodeFinder.getNextLine(requestNode), method);
    return requestValueNode.isPresent() ? requestNode : null;
  }

  private ConfigNode generatePageStreamingRequestValueNode(
      ConfigNode parentNode, int startLine, Method method) {
    boolean hasTokenField = false;
    boolean hasPageSizeField = false;
    for (Field field : method.getInputMessage().getReachableFields()) {
      String fieldName = field.getSimpleName();
      if (fieldName.equals(PARAMETER_PAGE_TOKEN)) {
        hasTokenField = true;
      } else if (fieldName.equals(PARAMETER_PAGE_SIZE)) {
        hasPageSizeField = true;
      }
    }

    ConfigNode requestValueNode = null;
    if (hasPageSizeField) {
      requestValueNode =
          StringPairTransformer.generateStringPair(
              startLine, "page_size_field", PARAMETER_PAGE_SIZE);
      if (hasTokenField) {
        ConfigNode tokenFieldNode =
            StringPairTransformer.generateStringPair(
                NodeFinder.getNextLine(requestValueNode), "token_field", PARAMETER_PAGE_TOKEN);
        requestValueNode.insertNext(tokenFieldNode);
      }
    } else if (hasTokenField) {
      requestValueNode =
          StringPairTransformer.generateStringPair(startLine, "token_field", PARAMETER_PAGE_TOKEN);
    } else {
      return new NullConfigNode();
    }

    parentNode.setChild(requestValueNode);
    return requestValueNode;
  }

  private ConfigNode generatePageStreamingResponseNode(
      ConfigNode prevNode, Method method, ConfigHelper helper) {
    ConfigNode responseNode = new FieldConfigNode(NodeFinder.getNextLine(prevNode), "response");
    ConfigNode responseValueNode =
        generatePageStreamingResponseValueNode(responseNode, method, helper);
    if (!responseValueNode.isPresent()) {
      return null;
    }

    prevNode.insertNext(responseNode);
    return responseNode;
  }

  private ConfigNode generatePageStreamingResponseValueNode(
      ConfigNode parentNode, Method method, ConfigHelper helper) {
    if (!hasResponseTokenField(method)) {
      return new NullConfigNode();
    }

    String resourcesFieldName = getResourcesFieldName(method, helper);
    if (resourcesFieldName == null) {
      return new NullConfigNode();
    }

    ConfigNode tokenFieldNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(parentNode), "token_field", PARAMETER_NEXT_PAGE_TOKEN);
    parentNode.setChild(tokenFieldNode);
    ConfigNode resourcesFieldNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(tokenFieldNode), "resources_field", resourcesFieldName);
    return tokenFieldNode.insertNext(resourcesFieldNode);
  }

  private boolean hasResponseTokenField(Method method) {
    Field tokenField = method.getOutputMessage().lookupField(PARAMETER_NEXT_PAGE_TOKEN);
    return tokenField != null;
  }

  private String getResourcesFieldName(Method method, ConfigHelper helper) {
    String resourcesField = null;
    for (Field field : method.getOutputMessage().getReachableFields()) {
      if (!field.getType().isRepeated()) {
        continue;
      }

      if (resourcesField != null) {
        helper.error(
            method.getLocation(),
            String.format(
                "Page streaming resources field could not be heuristically determined for method "
                    + "'%s'%n",
                method.getSimpleName()));
        return null;
      }

      resourcesField = field.getSimpleName();
    }
    return resourcesField;
  }
}
