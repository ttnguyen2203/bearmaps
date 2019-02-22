import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {
    private final double[] DEPTH_SCALE = generateDepthScale();
    private final double SL = 288200;

    public Rasterer() {
        // YOUR CODE HERE
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     *                    forget to set this to true on success! <br>
     */

    //Higher K in x direction is RIGHT
    //Higher K in y direction is DOWN
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        System.out.println(params);
        Map<String, Object> results = new HashMap<>();
        //System.out.println("Since you haven't implemented getMapRaster, nothing is displayed in "
        //                   + "your browser.");
        if (checkQueryBound(params)) {
            results.put("depth", 0.0);
            results.put("raster_ul_lon", 0.0);
            results.put("raster_ul_lat", 0.0);
            results.put("raster_lr_lon", 0.0);
            results.put("raster_lr_lat", 0.0);
            results.put("render_grid", null);
            results.put("query_success", false);
            return results;
        }

        int depth = findDepth(params);
        results.put("depth", depth);

        //find which tiles;
        Map<String, Integer> num = findXYtileSpan(params, depth);
        String[][] tiles = tileNameParser(num, depth);
        results.put("render_grid", tiles);

        //rastered measurements
        Map<String, Double> corners = cornerParser(num, depth);
        coordTransfer(results, corners);

        results.put("query_success", true);

        return results;
    }

    //calculates lonDPP
    private double findLonDPP(double left, double right, double width) {
        return (right - left) / width;
    }

    //compares given params with depthscale to get depth
    private int findDepth(Map<String, Double> params) {
        double ulLong = params.get("ullon");
        double lrLong = params.get("lrlon");
        double width = params.get("w");
        double queryDPP = findLonDPP(ulLong, lrLong, width);
        for (int i = 0; i < DEPTH_SCALE.length; i += 1) {
            double scaleDPP = DEPTH_SCALE[i];
            if (scaleDPP <= queryDPP) {
                return i;
            }
        }
        return 7; //if queryDPP is less than D7, use 7
    }

    //creates depth scale during instantiation
    private double[] generateDepthScale() {
        double[] depthScale = new double[8];
        double upLeft = MapServer.ROOT_ULLON;
        double loRight = MapServer.ROOT_LRLON;
        double tileSize = MapServer.TILE_SIZE;
        for (int i = 0; i < 8; i += 1) {
            double difference = loRight - upLeft;
            depthScale[i] = difference / tileSize;
            loRight = loRight - (difference / 2);
        }
        return depthScale;
    }

    private Map<String, Integer> findXYtileSpan(Map<String, Double> params, int depth) {
        Map<String, Integer> xyCoords = new HashMap<>();

        //number of tiles in X direction at given depth
        double increments = Math.pow(2, depth);

        //how long one tile is
        double xUnit = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / increments;

        //x tile number of leftmost tile and rightmost tile
        int xleftLon = incrementHelperLon(MapServer.ROOT_ULLON, params.get("ullon"), xUnit);
        xyCoords.put("LeftX", checkTileIndex(xleftLon, depth));
        int xrightLon = incrementHelperLon(MapServer.ROOT_ULLON, params.get("lrlon"), xUnit);
        xyCoords.put("RightX", checkTileIndex(xrightLon, depth));


        //how tall one tile is
        double yUnit = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / increments;

        //Y tile number of highest tile and lowest tile
        //This is trickier because scale increases downward but lat increases upward
        int yleftLat = incrementHelperLat(MapServer.ROOT_ULLAT, params.get("ullat"), yUnit);
        xyCoords.put("LeftY", checkTileIndex(yleftLat, depth));
        int yrightLat = incrementHelperLat(MapServer.ROOT_ULLAT, params.get("lrlat"), yUnit);
        xyCoords.put("RightY", checkTileIndex(yrightLat, depth));
        return xyCoords;
    }
    private Map<String, Double> cornerParser(Map<String, Integer> xy, int depth) {
        int lX = xy.get("LeftX");
        int rX = xy.get("RightX") + 1;
        int lY = xy.get("LeftY");
        int rY = xy.get("RightY") + 1;
        double increments = Math.pow(2, depth);
        double xUnit = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / increments;
        double yUnit = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / increments;
        Map<String, Double> result = new HashMap<>();
        result.put("raster_ul_lon", lX * xUnit + MapServer.ROOT_ULLON);

        result.put("raster_lr_lon", rX * xUnit + MapServer.ROOT_ULLON);

        result.put("raster_ul_lat", MapServer.ROOT_ULLAT - lY * yUnit);

        result.put("raster_lr_lat", MapServer.ROOT_ULLAT - rY * yUnit);
        return result;
    }
    private void coordTransfer(Map<String, Object> result, Map<String, Double> corners) {
        for (String i: corners.keySet()) {
            result.put(i, corners.get(i));
        }
    }

    private int incrementHelperLon(double root, double dimension, double unitLength) {
        double zeroing = Math.abs(dimension - root);
        return (int) Math.floor(zeroing / unitLength);
    }
    private int incrementHelperLat(double root, double dimension, double unitLength) {
        double zeroing = Math.abs(root - dimension);
        return (int) Math.floor(zeroing / unitLength);
    }

    private String[][] tileNameParser(Map<String, Integer> xy, int depth) {
        int lX = xy.get("LeftX");
        int rX = xy.get("RightX");
        int lY = xy.get("LeftY");
        int rY = xy.get("RightY");
        String[][] tiles = new String[rY - lY + 1][rX - lX + 1];
        for (int j = 0, y = lY; y < rY + 1; j += 1, y += 1) {
            for (int i = 0, x = lX; x < rX + 1; i += 1, x += 1) {
                tiles[j][i] = fileNameWriter(x, y, depth);
            }
        }
        return tiles;
    }
    private String fileNameWriter(int x, int y, int depth) {
        return "d" + Integer.toString(depth) + "_x"
                + Integer.toString(x) + "_y" + Integer.toString(y) + ".png";
    }

    //if query box is out of tiles available, return only available tiles
    private int checkTileIndex(int a, int depth) {
        int max = (int) Math.pow(2, depth) - 1;
        if (a < 0) {
            return 0;
        } else if (a > max) {
            return max;
        }
        return a;
    }

    //checks for edge cases in Lon and Lat
    private boolean checkQueryBound(Map<String, Double> params) {
        if (params.get("ullon") < MapServer.ROOT_ULLON
                && params.get("ullat") > MapServer.ROOT_ULLAT
                && params.get("lrlon") > MapServer.ROOT_LRLON
                && params.get("lrlat") < MapServer.ROOT_LRLAT) {
            return true;
        }
        if (params.get("ullon") > params.get("lrlon")
                || params.get("ullat") < params.get("lrlat")) {
            return true;
        }
        return false;
    }
}
