import org.xml.sax.SAXException;

import java.awt.geom.Line2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.util.*;

/**
 * Graph for storing all of the intersection (vertex) and road (edge) information.
 * Uses your GraphBuildingHandler to convert the XML files into a graph. Your
 * code must include the vertices, adjacent, distance, closest, lat, and lon
 * methods. You'll also need to include instance variables and methods for
 * modifying the graph (e.g. addNode and addEdge).
 *
 * @author Alan Yao, Josh Hug
 */
public class GraphDB {
    /** Your instance variables for storing the graph. You should consider
     * creating helper classes, e.g. Node, Edge, etc. */

    //store vertexes in Map ID --> node
    Map<Long, Node> graph = new LinkedHashMap<>();

    //store names in trie for prefix search
    Trie search = new Trie();

    //store removed nodes for searching purposes, also add these to search Trie
    Map<Long, Node> removedNodes = new LinkedHashMap<>();
    int V;
    int E;
    int way;



    /**
     * @param dbPath Path to the XML file to be parsed.
     */
    public GraphDB(String dbPath) {
        try {
            File inputFile = new File(dbPath);
            FileInputStream inputStream = new FileInputStream(inputFile);
            // GZIPInputStream stream = new GZIPInputStream(inputStream);
            V = 0;
            E = 0;
            way = 0;

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            GraphBuildingHandler gbh = new GraphBuildingHandler(this);
            saxParser.parse(inputStream, gbh);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        Iterator<Long> v = vertices().iterator();
        while (v.hasNext()) {
            long i = v.next();
            if (nodeAt(i).adj.isEmpty()) {
                if (nodeAt(i).extraInfo.containsKey("name")) {
                    removedNodes.put(i, nodeAt(i));
                    search.add(nodeAt(i).extraInfo.get("name"), i);
                }
                v.remove();
                graph.remove(i);
                V -= 1;
            }

        }
    }

    /**
     * Returns an iterable of all vertex IDs in the graph.
     * @return An iterable of id's of all vertices in the graph.
     */
    Iterable<Long> vertices() {
        return graph.keySet();
    }

    /**
     * Returns ids of all vertices adjacent to v.
     * @param v The id of the vertex we are looking adjacent to.
     * @return An iterable of the ids of the neighbors of v.
     */
    Iterable<Long> adjacent(long v) {
        return nodeAt(v).adj;
    }

    /**
     * Returns the great-circle distance between vertices v and w in miles.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The great-circle distance between the two locations from the graph.
     */
    double distance(long v, long w) {
        return distance(lon(v), lat(v), lon(w), lat(w));
    }

    static double distance(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double dphi = Math.toRadians(latW - latV);
        double dlambda = Math.toRadians(lonW - lonV);

        double a = Math.sin(dphi / 2.0) * Math.sin(dphi / 2.0);
        a += Math.cos(phi1) * Math.cos(phi2) * Math.sin(dlambda / 2.0) * Math.sin(dlambda / 2.0);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 3963 * c;
    }

    /**
     * Returns the initial bearing (angle) between vertices v and w in degrees.
     * The initial bearing is the angle that, if followed in a straight line
     * along a great-circle arc from the starting point, would take you to the
     * end point.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The initial bearing between the vertices.
     */
    double bearing(long v, long w) {
        return bearing(lon(v), lat(v), lon(w), lat(w));
    }

    static double bearing(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double lambda1 = Math.toRadians(lonV);
        double lambda2 = Math.toRadians(lonW);

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2);
        x -= Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Returns the vertex closest to the given longitude and latitude.
     * @param lon The target longitude.
     * @param lat The target latitude.
     * @return The id of the node in the graph closest to the target.
     */
    long closest(double lon, double lat) {
        long dummyValue = Long.MAX_VALUE;
        Node dummy = new Node(dummyValue, lon, lat);
        addNode(dummy);
        long closestID = dummyValue;
        double closestDistance = Double.MAX_VALUE;
        for (long i: vertices()) {
            if (i == dummyValue) {
                continue;
            }
            double distance = distance(dummy.id, i);
            if (distance < closestDistance) {
                closestID = i;
                closestDistance = distance;
            }
        }
        removeNode(dummyValue);
        return closestID;
    }

    /**
     * Gets the longitude of a vertex.
     * @param v The id of the vertex.
     * @return The longitude of the vertex.
     */
    double lon(long v) {
        return nodeAt(v).lon;
    }

    /**
     * Gets the latitude of a vertex.
     * @param v The id of the vertex.
     * @return The latitude of the vertex.
     */
    double lat(long v) {
        return nodeAt(v).lat;
    }

    /**
     * Vertex class
     */
    protected class Node {
        Long id;
        Double lat;
        Double lon;
        Set<Long> adj;
        Map<String, String> extraInfo;

        Node(String id, String lon, String lat) {
            this.id = Long.valueOf(id);
            this.lat = Double.valueOf(lat);
            this.lon = Double.valueOf(lon);
            adj = new HashSet<>();
            extraInfo = new HashMap<>();
        }

        Node(Long id, Double lon, Double lat) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
            adj = new HashSet<>();
            extraInfo = new HashMap<>();
        }
    }

    Node nodeAt(long v) {
        return graph.get(v);
    }

    void removeNode(long v) {
        for (long i: adjacent(v)) {
            Node checking = nodeAt(i);
            checking.adj.remove(v);
            E -= 1;
        }
        graph.remove(v);
        V -= 1;
    }
    void addNode(Node v) {
        graph.put(v.id, v);
        V += 1;
    }
    void addEdge(long v, long w) {
        nodeAt(v).adj.add(w);
        nodeAt(w).adj.add(v);
        E += 1;
    }

    Node makeNode(long id, double lon, double lat) {
        return new Node(id, lon, lat);
    }

    Node makeNode(String id, String lon, String lat) {
        return new Node(id, lon, lat);
    }


    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" : Number, The latitude of the node. <br>
     * "lon" : Number, The longitude of the node. <br>
     * "name" : String, The actual name of the node. <br>
     * "id" : Number, The id of the node. <br>
     */
    public List<Map<String, Object>> getLocations(String locationName) {
        List<Map<String, Object>> result = new LinkedList<>();
        String cleaned = cleanString(locationName);
        Trie.TrieNode node = search.findTrieNode(cleaned);
        if (node == null || node.locationID == null) {
            return result;
        }
        for (long i: node.locationID) {
            Node temp = nodeAt(i);
            if (temp == null) {
                temp = removedNodes.get(i);
            }
            Map<String, Object> a = new HashMap<>();
            a.put("lat", temp.lat);
            a.put("lon", temp.lon);
            a.put("name", temp.extraInfo.get("name"));
            a.put("id", i);
            result.add(a);
        }
        return result;
    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public List<String> getLocationsByPrefix(String prefix) {
        return search.find(prefix);
    }

    /**
     * @source: CS61B-Sp17: Lec37
     * @source: http://www.wikiwand.com/en/Trie
     */
    public class Trie {
        TrieNode root;
        public Trie() {
            root = new TrieNode();
        }
        public class TrieNode {
            boolean exists;
            Map<Character, TrieNode> links;
            List<String> fullName; //prevent collision
            Set<Long> locationID;
            public TrieNode() {
                links = new HashMap<>();
                exists = false;
                fullName = null;
                locationID = null;
            }
        }

        public void add(String name, long id) {
            String cleaned = cleanString(name);
            char[] working = cleaned.toCharArray();
            TrieNode current = root;

            if (cleaned.equals("")) { //edge case for numbered names
                current.exists = true;
                if (current.fullName == null) {
                    current.fullName = new ArrayList<>();
                }
                current.fullName.add(name);
                if (current.locationID == null) {
                    current.locationID = new HashSet<>();
                }
                current.locationID.add(id);
            }

            int endCount = 0;
            for (char i: working) {
                endCount += 1;
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                } else {
                    current.links.put(i, new TrieNode());
                    current = current.links.get(i);
                }
                if (endCount == working.length) { // last node
                    current.exists = true;
                    if (current.fullName == null) { //save memory
                        current.fullName = new ArrayList<>();
                    }
                    current.fullName.add(name);
                    if (current.locationID == null) {
                        current.locationID = new HashSet<>();
                    }
                    current.locationID.add(id);
                }
            }
        }

        //retrieve all strings children of this node
        public void retrieve(TrieNode a, Set<String> result) {
            if (a.exists) {
                for (String i: a.fullName) {
                    result.add(i);
                }

            }
            for (char i: a.links.keySet()) {
                retrieve(a.links.get(i), result);
            }
        }
        public List<String> find(String prefix) {
            String cleaned = cleanString(prefix);
            char[] working = cleaned.toCharArray();
            TrieNode current = root;
            for (char i: working) {
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                } else if (current.links.containsKey(Character.toUpperCase(i))) {
                    current = current.links.get(Character.toUpperCase(i));
                } else {
                    return null;
                }
            }
            //current is now node at last letter, prefix is available
            Set<String> result = new HashSet<>();
            retrieve(current, result);
            return new LinkedList<>(result);
        }

        private TrieNode findTrieNode(String name) {
            char[] working = name.toCharArray();
            TrieNode current = root;
            for (char i: working) {
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                }  else {
                    return null;
                }
            }
            return current;
        }
    }


}
