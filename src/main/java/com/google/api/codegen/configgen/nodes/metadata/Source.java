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
package com.google.api.codegen.configgen.nodes.metadata;

import com.google.auto.value.AutoValue;

/** Represents the source location of a ConfigNode. */
@AutoValue
public abstract class Source {
  public abstract int startLine();

  public abstract String fileName();

  public static Source forNextLine(Source source) {
    return create(source.startLine() + 1, source.fileName());
  }

  public static Source create(int startLine, String fileName) {
    return new AutoValue_Source(startLine, fileName);
  }
}
