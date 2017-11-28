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

import com.google.api.tools.framework.model.Location;
import com.google.auto.value.AutoValue;

/** Represents a piece of advice the Adviser can give. */
@AutoValue
public abstract class Advice {
  /** The rule that categorizes this advice. */
  public abstract Rule rule();

  /** The content of this advice. */
  public abstract String comment();

  /** Where this advice is applicable. */
  public abstract Location location();

  /** The name of the element this advice applies to. */
  public abstract String elementName();

  /** True if this advice has a snippet suggestion. */
  public abstract boolean hasSuggestion();

  public static Builder newBuilder() {
    return new AutoValue_Advice.Builder().elementName("").hasSuggestion(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder rule(Rule val);

    public abstract Builder comment(String val);

    public abstract Builder location(Location val);

    public abstract Builder elementName(String val);

    public abstract Builder hasSuggestion(boolean val);

    public abstract Advice build();
  }
}
