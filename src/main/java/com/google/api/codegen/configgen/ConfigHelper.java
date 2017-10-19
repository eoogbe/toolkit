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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.Node;

public class ConfigHelper {
  private final DiagCollector diag;
  private final LocationGenerator locationGenerator;

  public ConfigHelper(DiagCollector diag, LocationGenerator locationGenerator) {
    this.diag = diag;
    this.locationGenerator = locationGenerator;
  }

  public int getErrorCount() {
    return diag.getErrorCount();
  }

  public void error(Node node, String message, Object... params) {
    error(node.getStartMark(), message, params);
  }

  public void error(Mark mark, String message, Object... params) {
    error(getLocation(getStartLine(mark)), message, params);
  }

  public void error(String message, Object... params) {
    error(locationGenerator.getLocation(), message, params);
  }

  public void error(Location location, String message, Object... params) {
    diag.addDiag(Diag.error(location, message, params));
  }

  public int getStartLine(Node node) {
    return getStartLine(node.getStartMark());
  }

  public int getStartLine(Mark mark) {
    return mark.getLine() + 1;
  }

  public Location getLocation(int startLine) {
    return locationGenerator.getLocation(startLine);
  }
}
