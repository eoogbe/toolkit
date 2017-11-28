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
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ListItemConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.Comment;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;

/**
 * Gives advice to the user as a console warning based on rules.
 *
 * <p>Advice can be suppressed through suppressors in the format
 * rule-name[@element-name[|element-name...]] where rule-name is the name of an adviser rule and
 * element-name is the full name of a ProtoElement in the service protos. For example, "method"
 * suppresses all method rules, while "method@example.FooService" suppresses only the method rules
 * on the example.FooService methods.
 */
public class Adviser {
  private final Collection<String> suppressors;

  public Adviser(Collection<String> suppressors) {
    this.suppressors = suppressors;
  }

  /**
   * Gives the advice as console warnings.
   *
   * @param diag Collects the console warnings
   * @param configNode The merged config node
   */
  public void advise(final DiagCollector diag, ConfigNode configNode) {
    final Map<String, Collection<String>> suppressorMap = generateSuppressorMap();
    new NodeVisitor() {
      @VisitsBefore
      void advise(FieldConfigNode node) {
        Advice advice = node.getAdvice();
        if (advice != null && canGiveAdvice(advice, suppressorMap)) {
          giveAdvice(advice, diag, node);
        }
        visit(node.getChild());
      }

      @VisitsBefore
      void collectAdvice(ListItemConfigNode node) {
        Advice advice = node.getAdvice();
        if (canGiveAdvice(advice, suppressorMap)) {
          giveAdvice(advice, diag, node);
        }
        visit(node.getChild());
      }
    }.visit(configNode);
  }

  private Map<String, Collection<String>> generateSuppressorMap() {
    ImmutableMap.Builder<String, Collection<String>> suppressorMap = ImmutableMap.builder();
    for (String suppressor : suppressors) {
      int delimeterIndex = suppressor.indexOf("@");
      if (delimeterIndex > 0) {
        String ruleName = suppressor.substring(0, delimeterIndex);
        String elementsStr = suppressor.substring(delimeterIndex + 1);
        Collection<String> elements =
            Splitter.on('|').trimResults().omitEmptyStrings().splitToList(elementsStr);
        suppressorMap.put(ruleName, elements);
      } else if (delimeterIndex < 0) {
        suppressorMap.put(suppressor, ImmutableList.<String>of());
      }
    }
    return suppressorMap.build();
  }

  private boolean canGiveAdvice(Advice advice, Map<String, Collection<String>> suppressorMap) {
    if (advice == null) {
      return false;
    }

    if (!suppressorMap.containsKey(advice.rule().toString())) {
      return true;
    }

    Collection<String> suppressedElements = suppressorMap.get(advice.rule().toString());

    if (suppressedElements.isEmpty()) {
      return false;
    }

    if (advice.elementName().isEmpty()) {
      return true;
    }

    for (String suppressedElement : suppressedElements) {
      if (advice.elementName().startsWith(suppressedElement)) {
        return false;
      }
    }
    return true;
  }

  private void giveAdvice(Advice advice, DiagCollector diag, ConfigNode node) {
    diag.addDiag(
        Diag.warning(
            advice.location(),
            "(advice) %s: %s%s",
            advice.rule(),
            advice.comment(),
            advice.hasSuggestion() ? getSuggestion(node) : ""));
  }

  private String getSuggestion(ConfigNode node) {
    StringBuilder builder = new StringBuilder();
    builder.append(System.lineSeparator()).append("Did you mean:").append(System.lineSeparator());
    String key = node.getText();
    int indent = 2;
    if (!key.isEmpty()) {
      builder.append("  ").append(key).append(":").append(System.lineSeparator());
      indent = 4;
    }
    ConfigGenerator snippetGenerator = new ConfigGenerator(Comment.Type.NONE, indent, builder);
    snippetGenerator.visit(node.getChild());
    return snippetGenerator.toString();
  }
}
