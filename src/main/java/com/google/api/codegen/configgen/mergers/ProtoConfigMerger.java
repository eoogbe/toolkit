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
package com.google.api.codegen.configgen.mergers;

import com.google.api.codegen.config.ProtoApiModel;
import com.google.api.codegen.configgen.ConfigHelper;
import com.google.api.codegen.configgen.InterfaceTransformer;
import com.google.api.codegen.configgen.PagingParameters;
import com.google.api.codegen.configgen.ProtoInterfaceTransformer;
import com.google.api.codegen.configgen.ProtoPageStreamingTransformer;
import com.google.api.codegen.configgen.ProtoPagingParameters;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Model;
import com.google.protobuf.Api;
import java.util.List;

/** Merges the gapic config from a proto Model into a ConfigNode. */
public class ProtoConfigMerger implements ModelConfigMerger {
  private final Model model;
  private final ConfigHelper helper;

  public ProtoConfigMerger(Model model, ConfigHelper helper) {
    this.model = model;
    this.helper = helper;
  }

  @Override
  public ConfigHelper getHelper() {
    return helper;
  }

  @Override
  public ConfigNode mergeInitial() {
    return createConfigMerger().mergeInitial(new ProtoApiModel(model));
  }

  @Override
  public ConfigNode mergeRefresh(List<ConfigNode> configNodes) {
    return createConfigMerger().mergeRefresh(new ProtoApiModel(model), configNodes);
  }

  private ConfigMerger createConfigMerger() {
    String packageName = getPackageName();
    if (packageName == null) {
      return null;
    }

    CollectionMerger collectionMerger = new CollectionMerger();
    RetryMerger retryMerger = new RetryMerger();
    PagingParameters pagingParameters = new ProtoPagingParameters();
    PageStreamingMerger pageStreamingMerger =
        new PageStreamingMerger(new ProtoPageStreamingTransformer(), pagingParameters, helper);
    MethodMerger methodMerger =
        new MethodMerger(retryMerger, pageStreamingMerger, pagingParameters);
    LanguageSettingsMerger languageSettingsMerger = new LanguageSettingsMerger(helper);
    InterfaceTransformer interfaceTranformer = new ProtoInterfaceTransformer();
    InterfaceMerger interfaceMerger =
        new InterfaceMerger(collectionMerger, retryMerger, methodMerger, interfaceTranformer);
    return new ConfigMerger(languageSettingsMerger, interfaceMerger, packageName, helper);
  }

  private String getPackageName() {
    if (model.getServiceConfig().getApisCount() > 0) {
      Api api = model.getServiceConfig().getApis(0);
      Interface apiInterface = model.getSymbolTable().lookupInterface(api.getName());
      if (apiInterface != null) {
        return apiInterface.getFile().getFullName();
      }
    }

    helper.error(model.getLocation(), "No interface found");
    return null;
  }
}
