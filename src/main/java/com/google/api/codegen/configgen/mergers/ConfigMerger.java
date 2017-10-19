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

import com.google.api.codegen.ConfigProto;
import com.google.api.codegen.configgen.ConfigHelper;
import com.google.api.codegen.configgen.ConfigYamlReader;
import com.google.api.codegen.configgen.InitialConfigLocationGenerator;
import com.google.api.codegen.configgen.MissingFieldTransformer;
import com.google.api.codegen.configgen.NodeFinder;
import com.google.api.codegen.configgen.RefreshConfigLocationGenerator;
import com.google.api.codegen.configgen.StringPairTransformer;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.codegen.configgen.nodes.FieldConfigNode;
import com.google.api.codegen.configgen.nodes.ScalarConfigNode;
import com.google.api.codegen.configgen.nodes.metadata.Advice;
import com.google.api.codegen.configgen.nodes.metadata.FixmeComment;
import com.google.api.codegen.configgen.nodes.metadata.RejectRefreshComment;
import com.google.api.codegen.configgen.nodes.metadata.Rule;
import com.google.api.tools.framework.model.Model;
import java.io.File;

public class ConfigMerger {
  private static final String CONFIG_DEFAULT_COPYRIGHT_FILE = "copyright-google.txt";
  private static final String CONFIG_DEFAULT_LICENSE_FILE = "license-header-apache-2.0.txt";
  private static final String CONFIG_PROTO_TYPE = ConfigProto.getDescriptor().getFullName();
  private static final String REFRESH_CONFIG_COMMENT =
      "Address all the FIXMEs in this generated config before using it for client generation. "
          + "Remove this paragraph after you closed all the FIXMEs.";
  private static final String INITIAL_CONFIG_COMMENT =
      REFRESH_CONFIG_COMMENT
          + " The retry_codes_name, required_fields, flattening, and timeout properties cannot be "
          + "precisely decided by the tooling and may require some configuration.";

  private final LanguageSettingsMerger languageSettingsMerger = new LanguageSettingsMerger();
  private final CollectionMerger collectionMerger = new CollectionMerger();
  private final InterfaceMerger interfaceMerger = new InterfaceMerger();

  public ConfigNode mergeConfig(Model model) {
    ConfigHelper helper =
        new ConfigHelper(model.getDiagCollector(), new InitialConfigLocationGenerator());
    FieldConfigNode configNode = mergeConfig(model, new FieldConfigNode(0, ""), helper);
    if (configNode == null) {
      return null;
    }

    return configNode.setComment(new FixmeComment(INITIAL_CONFIG_COMMENT));
  }

  public ConfigNode mergeConfig(Model model, File file) {
    ConfigHelper helper =
        new ConfigHelper(
            model.getDiagCollector(), new RefreshConfigLocationGenerator(file.getName()));
    FieldConfigNode configNode = new ConfigYamlReader().generateConfigNode(file, helper);
    if (configNode == null) {
      return null;
    }

    FieldConfigNode mergedNode = mergeConfig(model, configNode, helper);
    if (mergedNode == null) {
      return null;
    }

    return mergedNode.setComment(new FixmeComment(REFRESH_CONFIG_COMMENT));
  }

  private FieldConfigNode mergeConfig(
      Model model, FieldConfigNode configNode, ConfigHelper helper) {
    ConfigNode typeNode = mergeType(configNode, helper);
    if (typeNode == null) {
      return null;
    }

    ConfigNode languageSettingsNode =
        languageSettingsMerger.mergeLanguageSettings(model, configNode, typeNode, helper);
    if (languageSettingsNode == null) {
      return null;
    }

    mergeLicenseHeader(configNode, languageSettingsNode, helper);
    collectionMerger.checkCollections(configNode, helper);
    interfaceMerger.mergeInterfaces(model, configNode, helper);

    return configNode;
  }

  private ConfigNode mergeType(ConfigNode configNode, ConfigHelper helper) {
    FieldConfigNode typeNode = MissingFieldTransformer.prepend("type", configNode).generate();

    if (!NodeFinder.hasChild(typeNode)) {
      return typeNode
          .setChild(new ScalarConfigNode(typeNode.getStartLine(), CONFIG_PROTO_TYPE))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.TYPE)
                  .comment(
                      "Expected a field 'type' specifying the configuration type name in root "
                          + "object.")
                  .location(helper.getLocation(typeNode.getStartLine()))
                  .hasSuggestion(true)
                  .build());
    }

    String type = typeNode.getChild().getText();
    if (CONFIG_PROTO_TYPE.equals(type)) {
      return typeNode;
    }

    helper.error("The specified configuration type '%s' is unknown.", type);
    return null;
  }

  private void mergeLicenseHeader(ConfigNode configNode, ConfigNode prevNode, ConfigHelper helper) {
    FieldConfigNode licenseHeaderNode =
        MissingFieldTransformer.insert("license_header", configNode, prevNode).generate();

    if (NodeFinder.hasChild(licenseHeaderNode)) {
      FieldConfigNode copyrightFileNode =
          MissingFieldTransformer.prepend("copyright_file", licenseHeaderNode).generate();
      if (!NodeFinder.hasChild(copyrightFileNode)) {
        copyrightFileNode
            .setChild(
                new ScalarConfigNode(
                    copyrightFileNode.getStartLine(), CONFIG_DEFAULT_COPYRIGHT_FILE))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.LICENSE_HEADER)
                    .comment("Missing 'copyright_file'")
                    .location(helper.getLocation(copyrightFileNode.getStartLine()))
                    .hasSuggestion(true)
                    .build());
      }

      FieldConfigNode licenseFileNode =
          MissingFieldTransformer.insert("license_file", licenseHeaderNode, copyrightFileNode)
              .generate();
      if (!NodeFinder.hasChild(licenseFileNode)) {
        licenseFileNode
            .setChild(
                new ScalarConfigNode(licenseFileNode.getStartLine(), CONFIG_DEFAULT_LICENSE_FILE))
            .setAdvice(
                Advice.newBuilder()
                    .rule(Rule.LICENSE_HEADER)
                    .comment("Missing 'license_file'")
                    .location(helper.getLocation(licenseFileNode.getStartLine()))
                    .hasSuggestion(true)
                    .build());
      }
    } else {
      FieldConfigNode copyrightFileNode =
          StringPairTransformer.generateStringPair(
              NodeFinder.getNextLine(licenseHeaderNode),
              "copyright_file",
              CONFIG_DEFAULT_COPYRIGHT_FILE);
      copyrightFileNode.setComment(
          new RejectRefreshComment("The file containing the copyright line(s)."));
      FieldConfigNode licenseFileNode =
          StringPairTransformer.generateStringPair(
              NodeFinder.getNextLine(copyrightFileNode),
              "license_file",
              CONFIG_DEFAULT_LICENSE_FILE);
      licenseFileNode.setComment(
          new RejectRefreshComment(
              "The file containing the raw license header without any copyright line(s)."));
      copyrightFileNode.insertNext(licenseFileNode);
      licenseHeaderNode
          .setChild(copyrightFileNode)
          .setComment(
              new RejectRefreshComment(
                  "The configuration for the license header to put on generated files."))
          .setAdvice(
              Advice.newBuilder()
                  .rule(Rule.LICENSE_HEADER)
                  .comment("Missing 'license_header'")
                  .location(helper.getLocation(licenseHeaderNode.getStartLine()))
                  .hasSuggestion(true)
                  .build());
    }
  }
}
