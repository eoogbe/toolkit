/* Copyright 2016 Google LLC
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
package com.google.api.codegen;

import com.google.api.codegen.configgen.MessageGenerator;
import com.google.api.codegen.configgen.MultiConfigYamlReader;
import com.google.api.codegen.configgen.mergers.ModelConfigMerger;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.api.tools.framework.setup.StandardSetup;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class CodegenTestUtil {

  public static Model readModel(
      TestDataLocator locator, TemporaryFolder tempDir, String[] protoFiles, String[] yamlFiles) {
    TestConfig testConfig =
        new TestConfig(locator, tempDir.getRoot().getPath(), Arrays.asList(protoFiles));
    Model model = testConfig.createModel(Arrays.asList(yamlFiles));
    StandardSetup.registerStandardProcessors(model);
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);
    return model;
  }

  public static ConfigProto readConfig(
      ModelConfigMerger configMerger,
      TestDataLocator testDataLocator,
      String[] gapicConfigFileNames) {
    List<File> configFiles = pathsToFiles(testDataLocator, gapicConfigFileNames);
    ConfigNode configNode = new MultiConfigYamlReader().readConfigNode(configMerger, configFiles);
    if (configMerger.getHelper().getErrorCount() > 0) {
      System.err.println(configMerger.getHelper().getCollectedErrors());
      return null;
    }

    MessageGenerator messageGenerator = new MessageGenerator(ConfigProto.newBuilder());
    messageGenerator.visit(configNode.getChild());
    return (ConfigProto) messageGenerator.getValue();
  }

  public static List<File> pathsToFiles(TestDataLocator testDataLocator, String[] configFileNames) {
    List<File> files = new ArrayList<>();
    for (String configFileName : configFileNames) {
      URL configUrl = testDataLocator.findTestData(configFileName);
      File configFile = null;
      try {
        configFile = new File(configUrl.toURI());
      } catch (URISyntaxException e) {
        continue;
      }

      files.add(configFile);
    }
    return files;
  }
}
