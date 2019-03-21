//
//   Copyright 2019  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.ext.geotransform;

import io.warp10.WarpConfig;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.InvalidValueException;
import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.UnknownAuthorityCodeException;
import org.locationtech.proj4j.UnsupportedParameterException;

import java.util.LinkedHashMap;
import java.util.Map;

public class GEOTRANSFORM extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  /**
   * Class to define pair of Coordinate Reference Systems (CRSs) from source to target.
   * Used as a key for the caching map.
   */
  private class CrsPair {

    private final String source;
    private final String target;

    private CrsPair(String source, String target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CrsPair)) {
        return false;
      }
      CrsPair crsPair = (CrsPair) o;
      return source.equals(crsPair.source) && target.equals(crsPair.target);
    }

    @Override
    public int hashCode() {
      return source.hashCode() + 31 * target.hashCode();
    }

  }

  private static final String CRS_CACHE_SIZE_PROPERTY_NAME = "geotransform.cache.size";
  private static int DEFAULT_CRS_CACHE_SIZE = 10;

  private final int crs_cache_size;

  /**
   * A Map implementing a cache for CoordinateTransform.
   * All calls to get with valid CrsPair return a valid CoordinateTransform either from the map or create a new instance
   * and add it tot the map. The Size of the map is constrained to avoid unruly growth.
   */
  private final Map<CrsPair, CoordinateTransform> crsTransformCache;

  /**
   * An instance of CRSFactory to create CoordinateReferenceSystem instances from CRS names.
   */
  private CRSFactory crsFactory;

  /**
   * An instance of CoordinateTransformFactory to create CoordinateTransform instances from pairs of CoordinateReferenceSystem
   */
  private CoordinateTransformFactory crsTransformFactory;

  public GEOTRANSFORM(String name) {
    super(name);
    crsFactory = new CRSFactory();
    crsTransformFactory = new CoordinateTransformFactory();

    crs_cache_size = Integer.valueOf(WarpConfig.getProperties().getProperty(CRS_CACHE_SIZE_PROPERTY_NAME, Integer.toString(DEFAULT_CRS_CACHE_SIZE)));

    crsTransformCache = new LinkedHashMap<CrsPair, CoordinateTransform>() {
      @Override
      protected boolean removeEldestEntry(java.util.Map.Entry<CrsPair, CoordinateTransform> eldest) {
        return this.size() > crs_cache_size;
      }

      @Override
      public CoordinateTransform get(Object key) {
        CoordinateTransform crst = super.get(key);

        if (null == crst) {
          CrsPair crsPair = (CrsPair) key;
          CoordinateReferenceSystem sourceCRS = crsFactory.createFromName(crsPair.source);
          CoordinateReferenceSystem destCRS = crsFactory.createFromName(crsPair.target);
          crst = crsTransformFactory.createTransform(sourceCRS, destCRS);
          this.put(crsPair, crst);
        }

        return crst;
      }
    };
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    // Get the target CRS name
    Object o1 = stack.pop();

    if (!(o1 instanceof String)) {
      throw new WarpScriptException(getName() + " expects a target CRS on top of the stack.");
    }

    String targetCRS = o1.toString();

    // Get the source CRS if defined. If not, defaults to EPSG:4326, which is the lat/lon used in Warp 10.
    String sourceCRS = "EPSG:4326"; // Default
    if (stack.peek() instanceof String) {
      // Source
      Object o2 = stack.pop();

      if (!(o2 instanceof String)) {
        throw new WarpScriptException(getName() + " expects a source CRS under the target CRS.");
      }

      sourceCRS = o2.toString();
    }

    // y coordinate
    Object o3 = stack.pop();

    if (!(o3 instanceof Number)) {
      throw new WarpScriptException(getName() + " expects a double `y` under the source CRS.");
    }

    double y = ((Number) o3).doubleValue();

    // x coordinate
    Object o4 = stack.pop();

    if (!(o4 instanceof Number)) {
      throw new WarpScriptException(getName() + " expects a double `x` under the double `y`.");
    }

    double x = ((Number) o4).doubleValue();

    // Get the CoordinateTransform, cached instance or new one if not in the cache.
    CoordinateTransform transform;
    try {
      transform = crsTransformCache.get(new CrsPair(sourceCRS, targetCRS));
    }
    catch (UnsupportedParameterException | InvalidValueException | UnknownAuthorityCodeException ex){
      throw new WarpScriptException(getName()+" expects valid CRS names.", ex);
    }

    // Actual computation
    ProjCoordinate srcCoord = new ProjCoordinate(x, y);
    ProjCoordinate destCoord = new ProjCoordinate();
    transform.transform(srcCoord, destCoord);

    // Push result on stack
    stack.push(destCoord.x);
    stack.push(destCoord.y);

    return stack;

  }
}
