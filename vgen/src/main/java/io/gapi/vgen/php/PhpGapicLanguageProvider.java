/* Copyright 2016 Google Inc
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
package io.gapi.vgen.php;

import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.collect.Multimap;

import io.gapi.vgen.ApiConfig;
import io.gapi.vgen.GapicLanguageProvider;
import io.gapi.vgen.GeneratedResult;
import io.gapi.vgen.InputElementView;
import io.gapi.vgen.SnippetDescriptor;

import java.io.IOException;

/**
 * The LanguageProvider which runs Gapic code generation for PHP.
 */
public class PhpGapicLanguageProvider<InputElementT extends ProtoElement>
    implements GapicLanguageProvider<InputElementT> {

  private final PhpGapicContext context;
  private final PhpLanguageProvider provider;
  private InputElementView<InputElementT> view;

  public PhpGapicLanguageProvider(
      Model model, ApiConfig apiConfig, InputElementView<InputElementT> view) {
    this.context = new PhpGapicContext(model, apiConfig);
    this.provider = new PhpLanguageProvider();
    this.view = view;
  }

  @Override
  public Model getModel() {
    return context.getModel();
  }

  @Override
  public <Element> void output(
      String outputPath, Multimap<Element, GeneratedResult> elements)
      throws IOException {
    provider.output("", outputPath, elements);
  }

  @Override
  public GeneratedResult generate(InputElementT element, SnippetDescriptor snippetDescriptor) {
    return provider.generate(element, snippetDescriptor, context);
  }

  @Override
  public InputElementView<InputElementT> getView() {
    return view;
  }
}