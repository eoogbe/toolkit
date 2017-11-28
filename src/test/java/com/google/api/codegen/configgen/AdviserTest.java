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

import com.google.api.codegen.CodegenTestUtil;
import com.google.api.codegen.configgen.mergers.ProtoConfigMerger;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.testing.ConfigBaselineTestCase;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AdviserTest extends ConfigBaselineTestCase {
  private String name;

  @Parameters(name = "{0}")
  public static Collection<Object[]> testedConfigs() {
    return Arrays.asList(
        new Object[][] {
          {"missing_config_schema_version"}, {"missing_language_settings"}, {"missing_package_name"}
        });
  }

  public AdviserTest(String name) {
    this.name = name;
  }

  @Override
  public Object run() throws Exception {
    model.addSupressionDirective(model, "control-*");
    model.getDiagSuppressor().addPattern(model, "http:.*");
    model.establishStage(Merged.KEY);
    String configFileName = name + "_gapic.yaml";
    List<File> configFiles =
        CodegenTestUtil.pathsToFiles(getTestDataLocator(), new String[] {configFileName});
    ConfigHelper helper = new ConfigHelper(model.getDiagCollector(), configFileName);
    ProtoConfigMerger configMerger = new ProtoConfigMerger(model, helper);
    ConfigNode configNode = new MultiConfigYamlReader().readConfigNode(configMerger, configFiles);
    if (configNode != null) {
      new Adviser(ImmutableList.of()).advise(model.getDiagCollector(), configNode);
    }
    return "";
  }

  @Override
  protected String baselineFileName() {
    return name + ".baseline";
  }

  @Before
  public void setup() {
    getTestDataLocator().addTestDataSource(CodegenTestUtil.class, "testsrc");
  }

  @Test
  public void test() throws Exception {
    test("library");
  }
}
