/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.google.common.collect.Lists;
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
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory.HardeningType;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.sparql2nl.entitysummarizer.rules.NumericLiteralFilter;
import org.aksw.sparql2nl.entitysummarizer.rules.ObjectMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.PredicateMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.Rule;
import org.aksw.sparql2nl.entitysummarizer.rules.SubjectMergeRule;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.openrdf.model.vocabulary.RDF;
import simplenlg.features.Feature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
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
    NumericLiteralFilter litFilter;

    public Verbalizer(SparqlEndpoint endpoint, String wordnetDirectory) {
        nlg = new SimpleNLGwithPostprocessing(endpoint, wordnetDirectory);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint);
    }
    
    public Verbalizer(SparqlEndpoint endpoint) {
        nlg = new SimpleNLGwithPostprocessing(endpoint);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint);
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
        return result;
    }

    /**
     * Generates the string representation of a verbalization
     *
     * @param properties List of property clusters to be used for varbalization
     * @param resource Resource to summarize
     * @return Textual representation
     */
    public String realize(List<Set<Node>> properties, Resource resource, NamedClass nc) {
        List<NLGElement> elts = generateSentencesFromClusters(properties, resource, nc);
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
    public List<NLGElement> generateSentencesFromClusters(List<Set<Node>> clusters,
            Resource resource, NamedClass namedClass) {
        List<SPhraseSpec> buffer;
        List<NPPhraseSpec> subjects = generateSubjects(resource, namedClass);
        List<NLGElement> result = new ArrayList<NLGElement>();
        Set<Triple> triples = new HashSet<Triple>();
//        PredicateMergeRule pr = new PredicateMergeRule();
        ObjectMergeRule or = new ObjectMergeRule();
        SubjectMergeRule sr = new SubjectMergeRule();

        for (Set<Node> propertySet : clusters) {
            //add up all triples for the given set of properties
            triples = new HashSet<Triple>();
            buffer = new ArrayList<SPhraseSpec>();
            for (Node property : propertySet) {
                triples = getTriples(resource, ResourceFactory.createProperty(property.label));
//                litFilter.filter(triples);
                //all share the same property, thus they can be merged
                buffer.addAll(or.apply(getPhraseSpecsFromTriples(triples)));
            }
            result.addAll(sr.apply(or.apply(buffer)));
        }

        for (int i = 0; i < result.size(); i++) {
            result.set(i, replaceSubject(result.get(i), subjects));
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
        return applyMergeRules(getPhraseSpecsFromTriples(triples));
    }

    /**
     * Generates sentence for a given set of triples
     *
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<SPhraseSpec> getPhraseSpecsFromTriples(Set<Triple> triples) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        for (Triple t : triples) {
            phrases.add(generateSimplePhraseFromTriple(t));
        }
        return phrases;
    }

    /**
     * Generates a set of sentences by merging the sentences in the list as well
     * as possible
     *
     * @param triples List of triles
     * @return List of sentences
     */
    public List<NLGElement> applyMergeRules(List<SPhraseSpec> triples) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.addAll(triples);

        int newSize = phrases.size(), oldSize = phrases.size() + 1;
        Rule or = new ObjectMergeRule();
        Rule pr = new PredicateMergeRule();

        //apply merging rules if more than one sentence to merge
        if (newSize > 1) {
            //fix point iteration for object and predicate merging
            while (newSize < oldSize) {
                oldSize = newSize;
                int orCount = or.isApplicable(phrases);
                int prCount = pr.isApplicable(phrases);
                if (prCount > 0 || orCount > 0) {
                    if (prCount > orCount) {
                        phrases = pr.apply(phrases);
                    } else {
                        phrases = or.apply(phrases);
                    }
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

    public List<NLGElement> verbalize(Individual ind, NamedClass nc, double threshold, Cooccurrence cooccurrence, HardeningType hType) {
        //first get graph for nc
        WeightedGraph wg = new DatasetBasedGraphGenerator(endpoint, "cache").generateGraph(nc, threshold, "http://dbpedia.org/ontology/", cooccurrence);

        //then cluster the graph
        BorderFlowX bf = new BorderFlowX(wg);
        Set<Set<Node>> clusters = bf.cluster();
        //then harden the results
        List<Set<Node>> sortedPropertyClusters = HardeningFactory.getHardening(hType).harden(clusters, wg);
        System.out.println("Cluster = " + sortedPropertyClusters);

        //finally generateSentencesFromClusters
        List<NLGElement> result = generateSentencesFromClusters(sortedPropertyClusters, ResourceFactory.createResource(ind.getName()), nc);

        //Add type information at the beginning of the sentence
        Triple t = Triple.create(ResourceFactory.createResource(ind.getName()).asNode(), ResourceFactory.createProperty(RDF.TYPE.toString()).asNode(),
                ResourceFactory.createResource(nc.getName()).asNode());
        result = Lists.reverse(result);
        result.add(generateSimplePhraseFromTriple(t));
        result = Lists.reverse(result);

        return result;
    }

    public Map<Individual, List<NLGElement>> verbalize(Set<Individual> individuals, NamedClass nc, double threshold, Cooccurrence cooccurrence, HardeningType hType) {
        //first get graph for nc
        WeightedGraph wg = new DatasetBasedGraphGenerator(endpoint, "cache").generateGraph(nc, threshold, "http://dbpedia.org/ontology/", cooccurrence);

        //then cluster the graph
        BorderFlowX bf = new BorderFlowX(wg);
        Set<Set<Node>> clusters = bf.cluster();
        //then harden the results
        List<Set<Node>> sortedPropertyClusters = HardeningFactory.getHardening(hType).harden(clusters, wg);
        System.out.println("Cluster = " + sortedPropertyClusters);

        Map<Individual, List<NLGElement>> verbalizations = new HashMap<Individual, List<NLGElement>>();

        for (Individual ind : individuals) {
            //finally generateSentencesFromClusters
            List<NLGElement> result = generateSentencesFromClusters(sortedPropertyClusters, ResourceFactory.createResource(ind.getName()), nc);

            Triple t = Triple.create(ResourceFactory.createResource(ind.getName()).asNode(), ResourceFactory.createProperty(RDF.TYPE.toString()).asNode(),
                    ResourceFactory.createResource(nc.getName()).asNode());
            result = Lists.reverse(result);
            result.add(generateSimplePhraseFromTriple(t));
            result = Lists.reverse(result);

            verbalizations.put(ind, result);
        }

        return verbalizations;
    }

    public List<NPPhraseSpec> generateSubjects(Resource resource, NamedClass nc) {
        List<NPPhraseSpec> result = new ArrayList<NPPhraseSpec>();
        result.add(nlg.getNPPhrase(resource.getURI(), false, false));
        NPPhraseSpec np = nlg.getNPPhrase(nc.getName(), false);
        np.addPreModifier("This");
        result.add(np);
        return result;
    }

    public static void main(String args[]) {
        Verbalizer v = new Verbalizer(SparqlEndpoint.getEndpointDBpediaLiveAKSW());

//        Individual ind = new Individual("http://dbpedia.org/resource/Chad_Ochocinco");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/AmericanFootballPlayer");
        Individual ind = new Individual("http://dbpedia.org/resource/Minority_Report_(film)");
        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/Film");
        List<NLGElement> text = v.verbalize(ind, nc, 0.5, Cooccurrence.PROPERTIES, HardeningType.SMALLEST);
        System.out.println(v.realize(text));
        for (NLGElement p : text) {
            if (p instanceof CoordinatedPhraseElement) {
                for(NLGElement p2: p.getChildren())
                System.out.println("==="+v.realiser.realiseSentence(p2));
            }
        }

    }

    /** Replaces the subject of a coordinated phrase or simple phrase with a 
     * subject from a list of precomputed subjects
     * 
     * @param phrase
     * @param subjects
     * @return Phrase with replaced subject
     */
    private NLGElement replaceSubject(NLGElement phrase, List<NPPhraseSpec> subjects) {
        SPhraseSpec sphrase;
        if (phrase instanceof SPhraseSpec) {
            sphrase = (SPhraseSpec) phrase;
        } else if (phrase instanceof CoordinatedPhraseElement) {
            sphrase = (SPhraseSpec) ((CoordinatedPhraseElement) phrase).getChildren().get(0);
        }
        else return phrase;
        int index = (int)Math.floor(Math.random()*subjects.size());
        if(sphrase.getChildren().size() > 1) //possesive subject
        {
            NPPhraseSpec subject = nlg.nlgFactory.createNounPhrase(((NPPhraseSpec)sphrase.getSubject()).getHead());
            NPPhraseSpec modifier = nlg.nlgFactory.createNounPhrase(subjects.get(index));
            modifier.setFeature(Feature.POSSESSIVE, true);
                subject.setPreModifier(modifier);
                if(sphrase.getSubject().isPlural())
                    subject.setPlural(true);
                sphrase.setSubject(subject);
//            ((NPPhraseSpec)sphrase.getSubject()).setPreModifier(subjects.get(index));
//            ((NPPhraseSpec)sphrase.getSubject()).setFeature(Feature.POSSESSIVE, true);
//            ((NPPhraseSpec)sphrase.getSubject()).getHead().setFeature(Feature.POSSESSIVE, true);
        }
        
        else sphrase.setSubject(subjects.get(index));
        return phrase;
    }
}
