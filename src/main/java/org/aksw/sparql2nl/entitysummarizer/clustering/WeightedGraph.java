/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author ngonga
 */
public class WeightedGraph {

    Map<Node, Double> nodes;
    Map<String, Set<Node>> nodeIndex;
    Map<Node, Map<Node, Double>> edges;

    public WeightedGraph() {
        nodes = new HashMap<Node, Double>();
        edges = new HashMap<Node, Map<Node, Double>>();
        nodeIndex = new HashMap<String, Set<Node>>();
    }

    /**
     * Adds a weighted node to the graph
     *
     * @param label label of node
     * @param weight weight of node
     * @return The new node
     */
    public Node addNode(String label, double weight) {
        Node n = new Node(label);
        nodes.put(n, weight);
        if (!nodeIndex.containsKey(label)) {
            nodeIndex.put(label, new HashSet<Node>());
        }
        nodeIndex.get(label).add(n);
        return n;
    }

    public Set<Node> getNeighbors(Node n)
    {
        if(edges.containsKey(n))
        return edges.get(n).keySet();
        else return new HashSet<Node>();
    }
    /**
     * Adds a weighted edge to the graph
     *
     * @param n1 Source node
     * @param n2 Target node
     * @param weight Weight of edge
     * @return True if everything went well, false if not
     */
    public boolean addEdge(Node n1, Node n2, double weight) {
        if (nodes.containsKey(n1) && nodes.containsKey(n2)) {
            if (!edges.containsKey(n1)) {
                edges.put(n1, new HashMap<Node, Double>());
            }
            edges.get(n1).put(n2, weight);
            return true;
        }
        return false;
    }

    /**
     * Adds a weighted edge to the graph
     *
     * @param n1 Source node
     * @param n2 Target node
     * @param weight Weight of edge
     * @return True if everything went well, false if not
     */
    public boolean addSymmetricEdge(Node n1, Node n2, double weight) {
        boolean b1 = addEdge(n1, n2, weight);
        boolean b2 = addEdge(n2, n1, weight);
        return (b1 && b2);
    }

    /**
     * Returns the weight of the edges between two nodes
     *
     * @param n1 Source node
     * @param n2 Target node
     * @return Weight of edges between the two
     */
    public double getEdgeWeight(Node n1, Node n2) {
        if (edges.containsKey(n1)) {
            if (edges.get(n1).containsKey(n2)) {
                return edges.get(n1).get(n2);
            }
        }
        return 0d;
    }
    
    /** 
     * Returns the weight of a node
     * @param n A node
     * @return Its weight
     */
    public double getNodeWeight(Node n) {
        if (nodes.containsKey(n)) {
            return nodes.get(n);
        }
        return -1;
    }

    @Override
    public String toString() {
        String buffer = "";
        for (Node n : edges.keySet()) {
            for (Node n2 : edges.get(n).keySet()) {
                buffer = buffer + n.label + "(" + nodes.get(n) + ")\t" + n2.label + "(" + nodes.get(n2) + ")\t" + edges.get(n).get(n2) + "\n";
            }
        }
        return buffer;
    }

    public Map<Node, Double> getEdges(Node n) {
        return edges.get(n);
    }

    public static void main(String args[]) {
        WeightedGraph wg = new WeightedGraph();
        Node n1 = wg.addNode("a", 1.0);
        Node n2 = wg.addNode("b", 2.0);
        Node n3 = wg.addNode("c", 3.0);
        Node n4 = wg.addNode("d", 4.0);
        wg.addEdge(n1, n2, 2.0);
        wg.addEdge(n3, n4, 1.0);
        wg.addEdge(n2, n3, 2.0);
        System.out.println(wg);
    }
}
