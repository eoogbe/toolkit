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

import com.google.api.codegen.configgen.ConfigHelper;
import com.google.api.codegen.configgen.nodes.ConfigNode;
import java.util.List;

/** ConfigMerger for a specific type of ApiModel source. */
public interface ModelConfigMerger {
  /** Merges the initial config. */
  ConfigNode mergeInitial();

  /** Merges a list of existing configs. */
  ConfigNode mergeRefresh(List<ConfigNode> configNodes);

  /** Returns the ConfigHelper that assist with merging. */
  ConfigHelper getHelper();
}
