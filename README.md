# Geo Transform Extension for WarpScript™

This extension to [Warp 10™](https://github.com/senx/warp10-platform) allows transformation between Coordinate Reference Systems or CRS for short.

It is based on [Proj4J](https://github.com/locationtech/proj4j).

# Adding the extension to Warp 10™

Add to your configuration file:

```
warpscript.extension.geotransform = io.warp10.script.ext.geotransform.GeoTransformWarpScriptExtension
```

## Optional tuning

There is a caching system of transformation instances between CRSs. For each pair of CRSs you use, an instance is kept in cache for faster consecutive usage.

By default 10 transformation instances are kept in memory, which should be sufficient. If you use a lot of different CRSs, you can consider increasing this value by adding to your Warp 10™ configuration file:
```
geotransform.cache.size = 20
```

# Using the function defined in this extension

This extension adds the `GEO.TRANSFORM` function which is called:

```
$x $y $optionalSourceCRS $destinationCRS GEO.TRANSFORM [ 'transf_x' 'transf_y' ] STORE
```

**BE CAREFUL** as `GEO.TRANSFORM` is based on Proj4J, it also uses Proj4J convention which is x=longitude and y=latitude. This is the reverse order Warp 10™ uses.

For the list of available CRSs, check [here](https://github.com/locationtech/proj4j/tree/v1.0.0/src/main/resources/proj4/nad). For instance, you can use `EPSG:4164` for South Yemen CRS.

The source CRS is optional, it defaults to `EPSG:4326`.