package org.aksw.sparql2nl;

import java.io.OutputStreamWriter;
import java.util.Set;

import javax.xml.transform.TransformerConfigurationException;

import org.aksw.sparql2nl.queryprocessing.TriplePatternExtractor;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.ext.GraphMLExporter;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;

public class QueryStats {

	/**
	 * @param args
	 * @throws SAXException 
	 * @throws TransformerConfigurationException 
	 */
	public static void main(String[] args) throws TransformerConfigurationException, SAXException {

		String queryString = "PREFIX dbo: <http://dbpedia.org/ontology/> "
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "SELECT DISTINCT ?uri "
				+ "WHERE { ?cave rdf:type dbo:Cave . "
				+ "?cave dbo:location ?uri . " + "?uri rdf:type dbo:Country . "
				+ "?uri dbo:writer ?y . FILTER(!BOUND(?cave))"
				+ "?cave dbo:location ?x } ";

		String queryString2 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
				+ "PREFIX foaf: <http://xmlns.com/foaf/0.1/> "
				+ "PREFIX mo: <http://purl.org/ontology/mo/> "
				+ "SELECT DISTINCT ?artisttype "
				+ "WHERE {"
				+ "?artist foaf:name 'Liz Story'. ?artisttype rdf:subClassOf ?super . ?super rdf:type ?supsup ."
				+ "?artist rdf:type ?artisttype ." + "}";

		Query query = QueryFactory.create(queryString2);

		System.out.println(query);

		TriplePatternExtractor tpe = new TriplePatternExtractor();
		Set<Triple> triples = tpe.extractTriplePattern(query);

		System.out.println("number of triple patterns: " + triples.size());
		System.out.println("number of FILTER expressions: "
				+ tpe.getFilterCount());
		System.out.println("number of UNION expressions: "
				+ tpe.getUnionCount());
		System.out.println("number of OPTIONAL expressions: "
				+ tpe.getOptionalCount());
		System.out.println("sum of the number of the above expressions: "
				+ (tpe.getFilterCount() + tpe.getUnionCount() + tpe
						.getOptionalCount()));

		// create JGraphT representation (unlabeled because it is simpler - if
		// we need edge lables,
		// https://github.com/jgrapht/jgrapht/wiki/LabeledEdges shows how to do
		// it)
		DirectedGraph<Node, DefaultEdge> g = new DefaultDirectedGraph<Node, DefaultEdge>(
				DefaultEdge.class);
		for (Triple triple : triples) {
			g.addVertex(triple.getSubject());
			g.addVertex(triple.getObject());
			g.addEdge(triple.getSubject(), triple.getObject());
		}

		// System.out.println(g);

		FloydWarshallShortestPaths<Node, DefaultEdge> f = new FloydWarshallShortestPaths<Node, DefaultEdge>(
				g);
		// TODO: results look strange
		System.out.println("graph diameter: " + f.getDiameter());
		System.out.println("number of shortest paths: "
				+ f.getShortestPathsCount());

		System.out.println("number of vertices: " + g.edgeSet().size());

		// TODO: in-/out-degree does not say much - maybe it would be more
		// meaningful when not counting leafs
		System.out.println("in/out-degree: " + g.edgeSet().size()
				/ (double) g.vertexSet().size());

		GraphMLExporter<Node,DefaultEdge> ge = new GraphMLExporter();
		ge.export(new OutputStreamWriter(System.out), g);
		
		// bug report (diameter 0 instead of 1)
		DirectedGraph<String, DefaultEdge> graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
		String a = "a", b = "b", c = "c";
		graph.addVertex(a);
		graph.addVertex(b);
		graph.addEdge(a, b);
//		graph.addVertex(c);
//		graph.addEdge(b, c);
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<String, DefaultEdge>(graph);
//		System.out.println(fw.getDiameter());
		
	}

}
