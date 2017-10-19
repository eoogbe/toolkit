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
import com.google.api.codegen.configgen.NodeFinder;
import com.google.api.codegen.configgen.StringPairTransformer;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.ScalarConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.RejectInitialComment;
import com.google.api.codegen.configgen.nodes.metadata.RejectRefreshComment;
import com.google.api.codegen.configgen.nodes.metadata.Rule;
import java.util.Map;

public class CollectionMerger {
  private static final String COLLECTIONS_COMMENT =
      "A list of resource collection configurations.\n"
          + "Consists of a name_pattern and an entity_name.\n"
          + "The name_pattern is a pattern to describe the names of the resources of this "
          + "collection, using the platform's conventions for URI patterns. A generator may use "
          + "this to generate methods to compose and decompose such names. The pattern should use "
          + "named placeholders as in `shelves/{shelf}/books/{book}`; those will be taken as hints "
          + "for the parameter names of the generated methods. If empty, no name methods are "
          + "generated.\n"
          + "The entity_name is the name to be used as a basis for generated methods and classes.";

  public ConfigNode generateCollectionsNode(ConfigNode prevNode, Map<String, String> nameMap) {
    FieldConfigNode collectionsNode =
        new FieldConfigNode(NodeFinder.getNextLine(prevNode), "collections");
    collectionsNode.setComment(new RejectRefreshComment(COLLECTIONS_COMMENT));
    prevNode.insertNext(collectionsNode);
    ListTransformer.generateList(
        nameMap.entrySet(),
        collectionsNode,
        new ListTransformer.ElementTransformer<Map.Entry<String, String>>() {
          @Override
          public ConfigNode generateElement(int startLine, Map.Entry<String, String> entry) {
            return generateCollectionNode(startLine, entry.getKey(), entry.getValue());
          }
        });
    return collectionsNode;
  }

  private ConfigNode generateCollectionNode(int startLine, String namePattern, String entityName) {
    ConfigNode collectionNode = new ListItemConfigNode(startLine);
    ConfigNode namePatternNode =
        StringPairTransformer.generateStringPair(
            collectionNode.getStartLine(), "name_pattern", namePattern);
    collectionNode.setChild(namePatternNode);
    ConfigNode entityNameNode =
        StringPairTransformer.generateStringPair(
            NodeFinder.getNextLine(namePatternNode), "entity_name", entityName);
    namePatternNode.insertNext(entityNameNode);
    return collectionNode;
  }

  public void checkCollections(ConfigNode parentNode, ConfigHelper helper) {
    ConfigNode collectionsNode = NodeFinder.findByValue(parentNode, "collections");
    for (ConfigNode collectionNode : NodeFinder.getChildren(collectionsNode)) {
      ConfigNode namePatternNode = NodeFinder.findByValue(collectionNode, "name_pattern");
      ConfigNode entityNameNode = NodeFinder.findByValue(collectionNode, "entity_name");

      if ((namePatternNode instanceof FieldConfigNode)
          && (entityNameNode instanceof FieldConfigNode)) {
        continue;
      }

      if (!(namePatternNode instanceof FieldConfigNode)
          && !(entityNameNode instanceof FieldConfigNode)) {
        continue;
      }

      ConfigNode next = collectionNode.getChild();
      int startLine = next.isPresent() ? next.getStartLine() : collectionNode.getStartLine() + 1;
      namePatternNode =
          new FieldConfigNode(startLine, "name_pattern")
              .setChild(new ScalarConfigNode(startLine, ""))
              .setComment(
                  new RejectInitialComment(
                      "Fill in a 'name_pattern' that corresponds to the 'entity_name'"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.COLLECTION)
                      .comment("Missing 'name_pattern'")
                      .location(helper.getLocation(startLine))
                      .build());
      collectionNode.setChild(namePatternNode.insertNext(next));

      next = entityNameNode.getNext();
      startLine = NodeFinder.getNextLine(namePatternNode);
      entityNameNode =
          new FieldConfigNode(startLine, "entity_name")
              .setChild(new ScalarConfigNode(startLine, ""))
              .setComment(
                  new RejectInitialComment(
                      "Fill in an 'entity_name' that corresponds to the 'name_pattern'"))
              .setAdvice(
                  Advice.newBuilder()
                      .rule(Rule.COLLECTION)
                      .comment("Missing 'entity_name'")
                      .location(helper.getLocation(startLine))
                      .build());
      namePatternNode.insertNext(entityNameNode.insertNext(next));
    }
  }
}
