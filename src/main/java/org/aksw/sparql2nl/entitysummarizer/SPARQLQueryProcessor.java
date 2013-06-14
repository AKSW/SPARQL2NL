package org.aksw.sparql2nl.entitysummarizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.aksw.sparql2nl.queryprocessing.TriplePatternExtractor;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.Property;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.ExtractionDBCache;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.vocabulary.RDF;

public class SPARQLQueryProcessor {

    private SparqlEndpoint endpoint;
    private TriplePatternExtractor patternExtractor = new TriplePatternExtractor();
    private SPARQLReasoner reasoner;

    public SPARQLQueryProcessor(SparqlEndpoint endpoint) {
        this.endpoint = endpoint;
        reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), new ExtractionDBCache("cache"));
    }

    public Map<NamedClass, Set<Property>> processQuery(String query) {
        return processQuery(QueryFactory.create(query, Syntax.syntaxARQ));
    }

    public Collection<Map<NamedClass, Set<Property>>> processQueries(Collection<Query> queries) {
        Collection<Map<NamedClass, Set<Property>>> result = new ArrayList<Map<NamedClass, Set<Property>>>();
        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Map<NamedClass, Set<Property>>>> futures = new ArrayList<Future<Map<NamedClass, Set<Property>>>>();
        for (final Query query : queries) {
            futures.add(threadPool.submit(new Callable<Map<NamedClass, Set<Property>>>() {

                @Override
                public Map<NamedClass, Set<Property>> call() throws Exception {
                    return processQuery(query);
                }
            }));
        }
        for (Future<Map<NamedClass, Set<Property>>> future : futures) {
            try {
                result.add(future.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        threadPool.shutdown();

        return result;
    }

    public Collection<Map<NamedClass, Set<Property>>> processEntries(Collection<LogEntry> entries) {
        List<Query> queries = new ArrayList<Query>();
        Set<String> blacklist = Sets.newHashSet("-", "bliss", "ARC", "[CURL]");
        for (LogEntry entry : entries) {
            if (!blacklist.contains(entry.userAgent)) {
                queries.add(entry.sparqlQuery);
            }
        }
        System.out.println(queries.size());
        return processQueries(queries);
    }

    /**
     * We want to get classes with frequent occurring predicates in the
     * knowledge base.
     *
     * @param query
     */
    public Map<NamedClass, Set<Property>> processQuery(Query query) {
        TriplePatternExtractor patternExtractor = new TriplePatternExtractor();
        Map<NamedClass, Set<Property>> result = new HashMap<NamedClass, Set<Property>>();
        //get all projection variables in the query
        List<Var> vars = query.getProjectVars();

        //we have to filter out variables which are the subject of a rdf:type statement
        for (Iterator<Var> iterator = vars.iterator(); iterator.hasNext();) {
            Var var = iterator.next();
            //get all outgoing triple patterns
            Set<Triple> outgoingTriplePatterns = patternExtractor.extractOutgoingTriplePatterns(query, var);
            for (Triple triple : outgoingTriplePatterns) {
                //check for rdf:type triples
                if (triple.subjectMatches(var) && triple.predicateMatches(RDF.type.asNode())) {
                    iterator.remove();
                    break;
                }
            }
        }

        //now we want to get all types
        for (Var var : vars) {
            //get all ingoing triple patterns
            Set<Triple> ingoingTriplePatterns = patternExtractor.extractIngoingTriplePatterns(query, var);

            //get the types for the subject of the triple pattern
            for (Triple triple : ingoingTriplePatterns) {
                //get the subject
                Node subject = triple.getSubject();
                //get the predicate
                Node predicate = triple.getPredicate();
                //*BUG HERE*. Sometimes the predicates are variables
                if (!predicate.isVariable()) {
                    Property property = new ObjectProperty(predicate.getURI());

                    if (subject.isVariable()) {//if the subject s is a variable we can look for outgoing rdf:type triple patterns of s in the query
                        Set<Triple> outgoingTriplePatterns = patternExtractor.extractOutgoingTriplePatterns(query, subject);
                        for (Triple tp : outgoingTriplePatterns) {
                            //check for rdf:type triples
                            if (tp.predicateMatches(RDF.type.asNode()) && tp.getObject().isURI()) {
                                NamedClass nc = new NamedClass(tp.getObject().getURI());
                                if (!result.containsKey(nc)) {
                                    result.put(nc, new HashSet<Property>());
                                }
                                result.get(nc).add(property);
                            }
                        }
                    } else if (subject.isURI()) {//if the subject is a URI we can ask the knowledge base for the types
                        Set<NamedClass> types = reasoner.getTypes(new Individual(subject.getURI()));
                        for (NamedClass nc : types) {
                            if (!result.containsKey(nc)) {
                                result.put(nc, new HashSet<Property>());
                            }
                            result.get(nc).add(property);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);

        Query query = QueryFactory.create(
                "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. ?s dbo:birthDate ?date.}");
        Map<NamedClass, Set<Property>> occurrences = processor.processQuery(query);
        System.out.println(Joiner.on("\n").withKeyValueSeparator("=").join(occurrences));

        query = QueryFactory.create(
                "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. ?s dbo:birthDate ?date.}");
        occurrences = processor.processQuery(query);
        System.out.println(Joiner.on("\n").withKeyValueSeparator("=").join(occurrences));

        query = QueryFactory.create(
                "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?place ?date WHERE {dbr:Brad_Pitt dbo:birthPlace ?place. dbr:Brad_Pitt dbo:birthDate ?date.}");
        occurrences = processor.processQuery(query);
        System.out.println(Joiner.on("\n").withKeyValueSeparator("=").join(occurrences));

        query = QueryFactory.create(
                "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Book. ?o dbo:birthPlace ?place. ?o dbo:birthDate ?date.}");
        occurrences = processor.processQuery(query);
        System.out.println(Joiner.on("\n").withKeyValueSeparator("=").join(occurrences));
    }
}
