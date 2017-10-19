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
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Api;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class InterfaceMerger {
  private final CollectionMerger collectionMerger = new CollectionMerger();
  private final RetryMerger retryMerger = new RetryMerger();
  private final MethodMerger methodMerger = new MethodMerger();

  public void mergeInterfaces(final Model model, ConfigNode configNode, final ConfigHelper helper) {
    FieldConfigNode interfacesNode =
        MissingFieldTransformer.append("interfaces", configNode).generate();

    if (NodeFinder.hasChild(interfacesNode)) {
      Map<String, Integer> apiInterfaces = new HashMap<>();
      for (Api api : model.getServiceConfig().getApisList()) {
        apiInterfaces.put(api.getName(), 0);
      }
      for (ConfigNode interfaceNode : NodeFinder.getChildren(interfacesNode)) {
        FieldConfigNode nameNode =
            MissingFieldTransformer.prepend("name", interfaceNode).generate();
        if (NodeFinder.hasChild(nameNode)) {
          String apiName = nameNode.getChild().getText();
          Integer count = apiInterfaces.get(apiName);
          if (count == null || count < 0) {
            apiInterfaces.put(apiName, -1);
          } else {
            apiInterfaces.put(apiName, count + 1);
          }
        } else {
          nameNode
              .setChild(new ScalarConfigNode(nameNode.getStartLine(), ""))
              .setComment(new RejectInitialComment("Fill in a 'name' for the API interface"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.INTERFACE)
                      .comment("Missing 'name' in API interface")
                      .location(helper.getLocation(nameNode.getStartLine()))
                      .build());
        }
      }
      for (Map.Entry<String, Integer> entry : apiInterfaces.entrySet()) {
        String apiName = entry.getKey();
        int count = entry.getValue();
        if (count < 0) {
          ListItemConfigNode interfaceNode =
              (ListItemConfigNode) NodeFinder.findByName(interfacesNode, apiName);
          interfaceNode
              .setComment(
                  new FixmeComment(
                      String.format(
                          "Remove extra API interface '%s' or define it in the IDL", apiName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.INTERFACE)
                      .comment(String.format("'%s' not found in IDL API interfaces", apiName))
                      .location(helper.getLocation(interfaceNode.getStartLine()))
                      .elementName(apiName)
                      .build());
        } else if (count == 0) {
          ConfigNode interfacesValueNode = NodeFinder.getLastChild(interfacesNode);
          ListItemConfigNode interfaceNode =
              generateInterfaceNode(
                  model, apiName, NodeFinder.getNextLine(interfacesValueNode), helper);
          interfaceNode
              .setComment(
                  new RejectInitialComment(
                      String.format("Check added API interface '%s'", apiName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.INTERFACE)
                      .comment(String.format("Missing API interface '%s'", apiName))
                      .location(helper.getLocation(interfaceNode.getStartLine()))
                      .elementName(apiName)
                      .hasSuggestion(true)
                      .build());
          interfacesValueNode.insertNext(interfaceNode);
        } else if (count > 1) {
          ListItemConfigNode interfaceNode =
              (ListItemConfigNode) NodeFinder.findByName(interfacesNode, apiName);
          interfaceNode
              .setComment(
                  new FixmeComment(
                      String.format("Remove or rename duplicated API interface '%s'", apiName)))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.INTERFACE)
                      .comment(
                          String.format("API interface '%s' duplicated %d times", apiName, count))
                      .location(helper.getLocation(interfaceNode.getStartLine()))
                      .elementName(apiName)
                      .build());
        } else {
          ConfigNode interfaceNode = NodeFinder.findByName(interfacesNode, apiName);
          Interface apiInterface = model.getSymbolTable().lookupInterface(apiName);
          checkInterface(interfaceNode, apiInterface, configNode, helper);
        }
      }
    } else {
      ConfigNode interfacesValueNode =
          ListTransformer.generateList(
              model.getServiceConfig().getApisList(),
              interfacesNode,
              new ListTransformer.ElementTransformer<Api>() {
                @Override
                public ConfigNode generateElement(int startLine, Api api) {
                  return generateInterfaceNode(model, api.getName(), startLine, helper);
                }
              });
      interfacesNode
          .setChild(interfacesValueNode)
          .setComment(new RejectRefreshComment("A list of API interface configurations."))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.INTERFACE)
                  .comment("Missing 'interfaces'")
                  .location(helper.getLocation(interfacesNode.getStartLine()))
                  .hasSuggestion(true)
                  .build());
    }
  }

  private ListItemConfigNode generateInterfaceNode(
      Model model, String apiName, int startLine, ConfigHelper helper) {
    Interface apiInterface = model.getSymbolTable().lookupInterface(apiName);
    Map<String, String> collectionNameMap =
        getResourceToEntityNameMap(apiInterface.getReachableMethods());
    ListItemConfigNode interfaceNode = new ListItemConfigNode(startLine);
    FieldConfigNode nameNode =
        StringPairTransformer.generateStringPair(interfaceNode.getStartLine(), "name", apiName);
    nameNode.setComment(new RejectRefreshComment("The fully qualified name of the API interface."));
    interfaceNode.setChild(nameNode);
    ConfigNode collectionsNode =
        collectionMerger.generateCollectionsNode(nameNode, collectionNameMap);
    ConfigNode retryParamsDefNode = retryMerger.generateRetryDefinitionsNode(collectionsNode);
    methodMerger.generateMethodsNode(retryParamsDefNode, apiInterface, collectionNameMap, helper);
    return interfaceNode;
  }

  private void checkInterface(
      ConfigNode interfaceNode,
      Interface apiInterface,
      ConfigNode configNode,
      ConfigHelper helper) {
    Map<String, String> collectionNameMap =
        getResourceToEntityNameMap(apiInterface.getReachableMethods());
    collectionMerger.checkCollections(interfaceNode, helper);
    retryMerger.checkRetryDefinitions(interfaceNode, helper);
    methodMerger.checkMethods(interfaceNode, apiInterface, collectionNameMap, configNode, helper);
  }

  /**
   * Examines all of the resource paths used by the methods, and returns a map from each unique
   * resource paths to a short name used by the collection configuration.
   */
  private static Map<String, String> getResourceToEntityNameMap(Iterable<Method> methods) {
    // Using a map with the string representation of the resource path to avoid duplication
    // of equivalent paths.
    // Using a TreeMap in particular so that the ordering is deterministic
    // (useful for testability).
    Map<String, CollectionPattern> specs = new TreeMap<>();
    for (Method method : methods) {
      for (CollectionPattern collectionPattern :
          CollectionPattern.getCollectionPatternsFromMethod(method)) {
        String resourcePath = collectionPattern.getTemplatizedResourcePath();
        // If there are multiple field segments with the same resource path, the last
        // one will be used, making the output deterministic. Also, the first field path
        // encountered tends to be simply "name" because it is the corresponding create
        // API method for the type.
        specs.put(resourcePath, collectionPattern);
      }
    }

    Set<String> usedNameSet = new HashSet<>();
    ImmutableMap.Builder<String, String> nameMapBuilder = ImmutableMap.builder();
    for (CollectionPattern collectionPattern : specs.values()) {
      String resourceNameString = collectionPattern.getTemplatizedResourcePath();
      String entityNameString = collectionPattern.getUniqueName(usedNameSet);
      usedNameSet.add(entityNameString);
      nameMapBuilder.put(resourceNameString, entityNameString);
    }
    return nameMapBuilder.build();
  }
}
