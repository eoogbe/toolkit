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
package com.google.api.codegen.configgen.nodes;

import com.google.common.truth.Truth;
import org.junit.Test;

public class BaseConfigNodeTest {
  private static class TestConfigNode extends BaseConfigNode {
    TestConfigNode(String text) {
      super(text);
    }
  }

  @Test
  public void testIsPresent() throws Exception {
    TestConfigNode node = new TestConfigNode("foo");
    Truth.assertThat(node.isPresent()).isTrue();
  }

  @Test
  public void testGetText() throws Exception {
    TestConfigNode node = new TestConfigNode("foo");
    Truth.assertThat(node.getText()).isEqualTo("foo");
  }

  @Test
  public void testChild() throws Exception {
    TestConfigNode node = new TestConfigNode("foo");
    ConfigNode child = new ScalarConfigNode("bar");
    Truth.assertThat(node.setChild(child)).isSameAs(node);
    Truth.assertThat(node.getChild().isPresent()).isFalse();
  }

  @Test
  public void testNext() throws Exception {
    TestConfigNode node = new TestConfigNode("foo");
    ConfigNode next = new ScalarConfigNode("bar");
    Truth.assertThat(node.getNext().isPresent()).isFalse();
    Truth.assertThat(node.insertNext(next)).isSameAs(node);
    Truth.assertThat(node.getNext()).isSameAs(next);
  }
}
