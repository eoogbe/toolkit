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
package com.google.api.codegen.config;

import com.google.api.tools.framework.aspects.http.model.HttpAttribute.FieldSegment;
import com.google.api.tools.framework.model.Interface;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Class for collection config generator.
 */
public class CollectionConfigGenerator {

  private static final String CONFIG_KEY_NAME_PATTERN = "name_pattern";
  private static final String CONFIG_KEY_ENTITY_NAME = "entity_name";

  public List<Object> generate(Interface service) {
    List<Object> output = new LinkedList<Object>();

    Iterable<FieldSegment> segments = Resources.getFieldSegmentsFromHttpPaths(service.getMethods());
    Map<String, String> nameMap = Resources.getResourceToEntityNameMap(segments);
    for (FieldSegment segment : segments) {
      Map<String, Object> collectionMap = new LinkedHashMap<String, Object>();
      String resourceNameString = Resources.templatize(segment);
      collectionMap.put(CONFIG_KEY_NAME_PATTERN, resourceNameString);
      collectionMap.put(CONFIG_KEY_ENTITY_NAME, nameMap.get(resourceNameString));
      output.add(collectionMap);
    }
    return output;
  }
}