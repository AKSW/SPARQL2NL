/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import java.util.*;
import org.aksw.sparql2nl.entitysummarizer.clustering.BorderFlowX;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.rules.ObjectMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.PredicateMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.Rule;
import org.aksw.sparql2nl.entitysummarizer.rules.SubjectMergeRule;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import simplenlg.framework.NLGElement;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 * A verbalizer for triples without variables.
 *
 * @author ngonga
 */
public class Verbalizer {

    SimpleNLGwithPostprocessing nlg;
    SparqlEndpoint endpoint;
    String language = "en";
    Realiser realiser = new Realiser(Lexicon.getDefaultLexicon());
    Map<Resource, String> labels;
    
    public Verbalizer(SparqlEndpoint endpoint) {
        nlg = new SimpleNLGwithPostprocessing(endpoint);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
    }

    /**
     * Gets all triples for resource r and property p
     *
     * @param r
     * @param p
     * @return A set of triples
     */
    public Set<Triple> getTriples(Resource r, Property p) {
        Set<Triple> result = new HashSet<Triple>();
        try {
            String q = "SELECT ?o where { <" + r.getURI() + "> <" + p.getURI() + "> ?o.}";// FILTER langMatches( lang(?p), " + language + " )}";
            QueryEngineHTTP qexec = new QueryEngineHTTP(endpoint.getURL().toString(), q);
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                while (results.hasNext()) {
                    RDFNode n = results.next().get("o");
                    result.add(Triple.create(r.asNode(), p.asNode(), n.asNode()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(result);
        return result;
    }

    /**
     * Generates the string representation of a verbalization
     *
     * @param properties List of property clusters to be used for varbalization
     * @param resource Resource to summarize
     * @return Textual representation
     */
    public String realize(List<Set<Node>> properties, Resource resource) {
        List<NLGElement> elts = generateSentencesFromClusters(properties, resource);
        return realize(elts);
    }

    public String realize(List<NLGElement> elts) {
        String realization = "";
        for (NLGElement elt : elts) {
            realization = realization + realiser.realiseSentence(elt) + " ";
        }
        return realization.substring(0, realization.length() - 1);
    }

    /**
     * Takes the output of the clustering for a given class and a resource.
     * Returns the verbalization for the resource
     *
     * @param clusters Output of the clustering
     * @param resource Resource to summarize
     * @return List of NLGElement
     */
    public List<NLGElement> generateSentencesFromClusters(List<Set<Node>> clusters, Resource resource) {
        List<NLGElement> result = new ArrayList<NLGElement>();
        Set<Triple> triples;
        for (Set<Node> propertySet : clusters) {
            triples = new HashSet<Triple>();
            for (Node property : propertySet) {
                triples.addAll(getTriples(resource, ResourceFactory.createProperty(property.label)));
                System.out.println(property + " => " + triples);
                if (triples.size() > 0) {
                    result.addAll(generateSentencesFromTriples(triples));
                }
            }
        }
        return result;
    }

    /**
     * Generates sentence for a given set of triples
     *
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<NLGElement> generateSentencesFromTriples(Set<Triple> triples) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        for (Triple t : triples) {
            phrases.add(generateSimplePhraseFromTriple(t));
        }
        return applyMergeRules(phrases);
    }

    /** Generates a set of sentences by merging the sentences in the list as well as possible
     * 
     * @param triples List of triles
     * @return List of sentences
     */
     
    public List<NLGElement> applyMergeRules(List<SPhraseSpec> triples) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.addAll(triples);

        int newSize = phrases.size(), oldSize = phrases.size() + 1;
        Rule mr = new ObjectMergeRule();
        Rule pr = new PredicateMergeRule();
        
        //apply merging rules if more than one sentence to merge
        if (newSize > 1) {
            //fix point iteration for object and predicate merging
            while (newSize < oldSize) {
                oldSize = newSize;
                int mrCount = mr.isApplicable(phrases);
                int prCount = pr.isApplicable(phrases);
                if (prCount > mrCount) {
                    phrases = pr.apply(phrases);
                } else {
                    phrases = mr.apply(phrases);
                }
                newSize = phrases.size();
            }
        }
        return (new SubjectMergeRule()).apply(phrases);
    }

    /**
     * Generates a simple phrase for a triple
     *
     * @param triple A triple
     * @return A simple phrase
     */
    public SPhraseSpec generateSimplePhraseFromTriple(Triple triple) {
        return nlg.getNLForTriple(triple);
    }

    public List<NLGElement> verbalize(Resource r, NamedClass nc, double threshold) {
        //first get graph for nc
        WeightedGraph wg = new DatasetBasedGraphGenerator(endpoint, "cache").generateGraph(nc, threshold, "http://dbpedia.org/ontology/");
        //then cluster the graph
        BorderFlowX bf = new BorderFlowX(wg);
        Set<Set<Node>> clusters = bf.cluster();
        //then harden the results
        List<Set<Node>> sortedPropertyClusters = HardeningFactory.getHardening(HardeningFactory.HardeningType.SMALLEST).harden(clusters, wg);
        System.out.println(sortedPropertyClusters);

        //finally generateSentencesFromClusters
        return generateSentencesFromClusters(sortedPropertyClusters, r);
    }

    public static void main(String args[]) {
        Verbalizer v = new Verbalizer(SparqlEndpoint.getEndpointDBpedia());
//        List<NLGElement> text = v.generateSentencesFromClusters(ResourceFactory.createResource("http://dbpedia.org/resource/Minority_Report_(film)"), new NamedClass("http://dbpedia.org/ontology/Film"), 0.5);
//        System.out.println(v.realize(text));
        List<NLGElement> text = v.verbalize(ResourceFactory.createResource("http://dbpedia.org/resource/A._J._Edds"), new NamedClass("http://dbpedia.org/ontology/AmericanFootballPlayer"), 0.5);
        System.out.println(v.realize(text));
    }
}
