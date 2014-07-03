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
import java.util.Map.Entry;
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
import org.aksw.sparql2nl.entitysummarizer.dataset.CachedDatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.sparql2nl.entitysummarizer.gender.Gender;
import org.aksw.sparql2nl.entitysummarizer.gender.LexiconBasedGenderDetector;
import org.aksw.sparql2nl.entitysummarizer.gender.TypeAwareGenderDetector;
import org.aksw.sparql2nl.entitysummarizer.rules.DateLiteralFilter;
import org.aksw.sparql2nl.entitysummarizer.rules.NumericLiteralFilter;
import org.aksw.sparql2nl.entitysummarizer.rules.ObjectMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.PredicateMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.SubjectMergeRule;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.utilities.MapUtils;

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
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * A verbalizer for triples without variables.
 *
 * @author ngonga
 */
public class Verbalizer {
	
	private static final Logger logger = Logger.getLogger(Verbalizer.class.getName());

    public SimpleNLGwithPostprocessing nlg;
    SparqlEndpoint endpoint;
    String language = "en";
    Realiser realiser;
    Map<Resource, String> labels;
    NumericLiteralFilter litFilter;
    TypeAwareGenderDetector gender;
    public Map<Resource, Collection<Triple>> resource2Triples;
    private QueryExecutionFactory qef;
    private String cacheDirectory = "cache/sparql";
    PredicateMergeRule pr;
    ObjectMergeRule or;
    SubjectMergeRule sr;
    public DatasetBasedGraphGenerator graphGenerator;
    int maxShownValuesPerProperty = 5;
    boolean omitContentInBrackets = true;

    public Verbalizer(SparqlEndpoint endpoint, CacheCoreEx cache, String cacheDirectory, String wordnetDirectory) {
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint, cache, cacheDirectory);
        realiser = nlg.realiser;
        gender = new TypeAwareGenderDetector(endpoint, new LexiconBasedGenderDetector());

        pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);

        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if (cache != null) {
            CacheEx cacheFrontend = new CacheExImpl(cache);
            qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
        }
        nlg = new SimpleNLGwithPostprocessing(qef, cacheDirectory, wordnetDirectory);

        graphGenerator = new CachedDatasetBasedGraphGenerator(endpoint, cache);
    }

    public Verbalizer(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory) {
        nlg = new SimpleNLGwithPostprocessing(endpoint, cacheDirectory, wordnetDirectory);
        this.endpoint = endpoint;
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(endpoint, cacheDirectory);
        realiser = nlg.realiser;

        pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);

        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        CacheCoreEx cacheBackend = null;
        if (cacheDirectory != null) {
            try {
                long timeToLive = TimeUnit.DAYS.toMillis(30);
                cacheBackend = CacheCoreH2.create(cacheDirectory, timeToLive, true);
                CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
                qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        gender = new TypeAwareGenderDetector(endpoint, cacheBackend, new LexiconBasedGenderDetector());

        graphGenerator = new CachedDatasetBasedGraphGenerator(endpoint, cacheDirectory);
    }
    
    public Verbalizer(QueryExecutionFactory qef, String cacheDirectory, String wordnetDirectory) {
    	this.qef = qef;
        nlg = new SimpleNLGwithPostprocessing(qef, cacheDirectory, wordnetDirectory);
        labels = new HashMap<Resource, String>();
        litFilter = new NumericLiteralFilter(qef, cacheDirectory);
        realiser = nlg.realiser;

        pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
        sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);

        gender = new TypeAwareGenderDetector(qef, new LexiconBasedGenderDetector());

        graphGenerator = new CachedDatasetBasedGraphGenerator(qef, cacheDirectory);
    }
    
    /**
     * @param blacklist a blacklist of properties that are omitted when building the summary
     */
    public void setPropertiesBlacklist(Set<String> blacklist) {
        graphGenerator.setPropertiesBlacklist(blacklist);
    }

    /**
     * @param personTypes the personTypes to set
     */
    public void setPersonTypes(Set<String> personTypes) {
        gender.setPersonTypes(personTypes);
    }

    /**
     * @param omitContentInBrackets the omitContentInBrackets to set
     */
    public void setOmitContentInBrackets(boolean omitContentInBrackets) {
        this.omitContentInBrackets = omitContentInBrackets;
    }

    public Verbalizer(SparqlEndpoint endpoint) {
        this(endpoint, (String) null, null);
    }

    /**
     * Gets all triples for resource r and property p.
     * If outgoing is true it returns all triples with <r,p,o>, else <s,p,r>
     *
     * @param r the resource 
     * @param p the property 
     * @param outgoing whether to get outgoing or ingoing triples
     * @return A set of triples
     */
    public Set<Triple> getTriples(Resource r, Property p, boolean outgoing) {
        Set<Triple> result = new HashSet<Triple>();
        try {
        	String q;
        	if(outgoing){
        		q = "SELECT ?o where { <" + r.getURI() + "> <" + p.getURI() + "> ?o.}";
        	} else {
        		q = "SELECT ?o where { ?o <" + p.getURI() + "> <" + r.getURI() + ">.}";
        	}
        	q += " LIMIT " + maxShownValuesPerProperty+1;
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
    
    public Set<Node> getSummaryProperties(NamedClass cls, double threshold, String namespace, DatasetBasedGraphGenerator.Cooccurrence cooccurrence){
   	 Set<Node> properties = new HashSet<Node>();
   	 WeightedGraph wg = graphGenerator.generateGraph(cls, threshold, "http://dbpedia.org/ontology/", cooccurrence);
   	 return wg.getNodes().keySet();
//   	 for (Node property : wg.getNodes().keySet()) {
//			properties.add(property.label);
//   	 }
//   	 return properties;
    }

    /**
     * Generates the string representation of a verbalization
     *
     * @param properties List of property clusters to be used for verbalization
     * @param resource Resource to summarize
     * @return Textual representation
     */
    public String realize(List<Set<Node>> properties, Resource resource, NamedClass nc) {
        List<NLGElement> elts = generateSentencesFromClusters(properties, resource, nc);
        return realize(elts);
    }

    public String realize(List<NLGElement> elts) {
        if(elts.isEmpty()) return null;
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

        //compute the gender of the resource
        Gender g = getGender(resource);

        //get a list of possible subject replacements
        List<NPPhraseSpec> subjects = generateSubjects(resource, namedClass, g);
        
        List<NLGElement> result = new ArrayList<NLGElement>();
        Collection<Triple> allTriples = new ArrayList<Triple>();
        DateLiteralFilter dateFilter = new DateLiteralFilter();
//      
        for (Set<Node> propertySet : clusters) {
            //add up all triples for the given set of properties
            Set<Triple> triples = new HashSet<Triple>();
            buffer = new ArrayList<SPhraseSpec>();
            for (Node property : propertySet) {
                triples = getTriples(resource, ResourceFactory.createProperty(property.label), property.outgoing);
                litFilter.filter(triples);
                dateFilter.filter(triples);
                //restrict the number of shown values for the same property
                boolean subsetShown = false;
                if (triples.size() > maxShownValuesPerProperty) {
                    triples = getSubsetToShow(triples);
                    subsetShown = true;
                }
                //all share the same property, thus they can be merged
                buffer.addAll(or.apply(getPhraseSpecsFromTriples(triples, property.outgoing), subsetShown));
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

    private Set<Triple> getSubsetToShow(Set<Triple> triples) {
        Set<Triple> triplesToShow = new HashSet<>(maxShownValuesPerProperty);
        for (Triple triple : sortByObjectPopularity(triples)) {
            if (triplesToShow.size() < maxShownValuesPerProperty) {
                triplesToShow.add(triple);
            }
        }

        return triplesToShow;
    }

    /**
     * Sorts the given triples by the popularity of the triple objects.
     * @param triples the triples
     * @return a list of sorted triples
     */
    private List<Triple> sortByObjectPopularity(Set<Triple> triples) {
        List<Triple> orderedTriples = new ArrayList<>();

        //if one of the objects is a literal we do not sort 
        if (triples.iterator().next().getObject().isLiteral()) {
            orderedTriples.addAll(triples);
        } else {
            //we get the popularity of the object
            Map<Triple, Integer> triple2ObjectPopularity = new HashMap<>();
            for (Triple triple : triples) {
                if (triple.getObject().isURI()) {
                    String query = "SELECT (COUNT(*) AS ?cnt) WHERE {<" + triple.getObject().getURI() + "> ?p ?o.}";
                    QueryExecution qe = qef.createQueryExecution(query);
                    ResultSet rs = qe.execSelect();
                    int popularity = rs.next().getLiteral("cnt").getInt();
                    triple2ObjectPopularity.put(triple, popularity);
                    qe.close();
                }
            }
            List<Entry<Triple, Integer>> sortedByValues = MapUtils.sortByValues(triple2ObjectPopularity);

            for (Entry<Triple, Integer> entry : sortedByValues) {
                Triple triple = entry.getKey();
                orderedTriples.add(triple);
            }
        }

        return orderedTriples;
    }

    /**
     * Returns the triples of the summary for the given resource.
     * @param resource the resource of the summary
     * @return a set of triples
     */
    public Collection<Triple> getSummaryTriples(Resource resource) {
        return resource2Triples.get(resource);
    }

    /**
     * Returns the triples of the summary for the given individual.
     * @param individual the individual of the summary
     * @return a set of triples
     */
    public Collection<Triple> getSummaryTriples(Individual individual) {
        return getSummaryTriples(ResourceFactory.createResource(individual.getName()));
    }

    /**
     * Generates sentence for a given set of triples
     *
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<NLGElement> generateSentencesFromTriples(Set<Triple> triples, boolean outgoing, Gender g) {
        return applyMergeRules(getPhraseSpecsFromTriples(triples, outgoing), g);
    }

    /**
     * Generates sentence for a given set of triples
     *
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<SPhraseSpec> getPhraseSpecsFromTriples(Set<Triple> triples, boolean outgoing) {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        SPhraseSpec phrase;
        for (Triple t : triples) {
            phrase = generateSimplePhraseFromTriple(t, outgoing);
			phrases.add(phrase);
        }
        return phrases;
    }

    /**
     * Generates a set of sentences by merging the sentences in the list as well
     * as possible
     *
     * @param triples List of triples
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
    
    /**
     * Generates a simple phrase for a triple
     *
     * @param triple A triple
     * @return A simple phrase
     */
    public SPhraseSpec generateSimplePhraseFromTriple(Triple triple, boolean outgoing) {
        return nlg.getNLForTriple(triple, outgoing);
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
        logger.debug("Cluster = " + sortedPropertyClusters);

        Map<Individual, List<NLGElement>> verbalizations = new HashMap<Individual, List<NLGElement>>();

        for (Individual ind : individuals) {
            //finally generateSentencesFromClusters
            List<NLGElement> result = generateSentencesFromClusters(sortedPropertyClusters, ResourceFactory.createResource(ind.getName()), nc, true);

            Triple t = Triple.create(ResourceFactory.createResource(ind.getName()).asNode(), ResourceFactory.createProperty(RDF.type.getURI()).asNode(),
                    ResourceFactory.createResource(nc.getName()).asNode());
            result = Lists.reverse(result);
            result.add(generateSimplePhraseFromTriple(t));
            result = Lists.reverse(result);

            verbalizations.put(ind, result);

            resource2Triples.get(ResourceFactory.createResource(ind.getName())).add(t);
        }

        return verbalizations;
    }

    /**
     * Returns a textual summary of the given entity.
     *
     * @return
     */
    public String getSummary(Individual individual, NamedClass nc, double threshold, Cooccurrence cooccurrence, HardeningType hType) {
        List<NLGElement> elements = verbalize(individual, nc, threshold, cooccurrence, hType);
        String summary = realize(elements);
        summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
        summary = summary.replace(" , among others,", ", among others,");
        return summary;
    }

    /**
     * Returns a textual summary of the given entity.
     *
     * @return
     */
    public Map<Individual, String> getSummaries(Set<Individual> individuals, NamedClass nc, double threshold, Cooccurrence cooccurrence, HardeningType hType) {
        Map<Individual, String> entity2Summaries = new HashMap<>();

        Map<Individual, List<NLGElement>> verbalize = verbalize(individuals, nc, threshold, cooccurrence, hType);
        for (Entry<Individual, List<NLGElement>> entry : verbalize.entrySet()) {
            Individual individual = entry.getKey();
            List<NLGElement> elements = entry.getValue();
            String summary = realize(elements);
            summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
            summary = summary.replace(" , among others,", ", among others,");
            entity2Summaries.put(individual, summary);
        }

        return entity2Summaries;
    }

    /**
     * Returns a list of synonymous expressions as subject for the given resource.
     * @param resource the resource
     * @param resourceType the type of the resource
     * @param resourceGender the gender of the resource
     * @return list of synonymous expressions
     */
    public List<NPPhraseSpec> generateSubjects(Resource resource, NamedClass resourceType, Gender resourceGender) {
        List<NPPhraseSpec> result = new ArrayList<NPPhraseSpec>();
        //the textual representation of the resource itself
        result.add(nlg.getNPPhrase(resource.getURI(), false, false));
        //the class, e.g. 'this book'
        NPPhraseSpec np = nlg.getNPPhrase(resourceType.getName(), false);
        np.addPreModifier("This");
        result.add(np);
        //the pronoun depending on the gender of the resource
        if (resourceGender.equals(Gender.MALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("he"));
        } else if (resourceGender.equals(Gender.FEMALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("she"));
        } else {
            result.add(nlg.nlgFactory.createNounPhrase("it"));
        }
        return result;
    }
    
    /**
     * Returns the gender of the given resource.
     * @param resource
     * @return the gender
     */
    public Gender getGender(Resource resource){
    	//get a textual representation of the resource
    	String label = realiser.realiseSentence(nlg.getNPPhrase(resource.getURI(), false, false));
    	//we take the first token because we assume this is the first name
        String firstToken = label.split(" ")[0];
        //lookup the gender
        Gender g = gender.getGender(resource.getURI(), firstToken);
        return g;
    }

    public static void main(String args[]) {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        endpoint.getDefaultGraphURIs().add("http://dbpedia.org");
        //endpoint = SparqlEndpoint.getEndpointDBpedia();

        Verbalizer v;
        if (SimpleNLGwithPostprocessing.isWindows()) {
            v = new Verbalizer(endpoint, "cache/sparql", "resources/wordnetWindows/");
        } else {
            v = new Verbalizer(endpoint, "cache/sparql", "resources/wordnet/dict");
        }
        v.setPersonTypes(Sets.newHashSet("http://dbpedia.org/ontology/Person"));
//        Individual ind = new Individual("http://dbpedia.org/resource/Barbara_Aland");
//        Individual ind = new Individual("http://dbpedia.org/resource/John_Passmore");
//        Individual ind = new Individual("http://dbpedia.org/resource/Ford_Zetec_engine");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/AutomobileEngine");
        Individual ind = new Individual("http://dbpedia.org/resource/John_Major");
        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/OfficeHolder");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/MusicalArtist");
//        ind = new Individual("http://dbpedia.org/resource/King_Kong_(2005_film)");
//        nc = new NamedClass("http://dbpedia.org/ontology/Film");
//        Individual ind = new Individual("http://dbpedia.org/resource/David_Foster");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/MusicalArtist");
        int maxShownValuesPerProperty = 3;
        v.setMaxShownValuesPerProperty(maxShownValuesPerProperty);
        List<NLGElement> text = v.verbalize(ind, nc, 0.4, Cooccurrence.PROPERTIES, HardeningType.SMALLEST);
        String summary = v.realize(text);
        summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
        summary = summary.replace(" , among others,", ", among others,");
        System.out.println(summary);
    }

    /**
     * @param maxShownValuesPerProperty the maxShownValuesPerProperty to set
     */
    public void setMaxShownValuesPerProperty(int maxShownValuesPerProperty) {
        this.maxShownValuesPerProperty = maxShownValuesPerProperty;
    }

    /**
     * Replaces the subject of a coordinated phrase or simple phrase with a
     * subject from a list of precomputed subjects
     *
     * @param phrase
     * @param subjects
     * @return Phrase with replaced subject
     */
    protected NLGElement replaceSubject(NLGElement phrase, List<NPPhraseSpec> subjects, Gender g) {
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
