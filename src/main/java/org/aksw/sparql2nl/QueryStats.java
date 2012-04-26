package org.aksw.sparql2nl;

import org.aksw.sparql2nl.queryprocessing.Query;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import simpack.accessor.graph.SimpleGraphAccessor;
import simpack.api.IGraphNode;

//import com.hp.hpl.jena.query.Query;
//import com.hp.hpl.jena.query.QueryFactory;
//import com.hp.hpl.jena.sparql.algebra.Algebra;
//import com.hp.hpl.jena.sparql.algebra.Op;

public class QueryStats {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//        String queryString = "PREFIX dbo: <http://dbpedia.org/ontology/> "
//                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
//                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
//                + "SELECT DISTINCT ?uri "
//                + "WHERE { ?cave rdf:type dbo:Cave . "
//                + "?cave dbo:location ?uri . "
//                + "?uri rdf:type dbo:Country . "
//                + "?uri dbo:writer ?y . FILTER(!BOUND(?cave))"
//                + "?cave dbo:location ?x } "; 
        
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1/> "
                + "PREFIX mo: <http://purl.org/ontology/mo/> "
                + "SELECT DISTINCT ?artisttype "
                + "WHERE {"
                + "?artist foaf:name 'Liz Story'."
                + "?artist rdf:type ?artisttype ."
                + "}";        
        
       Query query = new Query(queryString);
       SimpleGraphAccessor sga = query.getGraphRepresentation();
       
       // create JGraphT representation
       DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
       
       for (IGraphNode node : sga.getNodeSet()) {
           g.addVertex(node.getLabel());
       }       
       
       for (IGraphNode node : sga.getNodeSet()) {
           for (IGraphNode node2 : node.getSuccessorSet()) {
               g.addEdge(node.getLabel(), node2.getLabel());
           }
       }
       
       System.out.println(g);
       
       FloydWarshallShortestPaths<String,DefaultEdge> f = new FloydWarshallShortestPaths<String,DefaultEdge>(g);
       System.out.println("graph diameter: " + f.getDiameter());
       
       System.out.println("number of vertices: " + g.edgeSet().size());
     
       System.out.println("in/out-degree: " + g.edgeSet().size() / (double) g.vertexSet().size());
       
	}

}
