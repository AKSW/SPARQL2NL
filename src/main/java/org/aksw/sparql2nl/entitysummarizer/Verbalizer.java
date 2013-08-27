/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.entitysummarizer.clustering.BorderFlowX;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory.HardeningType;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector;
import org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector.Gender;
import org.aksw.sparql2nl.entitysummarizer.gender.LexiconBasedGenderDetector;
import org.aksw.sparql2nl.entitysummarizer.rules.NumericLiteralFilter;
import org.aksw.sparql2nl.entitysummarizer.rules.ObjectMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.PredicateMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.SubjectMergeRule;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.openrdf.model.vocabulary.RDF;

import simplenlg.features.Feature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import org.aksw.sparql2nl.naturallanguagegeneration.PropertyProcessor;

/**
 * A verbalizer for triples without variables.
 *
 * @author ngonga
 */
public class Verbalizer {

    SimpleNLGwithPostprocessing nlg;
    SparqlEndpoint endpoint;
    String language = "en";
    Realiser realiser;
    Map<Resource, String> labels;
    NumericLiteralFilter litFilter;
    GenderDetector gender;
    Map<Resource, Collection<Triple>> resource2Triples;
    
    private QueryExecutionFactory qef;
	private String cacheDirectory = "cache/sparql";
	
	PredicateMergeRule pr;
    ObjectMergeRule or;
    SubjectMergeRule sr;
    
    DatasetBasedGraphGenerator graphGenerator;

    public Verbalizer(SparqlEndpoint endpoint, CacheCoreEx cache, String cacheDirectory, String wordnetDirectory) {
        nlg = new SimpleNLGwithPostprocessing(endpoint, cache, cacheDirectory, wordnetDirectory);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint, cache, cacheDirectory);
        realiser = nlg.realiser;
        gender = new LexiconBasedGenderDetector();
        
        pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        
        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		if(cache != null){
			CacheEx cacheFrontend = new CacheExImpl(cache);
			qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
		}
		
		graphGenerator = new DatasetBasedGraphGenerator(endpoint, cache);
    }
    
    public Verbalizer(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory) {
        nlg = new SimpleNLGwithPostprocessing(endpoint, cacheDirectory, wordnetDirectory);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint, cacheDirectory);
        realiser = nlg.realiser;
        gender = new LexiconBasedGenderDetector();
        
        pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        
        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		if(cacheDirectory != null){
			try {
				long timeToLive = TimeUnit.DAYS.toMillis(30);
				CacheCoreEx cacheBackend = CacheCoreH2.create(cacheDirectory, timeToLive, true);
				CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
				qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		graphGenerator = new DatasetBasedGraphGenerator(endpoint, cacheDirectory);
    }

    public Verbalizer(SparqlEndpoint endpoint) {
        this(endpoint, (String)null, null);
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
            QueryExecution qe = qef.createQueryExecution(q);
            ResultSet results = qe.execSelect();
            if (results.hasNext()) {
                while (results.hasNext()) {
                    RDFNode n = results.next().get("o");
                    result.add(Triple.create(r.asNode(), p.asNode(), n.asNode()));
                }
            }
            qe.close();
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

    public List<NLGElement> generateSentencesFromClusters(List<Set<Node>> clusters,
            Resource resource, NamedClass namedClass) {
        return generateSentencesFromClusters(clusters, resource, namedClass, false);
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
            Resource resource, NamedClass namedClass, boolean replaceSubjects) {
        List<SPhraseSpec> buffer;
        

        String label = realiser.realiseSentence(nlg.getNPPhrase(resource.getURI(), false, false));
        String firstToken = label.split(" ")[0];
        Gender g = gender.getGender(firstToken);

        List<NPPhraseSpec> subjects = generateSubjects(resource, namedClass, g);
        List<NLGElement> result = new ArrayList<NLGElement>();
        Collection<Triple> allTriples = new ArrayList<Triple>();
//      
        for (Set<Node> propertySet : clusters) {
            //add up all triples for the given set of properties
            Set<Triple> triples = new HashSet<Triple>();
            buffer = new ArrayList<SPhraseSpec>();
            for (Node property : propertySet) {
                triples = getTriples(resource, ResourceFactory.createProperty(property.label));
//                litFilter.filter(triples);
                //all share the same property, thus they can be merged
                buffer.addAll(or.apply(getPhraseSpecsFromTriples(triples)));
                allTriples.addAll(triples);
            }
            result.addAll(sr.apply(or.apply(buffer), g));
        }
        
        resource2Triples.put(resource, allTriples);

        List<NLGElement> phrases = new ArrayList<NLGElement>();
        if (replaceSubjects) {

            for (int i = 0; i < result.size(); i++) {
                phrases.add(replaceSubject(result.get(i), subjects, g));
            }
            return phrases;
        } else {
            return result;
        }

    }
    
    public Collection<Triple> getSummaryTriples(Resource resource){
    	return resource2Triples.get(resource);
    }
    
    public Collection<Triple> getSummaryTriples(Individual individual){
    	return resource2Triples.get(ResourceFactory.createResource(individual.getName()));
    }

    /**
     * Generates sentence for a given set of triples
     *
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<NLGElement> generateSentencesFromTriples(Set<Triple> triples, Gender g) {
        return applyMergeRules(getPhraseSpecsFromTriples(triples), g);
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
    public List<NLGElement> applyMergeRules(List<SPhraseSpec> triples, Gender g) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.addAll(triples);

        int newSize = phrases.size(), oldSize = phrases.size() + 1;

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
        return sr.apply(phrases, g);
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
    	return verbalize(Sets.newHashSet(ind), nc, threshold, cooccurrence, hType).get(ind);
    }

    public Map<Individual, List<NLGElement>> verbalize(Set<Individual> individuals, NamedClass nc, double threshold, Cooccurrence cooccurrence, HardeningType hType) {
        resource2Triples = new HashMap<Resource, Collection<Triple>>();
    	//first get graph for nc
        WeightedGraph wg = graphGenerator.generateGraph(nc, threshold, "http://dbpedia.org/ontology/", cooccurrence);

        //then cluster the graph
        BorderFlowX bf = new BorderFlowX(wg);
        Set<Set<Node>> clusters = bf.cluster();
        //then harden the results
        List<Set<Node>> sortedPropertyClusters = HardeningFactory.getHardening(hType).harden(clusters, wg);
        System.out.println("Cluster = " + sortedPropertyClusters);

        Map<Individual, List<NLGElement>> verbalizations = new HashMap<Individual, List<NLGElement>>();

        for (Individual ind : individuals) {
            //finally generateSentencesFromClusters
            List<NLGElement> result = generateSentencesFromClusters(sortedPropertyClusters, ResourceFactory.createResource(ind.getName()), nc, true);

            Triple t = Triple.create(ResourceFactory.createResource(ind.getName()).asNode(), ResourceFactory.createProperty(RDF.TYPE.toString()).asNode(),
                    ResourceFactory.createResource(nc.getName()).asNode());
            result = Lists.reverse(result);
            result.add(generateSimplePhraseFromTriple(t));
            result = Lists.reverse(result);

            verbalizations.put(ind, result);
            
            resource2Triples.get(ResourceFactory.createResource(ind.getName())).add(t);
        }

        return verbalizations;
    }

    public List<NPPhraseSpec> generateSubjects(Resource resource, NamedClass nc, Gender g) {
        List<NPPhraseSpec> result = new ArrayList<NPPhraseSpec>();
        result.add(nlg.getNPPhrase(resource.getURI(), false, false));
        NPPhraseSpec np = nlg.getNPPhrase(nc.getName(), false);
        np.addPreModifier("This");
        result.add(np);
        if (g.equals(Gender.MALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("he"));
        } else if (g.equals(Gender.FEMALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("she"));
        } else {
            result.add(nlg.nlgFactory.createNounPhrase("it"));
        }
        return result;
    }

    public static void main(String args[]) {
    	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointLOD2Cloud();
    	endpoint.getDefaultGraphURIs().add("http://dbpedia.org");
    	//endpoint = SparqlEndpoint.getEndpointDBpedia();
    	
        Verbalizer v;
        if (SimpleNLGwithPostprocessing.isWindows()) {
            v = new Verbalizer(endpoint, "cache/sparql", "E:/Work/Java/SPARQL2NL/resources/wordnetWindows/");
        } else {
            v = new Verbalizer(endpoint, "cache/sparql", "resources/wordnet/dict");
        }
        
//        Individual ind = new Individual("http://dbpedia.org/resource/Barbara_Aland");
//        Individual ind = new Individual("http://dbpedia.org/resource/John_Passmore");
//        Individual ind = new Individual("http://dbpedia.org/resource/Ford_Zetec_engine");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/AutomobileEngine");
        Individual ind = new Individual("http://dbpedia.org/resource/69_Love_Songs");
        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/Album");
//        Individual ind = new Individual("http://dbpedia.org/resource/David_Foster");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/MusicalArtist");
        List<NLGElement> text = v.verbalize(ind, nc, 0.5, Cooccurrence.PROPERTIES, HardeningType.SMALLEST);
        System.out.println(v.realize(text));

    }

    /**
     * Replaces the subject of a coordinated phrase or simple phrase with a
     * subject from a list of precomputed subjects
     *
     * @param phrase
     * @param subjects
     * @return Phrase with replaced subject
     */
    private NLGElement replaceSubject(NLGElement phrase, List<NPPhraseSpec> subjects, Gender g) {
        SPhraseSpec sphrase;
        if (phrase instanceof SPhraseSpec) {
            sphrase = (SPhraseSpec) phrase;
        } else if (phrase instanceof CoordinatedPhraseElement) {
            sphrase = (SPhraseSpec) ((CoordinatedPhraseElement) phrase).getChildren().get(0);
        } else {
            return phrase;
        }
        int index = (int) Math.floor(Math.random() * subjects.size());
//        index = 2;
        if (((NPPhraseSpec) sphrase.getSubject()).getPreModifiers().size() > 0) //possessive subject
        {

            NPPhraseSpec subject = nlg.nlgFactory.createNounPhrase(((NPPhraseSpec) sphrase.getSubject()).getHead());
            NPPhraseSpec modifier;
            if (index < subjects.size() - 1) {
                modifier = nlg.nlgFactory.createNounPhrase(subjects.get(index));
                modifier.setFeature(Feature.POSSESSIVE, true);
                subject.setPreModifier(modifier);
                modifier.setFeature(Feature.POSSESSIVE, true);
            } else {
                if (g.equals(Gender.MALE)) {
                    subject.setPreModifier("his");
                } else if (g.equals(Gender.FEMALE)) {
                    subject.setPreModifier("her");
                } else {
                    subject.setPreModifier("its");
                }
            }
            if (sphrase.getSubject().isPlural()) {

//                subject.getSpecifier().setPlural(false);
                subject.setPlural(true);
            }
            sphrase.setSubject(subject);

        } else {
// does not fully work due to bug in SimpleNLG code      
            if (g.equals(Gender.MALE)) {
                sphrase.setSubject("He");
            } else if (g.equals(Gender.FEMALE)) {
                sphrase.setSubject("She");
            } else {
                sphrase.setSubject("It");
            }
        }
        return phrase;
    }
}
