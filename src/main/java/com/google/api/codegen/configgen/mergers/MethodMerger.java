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

import com.google.api.codegen.configgen.CollectionPattern;
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
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodMerger {
  private static final ImmutableSet<String> IGNORED_FIELDS =
      ImmutableSet.of("page_token", "page_size");

  // Do not apply flattening if the parameter count exceeds the threshold.
  // TODO(shinfan): Investigate a more intelligent way to handle this.
  private static final int FLATTENING_THRESHOLD = 4;

  private static final int REQUEST_OBJECT_METHOD_THRESHOLD = 1;

  private static final String METHODS_COMMENT =
      "A list of method configurations.\n"
          + "Common properties:\n\n"
          + "  name - The simple name of the method.\n"
          + "    flattening - Specifies the configuration for parameter flattening.\n"
          + "    Describes the parameter groups for which a generator should produce method "
          + "overloads which allow a client to directly pass request message fields as method "
          + "parameters. This information may or may not be used, depending on the target "
          + "language. Consists of groups, which each represent a list of parameters to be "
          + "flattened. Each parameter listed must be a field of the request message.\n\n"
          + "  required_fields - Fields that are always required for a request to be valid.\n\n"
          + "  request_object_method - Turns on or off the generation of a method whose sole "
          + "parameter is a request object. Not all languages will generate this method.\n\n"
          + "  page_streaming - Specifies the configuration for paging.\n"
          + "    Describes information for generating a method which transforms a paging list RPC "
          + "into a stream of resources.\n"
          + "    Consists of a request and a response.\n"
          + "    The request specifies request information of the list method. It defines which "
          + "fields match the paging pattern in the request. The request consists of a "
          + "page_size_field and a token_field. The page_size_field is the name of the optional "
          + "field specifying the maximum number of elements to be returned in the response. The "
          + "token_field is the name of the field in the request containing the page token.\n"
          + "    The response specifies response information of the list method. It defines which "
          + "fields match the paging pattern in the response. The response consists of a "
          + "token_field and a resources_field. The token_field is the name of the field in the "
          + "response containing the next page token. The resources_field is the name of the field "
          + "in the response containing the list of resources belonging to the page.\n\n"
          + "  retry_codes_name - Specifies the configuration for retryable codes. The name must "
          + "be defined in interfaces.retry_codes_def.\n\n"
          + "  retry_params_name - Specifies the configuration for retry/backoff parameters. The "
          + "name must be defined in interfaces.retry_params_def.\n\n"
          + "  field_name_patterns - Maps the field name of the request type to entity_name of "
          + "interfaces.collections.\n"
          + "    Specifies the string pattern that the field must follow.\n\n"
          + "  timeout_millis - Specifies the default timeout for a non-retrying call. If the call "
          + "is retrying, refer to retry_params_name instead.";

  private final RetryMerger retryMerger = new RetryMerger();
  private final PageStreamingMerger pageStreamingMerger = new PageStreamingMerger();

  public void generateMethodsNode(
      ConfigNode prevNode,
      Interface apiInterface,
      Map<String, String> collectionNameMap,
      ConfigHelper helper) {
    FieldConfigNode methodsNode = new FieldConfigNode(NodeFinder.getNextLine(prevNode), "methods");
    methodsNode.setComment(new RejectRefreshComment(METHODS_COMMENT));
    prevNode.insertNext(methodsNode);
    generateMethodsValueNode(methodsNode, apiInterface, collectionNameMap, helper);
  }

  public void checkMethods(
      ConfigNode interfaceNode,
      Interface apiInterface,
      Map<String, String> collectionNameMap,
      ConfigNode configNode,
      ConfigHelper helper) {
    FieldConfigNode methodsNode =
        MissingFieldTransformer.append("methods", interfaceNode).generate();

    if (NodeFinder.hasChild(methodsNode)) {
      Map<String, Integer> methods = new HashMap<>();
      for (Method method : apiInterface.getReachableMethods()) {
        methods.put(method.getSimpleName(), 0);
      }
      for (ConfigNode methodNode : NodeFinder.getChildren(methodsNode)) {
        FieldConfigNode nameNode = MissingFieldTransformer.prepend("name", methodNode).generate();

        if (NodeFinder.hasChild(nameNode)) {
          String methodName = nameNode.getChild().getText();
          Integer count = methods.get(methodName);
          if (count == null || count < 0) {
            methods.put(methodName, -1);
          } else {
            methods.put(methodName, count + 1);
          }
        } else {
          nameNode
              .setChild(new ScalarConfigNode(nameNode.getStartLine(), ""))
              .setComment(new RejectInitialComment("Fill-in a 'name' for the method"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.METHOD)
                      .comment("Missing 'name' in method")
                      .location(helper.getLocation(nameNode.getStartLine()))
                      .elementName(apiInterface.getFullName())
                      .build());
        }
      }
      for (Map.Entry<String, Integer> entry : methods.entrySet()) {
        String methodName = entry.getKey();
        int count = entry.getValue();
        if (count < 0) {
          ListItemConfigNode methodNode =
              (ListItemConfigNode) NodeFinder.findByName(methodsNode, methodName);
          methodNode
              .setComment(
                  new FixmeComment(
                      String.format(
                          "Remove extra method '%s' or define it in the IDL", methodName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.METHOD)
                      .comment(
                          String.format(
                              "'%s' not found in IDL methods for API interface '%s'",
                              methodName, apiInterface.getFullName()))
                      .location(helper.getLocation(methodNode.getStartLine()))
                      .elementName(String.format("%s.%s", apiInterface.getFullName(), methodName))
                      .build());
        } else if (count == 0) {
          ConfigNode methodsValueNode = NodeFinder.getLastChild(methodsNode);
          Method method = apiInterface.lookupMethod(methodName);
          String fullMethodName = method.getFullName();
          ListItemConfigNode methodNode =
              generateMethodNode(
                  NodeFinder.getNextLine(methodsValueNode), method, collectionNameMap, helper);
          methodNode
              .setComment(
                  new RejectInitialComment(String.format("Check added method '%s'", methodName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.METHOD)
                      .comment(String.format("Missing method '%s'", fullMethodName))
                      .location(helper.getLocation(methodNode.getStartLine()))
                      .elementName(fullMethodName)
                      .hasSuggestion(true)
                      .build());
          methodsValueNode.insertNext(methodNode);
        } else if (count > 1) {
          String fullMethodName = String.format("%s.%s", apiInterface.getFullName(), methodName);
          ListItemConfigNode methodNode =
              (ListItemConfigNode) NodeFinder.findByName(methodsNode, methodName);
          methodNode
              .setComment(
                  new FixmeComment(
                      String.format("Remove or rename duplicated method '%s'", methodName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.METHOD)
                      .comment(
                          String.format("Method '%s' duplicated %d times", fullMethodName, count))
                      .location(helper.getLocation(methodNode.getStartLine()))
                      .elementName(fullMethodName)
                      .build());
        } else {
          ConfigNode methodNode = NodeFinder.findByName(methodsNode, methodName);
          Method method = apiInterface.lookupMethod(methodName);
          checkMethod(methodNode, method, configNode, helper);
        }
      }
    } else {
      generateMethodsValueNode(methodsNode, apiInterface, collectionNameMap, helper);
      methodsNode.setAdvice(
          Advice.newBuilder()
              .rule(Rule.METHOD)
              .comment("Missing 'methods'")
              .location(helper.getLocation(methodsNode.getStartLine()))
              .elementName(apiInterface.getFullName())
              .hasSuggestion(true)
              .build());
    }
  }

  private ConfigNode generateMethodsValueNode(
      ConfigNode parentNode,
      Interface apiInterface,
      final Map<String, String> collectionNameMap,
      final ConfigHelper helper) {
    return ListTransformer.generateList(
        apiInterface.getReachableMethods(),
        parentNode,
        new ListTransformer.ElementTransformer<Method>() {
          @Override
          public ConfigNode generateElement(int startLine, Method method) {
            return generateMethodNode(startLine, method, collectionNameMap, helper);
          }
        });
  }

  private void checkMethod(
      ConfigNode methodNode, Method method, ConfigNode configNode, ConfigHelper helper) {
    ConfigNode interfaceNode =
        NodeFinder.findByName(
            NodeFinder.findByValue(configNode, "interfaces"), method.getParent().getFullName());
    retryMerger.checkRetryNames(methodNode, interfaceNode, helper);
    MessageType message = method.getInputMessage();
    ConfigNode flatteningNode = NodeFinder.findByValue(methodNode, "flattening");
    if (flatteningNode.isPresent()) {
      ConfigNode groupsNode = NodeFinder.findByValue(flatteningNode, "groups");
      for (ConfigNode groupNode : NodeFinder.getChildren(groupsNode)) {
        ConfigNode parametersNode = NodeFinder.findByValue(groupNode, "parameters");
        for (ConfigNode parameterNode : NodeFinder.getChildren(parametersNode)) {
          String fieldName = parameterNode.getChild().getText();
          Field field = message.lookupField(fieldName);
          if (field == null) {
            ((ListItemConfigNode) parameterNode)
                .setComment(
                    new FixmeComment(
                        String.format(
                            "Remove extra parameter '%s' from method '%s'",
                            fieldName, method.getSimpleName())))
                .setAdvice(
                    Advice.newBuilder()
                        .rule(Rule.FIELD)
                        .comment(
                            String.format(
                                "Parameter '%s' not found in IDL input message fields for method "
                                    + "'%s'",
                                fieldName, method.getFullName()))
                        .location(helper.getLocation(parameterNode.getStartLine()))
                        .elementName(String.format("%s.%s", message.getFullName(), fieldName))
                        .build());
          }
        }
      }
    }
    ConfigNode requiredFieldsNode = NodeFinder.findByValue(methodNode, "required_fields");
    for (ConfigNode requiredFieldNode : NodeFinder.getChildren(requiredFieldsNode)) {
      String fieldName = requiredFieldNode.getChild().getText();
      Field field = message.lookupField(fieldName);
      if (field == null) {
        ((ListItemConfigNode) requiredFieldNode)
            .setComment(
                new FixmeComment(
                    String.format(
                        "Remove extra required field '%s' from method '%s'",
                        fieldName, method.getSimpleName())))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.FIELD)
                    .comment(
                        String.format(
                            "Required field '%s' not found in IDL input message fields for method "
                                + "'%s'",
                            fieldName, method.getFullName()))
                    .location(helper.getLocation(requiredFieldNode.getStartLine()))
                    .elementName(String.format("%s.%s", message.getFullName(), fieldName))
                    .build());
      }
    }
    ConfigNode fieldNamePatternsNode = NodeFinder.findByValue(methodNode, "field_name_patterns");
    for (ConfigNode fieldNamePatternNode : NodeFinder.getChildren(fieldNamePatternsNode)) {
      String fieldName = fieldNamePatternNode.getText();
      Field field = message.lookupField(fieldName);
      if (field == null) {
        ((FieldConfigNode) fieldNamePatternNode)
            .setComment(
                new FixmeComment(
                    String.format(
                        "Remove extra path template '%s' from method '%s'",
                        fieldName, method.getSimpleName())))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.FIELD)
                    .comment(
                        String.format(
                            "Path template '%s' not found in IDL input message fields for method "
                                + "'%s'",
                            fieldName, method.getFullName()))
                    .location(helper.getLocation(fieldNamePatternNode.getStartLine()))
                    .elementName(String.format("%s.%s", message.getFullName(), fieldName))
                    .build());
      }
    }
    pageStreamingMerger.checkPageStreaming(methodNode, method, helper);
  }

  private ListItemConfigNode generateMethodNode(
      int startLine, Method method, Map<String, String> collectionNameMap, ConfigHelper helper) {
    ListItemConfigNode methodNode = new ListItemConfigNode(startLine);
    ConfigNode nameNode =
        StringPairTransformer.generateStringPair(startLine, "name", method.getSimpleName());
    methodNode.setChild(nameNode);
    ConfigNode prevNode = generateField(nameNode, method);
    prevNode = pageStreamingMerger.generatePageStreamingNode(prevNode, method, helper);
    prevNode = retryMerger.generateRetryNamesNode(prevNode, method);
    prevNode = generateFieldNamePatterns(prevNode, method, collectionNameMap);
    ConfigNode timeoutMillisNode =
        StringPairTransformer.generateStringPair(
                NodeFinder.getNextLine(prevNode), "timeout_millis", "60000")
            .setComment(new FixmeComment("Configure the default timeout for a non-retrying call."));
    prevNode.insertNext(timeoutMillisNode);
    return methodNode;
  }

  private ConfigNode generateField(ConfigNode prevNode, Method method) {
    List<String> parameterList = new ArrayList<>();
    MessageType message = method.getInputMessage();
    for (Field field : message.getReachableFields()) {
      String fieldName = field.getSimpleName();
      if (field.getOneof() == null && !IGNORED_FIELDS.contains(fieldName)) {
        parameterList.add(fieldName);
      }
    }

    if (parameterList.size() > 0 && parameterList.size() <= FLATTENING_THRESHOLD) {
      prevNode = generateFlatteningNode(prevNode, parameterList);
    }

    FieldConfigNode requiredFieldsNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "required_fields");
    requiredFieldsNode.setComment(new FixmeComment("Configure which fields are required."));
    ConfigNode requiredFieldsValueNode =
        ListTransformer.generateStringList(parameterList, requiredFieldsNode);
    if (requiredFieldsValueNode.isPresent()) {
      prevNode.insertNext(requiredFieldsNode);
      prevNode = requiredFieldsNode;
    }

    // use all fields for the following check; if there are ignored fields for flattening
    // purposes, the caller still needs a way to set them (by using the request object method).
    int fieldCount = Iterables.size(message.getReachableFields());
    boolean requestObjectMethod =
        (fieldCount > REQUEST_OBJECT_METHOD_THRESHOLD || fieldCount != parameterList.size())
            && !method.getRequestStreaming();
    ConfigNode requestObjectMethodNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(prevNode),
            "request_object_method",
            String.valueOf(requestObjectMethod));
    prevNode.insertNext(requestObjectMethodNode);
    return requestObjectMethodNode;
  }

  private ConfigNode generateFlatteningNode(ConfigNode prevNode, List<String> parameterList) {
    ConfigNode flatteningNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "flattening")
            .setComment(
                new FixmeComment(
                    "Configure which groups of fields should be flattened into method params."));
    prevNode.insertNext(flatteningNode);
    ConfigNode flatteningGroupsNode =
        new FieldConfigNode(NodeFinder.getNextLine(flatteningNode), "groups");
    flatteningNode.setChild(flatteningGroupsNode);
    ConfigNode groupNode = new ListItemConfigNode(NodeFinder.getNextLine(flatteningGroupsNode));
    flatteningGroupsNode.setChild(groupNode);
    ConfigNode parametersNode = new FieldConfigNode(groupNode.getStartLine(), "parameters");
    groupNode.setChild(parametersNode);
    ListTransformer.generateStringList(parameterList, parametersNode);
    return flatteningNode;
  }

  private ConfigNode generateFieldNamePatterns(
      ConfigNode prevNode, Method method, final Map<String, String> nameMap) {
    ConfigNode fieldNamePatternsNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "field_name_patterns");
    ConfigNode fieldNamePatternsValueNode =
        ListTransformer.generateList(
            CollectionPattern.getCollectionPatternsFromMethod(method),
            fieldNamePatternsNode,
            new ListTransformer.ElementTransformer<CollectionPattern>() {
              @Override
              public ConfigNode generateElement(
                  int startLine, CollectionPattern collectionPattern) {
                return StringPairTransformer.generateStringPair(
                    startLine,
                    collectionPattern.getFieldPath(),
                    nameMap.get(collectionPattern.getTemplatizedResourcePath()));
              }
            });
    if (!fieldNamePatternsValueNode.isPresent()) {
      return prevNode;
    }

    prevNode.insertNext(fieldNamePatternsNode);
    return fieldNamePatternsNode;
  }
}
