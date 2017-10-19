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
package com.google.api.codegen.configgen;

import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.ScalarConfigNode;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

public class MessageGenerator extends NodeVisitor {
  private final FieldDescriptor parentField;
  private Message.Builder parentBuilder;

  public MessageGenerator(Message.Builder parentBuilder) {
    this(parentBuilder, null);
  }

  private MessageGenerator(Message.Builder parentBuilder, FieldDescriptor parentField) {
    this.parentBuilder = parentBuilder;
    this.parentField = parentField;
  }

  @VisitsBefore
  boolean generate(FieldConfigNode node) {
    Descriptor messageType = parentBuilder.getDescriptorForType();
    FieldDescriptor field = messageType.findFieldByName(node.getText());
    if (field == null) {
      parentBuilder = null;
      return false;
    }

    Message.Builder messageBuilder = parentBuilder.newBuilderForField(field);
    MessageGenerator childGenerator = new MessageGenerator(messageBuilder, field);
    childGenerator.visit(node.getChild());
    Message message = childGenerator.toMessage();
    if (message == null) {
      parentBuilder = null;
      return false;
    }

    parentBuilder.setField(field, message);
    return true;
  }

  @VisitsBefore
  boolean generate(ListItemConfigNode node) {
    MessageGenerator childGenerator = new MessageGenerator(parentBuilder, parentField);
    childGenerator.visit(node.getChild());
    Message message = childGenerator.toMessage();
    if (message == null) {
      parentBuilder = null;
      return false;
    }

    parentBuilder.addRepeatedField(parentField, message);
    return true;
  }

  @VisitsBefore
  void generate(ScalarConfigNode node) {
    String text = node.getText().trim();
    if (!text.isEmpty() && !text.startsWith("#")) {
      parentBuilder.setField(parentField, text);
    }
  }

  public Message toMessage() {
    return parentBuilder == null ? null : parentBuilder.build();
  }
}
