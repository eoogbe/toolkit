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
package com.google.api.codegen.configgen.nodes;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.Comment;
import com.google.api.codegen.configgen.nodes.metadata.NullComment;

public class ListItemConfigNode extends BaseConfigNode {
  private ConfigNode child;
  private Comment comment;
  private Advice advice;

  public ListItemConfigNode(int startLine) {
    super(startLine, "");
  }

  @Override
  public ConfigNode getChild() {
    return child == null ? new NullConfigNode() : child;
  }

  public Comment getComment() {
    return comment == null ? new NullComment() : comment;
  }

  public Advice getAdvice() {
    return advice;
  }

  @Override
  public ListItemConfigNode setChild(ConfigNode child) {
    checkArgument(this != child, "Cannot set child to this node");
    this.child = child;
    return this;
  }

  public ListItemConfigNode setComment(Comment comment) {
    this.comment = comment;
    return this;
  }

  public ListItemConfigNode setAdvice(Advice advice) {
    this.advice = advice;
    return this;
  }
}
