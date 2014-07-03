/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedSubgraph;

import simplenlg.features.Feature;
import simplenlg.features.InternalFeature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * @author Lorenz Buehmann
 *
 */
public class DocumentGenerator {

	private TripleConverter tripleConverter;
	private NLGFactory nlgFactory;
	private Realiser realiser;

	private boolean useAsWellAsCoordination = true;

	public DocumentGenerator(SparqlEndpoint endpoint, String cacheDirectory) {
		this(endpoint, cacheDirectory, Lexicon.getDefaultLexicon());
	}

	public DocumentGenerator(SparqlEndpoint endpoint, String cacheDirectory, Lexicon lexicon) {
		tripleConverter = new TripleConverter(endpoint, cacheDirectory, lexicon);

		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);
	}

	public String generateDocument(Model model) {
		Set<Triple> triples = asTriples(model);
		return generateDocument(triples);
	}

	private Set<Triple> asTriples(Model model) {
		Set<Triple> triples = new HashSet<Triple>((int) model.size());
		StmtIterator iterator = model.listStatements();
		while (iterator.hasNext()) {
			Statement statement = (Statement) iterator.next();
			triples.add(statement.asTriple());
		}
		return triples;
	}

	public String generateDocument(Set<Triple> documentTriples) {
		DefaultDirectedGraph<Node, DefaultEdge> graph = asGraph(documentTriples);
		
		//divide the document into paragraphs for each connected component in the graph
		ConnectivityInspector<Node, DefaultEdge> connectivityInspector = new ConnectivityInspector<Node, DefaultEdge>(graph);
		List<Set<Node>> connectedNodes = connectivityInspector.connectedSets();
		
		for (Set<Node> nodes : connectedNodes) {
			System.out.println(nodes);
		}
		
		//group triples by subject
		Map<Node, Collection<Triple>> subject2Triples = groupBySubject(documentTriples);
		
		//do some sorting
		subject2Triples = sort(documentTriples, subject2Triples);
		
		List<DocumentElement> sentences = new ArrayList<DocumentElement>();
		for (Entry<Node, Collection<Triple>> entry : subject2Triples.entrySet()) {
			Node subject = entry.getKey();
			Collection<Triple> triples = entry.getValue();
			
			// combine with conjunction
			CoordinatedPhraseElement conjunction = nlgFactory.createCoordinatedPhrase();

			// get the type triples first
			Set<Triple> typeTriples = new HashSet<Triple>();
			Set<Triple> otherTriples = new HashSet<Triple>();

			for (Triple triple : triples) {
				if (triple.predicateMatches(RDF.type.asNode())) {
					typeTriples.add(triple);
				} else {
					otherTriples.add(triple);
				}
			}

			// convert the type triples
			List<SPhraseSpec> typePhrases = tripleConverter.convertTriples(typeTriples);

			// if there are more than one types, we combine them in a single clause
			if (typePhrases.size() > 1) {
				// combine all objects in a coordinated phrase
				CoordinatedPhraseElement combinedObject = nlgFactory.createCoordinatedPhrase();

				// the last 2 phrases are combined via 'as well as'
				if (useAsWellAsCoordination) {
					SPhraseSpec phrase1 = typePhrases.remove(typePhrases.size() - 1);
					SPhraseSpec phrase2 = typePhrases.get(typePhrases.size() - 1);
					// combine all objects in a coordinated phrase
					CoordinatedPhraseElement combinedLastTwoObjects = nlgFactory.createCoordinatedPhrase(
							phrase1.getObject(), phrase2.getObject());
					combinedLastTwoObjects.setConjunction("as well as");
					combinedLastTwoObjects.setFeature(Feature.RAISE_SPECIFIER, false);
					combinedLastTwoObjects.setFeature(InternalFeature.SPECIFIER, "a");
					phrase2.setObject(combinedLastTwoObjects);
				}

				Iterator<SPhraseSpec> iterator = typePhrases.iterator();
				// pick first phrase as representative
				SPhraseSpec representative = iterator.next();
				combinedObject.addCoordinate(representative.getObject());

				while (iterator.hasNext()) {
					SPhraseSpec phrase = iterator.next();
					NLGElement object = phrase.getObject();
					combinedObject.addCoordinate(object);
				}
				
				combinedObject.setFeature(Feature.RAISE_SPECIFIER, true);
				// set the coordinated phrase as the object
				representative.setObject(combinedObject);
				// return a single phrase
				typePhrases = Lists.newArrayList(representative);
			}
			for (SPhraseSpec phrase : typePhrases) {
				conjunction.addCoordinate(phrase);
			}

			// convert the other triples, but use place holders for the subject
			String placeHolderToken = (typeTriples.isEmpty() || otherTriples.size() == 1) ? "it" : "whose";
			Node placeHolder = NodeFactory.createURI("http://sparql2nl.aksw.org/placeHolder/" + placeHolderToken);
			Collection<Triple> placeHolderTriples = new ArrayList<Triple>(otherTriples.size());
			Iterator<Triple> iterator = otherTriples.iterator();
			// we have to keep one triple with subject if we have no type triples
			if (typeTriples.isEmpty() && iterator.hasNext()) {
				placeHolderTriples.add(iterator.next());
			}
			while (iterator.hasNext()) {
				Triple triple = iterator.next();
				Triple newTriple = Triple.create(placeHolder, triple.getPredicate(), triple.getObject());
				placeHolderTriples.add(newTriple);
			}

			Collection<SPhraseSpec> otherPhrases = tripleConverter.convertTriples(placeHolderTriples);

			for (SPhraseSpec phrase : otherPhrases) {
				conjunction.addCoordinate(phrase);
			}

			DocumentElement sentence = nlgFactory.createSentence(conjunction);
			sentences.add(sentence);
		}
		
		DocumentElement paragraph = nlgFactory.createParagraph(sentences);
		
		String paragraphText = realiser.realise(paragraph).getRealisation();
		return paragraphText;
	}
	
	/**
	 * @param documentTriples 
	 * @param subject2Triples
	 */
	private Map<Node, Collection<Triple>> sort(Set<Triple> documentTriples, Map<Node, Collection<Triple>> subject2Triples) {
		Map<Node, Collection<Triple>> sortedTriples = new LinkedHashMap<Node, Collection<Triple>>();
		//we can order by occurrence, i.e. which subjects do not occur in object position
		//for each subject we check how often they occur in subject/object position
		Multimap<Node, Node> outgoing = HashMultimap.create();
		Multimap<Node, Node> incoming = HashMultimap.create();
		for (Node subject : subject2Triples.keySet()) {
			for (Triple triple : documentTriples) {
				if(triple.subjectMatches(subject)){
					outgoing.put(subject, triple.getObject());
				} else if(triple.objectMatches(subject)){
					incoming.put(subject, triple.getSubject());
				}
			}
		}
		//prefer subjects that do not occur in object position first
		for (Iterator<Entry<Node, Collection<Triple>>> iterator = subject2Triples.entrySet().iterator(); iterator.hasNext();) {
			Entry<Node, Collection<Triple>> entry = iterator.next();
			Node subject = entry.getKey();
			if(!incoming.containsKey(subject)){
				sortedTriples.put(subject, new HashSet<Triple>(entry.getValue()));
				iterator.remove();
			}
		}
		//add the rest
		sortedTriples.putAll(subject2Triples);
		
		//TODO order by triple count
		
		//TODO order by prominence
		
		return sortedTriples;
	}

	private Map<Node, Collection<Triple>> groupBySubject(Set<Triple> triples){
		Multimap<Node, Triple> subject2Triples = HashMultimap.create();
		for (Triple triple : triples) {
			subject2Triples.put(triple.getSubject(), triple);
		}
		return subject2Triples.asMap();
	}
	
	private DefaultDirectedGraph<Node, DefaultEdge> asGraph(Set<Triple> triples){
		DefaultDirectedGraph<Node, DefaultEdge> graph = new DefaultDirectedGraph<Node, DefaultEdge>(DefaultEdge.class);
		for (Triple triple : triples) {
			//we have to omit type triples to get connected subgraphs later on
			if(!triple.predicateMatches(RDF.type.asNode())){
				graph.addVertex(triple.getSubject());
				graph.addVertex(triple.getObject());
				graph.addEdge(triple.getSubject(), triple.getObject());
			}
		}	
		return graph;
	}
	
	public static void main(String[] args) throws Exception {
		String triples = 
				"@prefix : <http://dbpedia.org/resource/> ."
				+ "@prefix dbo: <http://dbpedia.org/ontology/> ."
				+ "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> ."
				+ ":Albert_Einstein a dbo:Physican, dbo:Philosopher;"
				+ "dbo:birthPlace :Ulm;"
				+ "dbo:birthDate \"1879-03-14\"^^xsd:date ."
				+ ":Ulm a dbo:City;"
				+ "dbo:country :Germany;"
				+ "dbo:federalState :Baden_Württemberg ."
				+ ":Leipzig a dbo:City;"
				+ "dbo:country :Germany;"
				+ "dbo:federalState :Saxony .";
		
		Model model = ModelFactory.createDefaultModel();
		model.read(new ByteArrayInputStream(triples.getBytes()), null, "TURTLE");
		
		DocumentGenerator gen = new DocumentGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache");
		String document = gen.generateDocument(model);
		System.out.println(document);
	}

}
