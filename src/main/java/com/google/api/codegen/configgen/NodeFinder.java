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
package com.google.api.codegen.configgen;

import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.NullConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Source;
import com.google.common.collect.Iterables;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Utilities for finding and traversing ConfigNodes. */
public class NodeFinder {
  public static ConfigNode findByValue(ConfigNode parentNode, String value) {
    for (ConfigNode childNode : getChildren(parentNode)) {
      if (value.equals(childNode.getText())) {
        return childNode;
      }
    }

    return new NullConfigNode();
  }

  public static boolean hasChild(ConfigNode parentNode) {
    ConfigNode childNode = parentNode.getChild();
    if (!childNode.isPresent()) {
      return false;
    }

    if (!childNode.getText().isEmpty()) {
      return true;
    }

    if (!(childNode instanceof ListItemConfigNode)) {
      return false;
    }

    return ((ListItemConfigNode) childNode).getChild().isPresent();
  }

  public static Source getNextSourceLine(ConfigNode node) {
    return node.getChild().isPresent()
        ? getNextSourceLine(node.getChild())
        : Source.forNextLine(node.getSource());
  }

  public static ConfigNode getLastChild(ConfigNode parentNode) {
    return Iterables.getLast(getChildren(parentNode));
  }

  public static Iterable<ConfigNode> getChildren(final ConfigNode parentNode) {
    return () -> new NodeIterator(parentNode.getChild());
  }

  private static class NodeIterator implements Iterator<ConfigNode> {
    private ConfigNode cursor;

    private NodeIterator(ConfigNode start) {
      cursor = start;
    }

    @Override
    public boolean hasNext() {
      return cursor.isPresent();
    }

    @Override
    public ConfigNode next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      ConfigNode current = cursor;
      cursor = cursor.getNext();
      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
