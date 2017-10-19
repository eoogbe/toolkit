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

import com.google.api.codegen.CodegenTestUtil;
import com.google.api.codegen.configgen.mergers.ConfigMerger;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.testing.ConfigBaselineTestCase;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.junit.Before;
import org.junit.Test;

public class AdviserTest extends ConfigBaselineTestCase {
  private String configFileName;
  private Adviser adviser;

  @Override
  public Object run() throws Exception {
    model.addSupressionDirective(model, "control-*");
    model.establishStage(Merged.KEY);
    File configFile = new File(getTestDataLocator().findTestData(configFileName).toURI());
    adviser.advise(model.getDiagCollector(), new ConfigMerger().mergeConfig(model, configFile));
    return "";
  }

  @Before
  public void setup() {
    getTestDataLocator().addTestDataSource(CodegenTestUtil.class, "testsrc");
  }

  @Test
  public void missing_language_settings() throws Exception {
    configFileName = "missing_language_settings_gapic.yaml";
    adviser =
        new Adviser(
            ImmutableList.of(
                "type",
                "license-header",
                "interface",
                "collection",
                "method",
                "retry",
                "field",
                "page-streaming"));
    test("myproto");
  }
}
