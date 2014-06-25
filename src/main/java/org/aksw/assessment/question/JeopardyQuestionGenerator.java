/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.assessment.question.rest.RESTService;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.sparqltools.util.SPARQLQueryUtils;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 *
 * @author ngonga
 */
public class JeopardyQuestionGenerator extends MultipleChoiceQuestionGenerator {

    private static final Logger logger = Logger.getLogger(MultipleChoiceQuestionGenerator.class.getName());
   
    private Map<NamedClass, List<Resource>> wrongAnswersByType = new HashMap<>();
    
	private boolean preferPopularWrongAnswers = false;

	private boolean optimizedAnswerGeneration = true;

	private boolean useCompleteResourcesOnly = true;
	
    
    public JeopardyQuestionGenerator(SparqlEndpoint ep, String cacheDirectory, String namespace, Map<NamedClass, Set<ObjectProperty>> restrictions, Set<String> personTypes, BlackList blackList) {
        super(ep, cacheDirectory, namespace, restrictions, personTypes, blackList);
    }
    
    public JeopardyQuestionGenerator(SparqlEndpoint ep, QueryExecutionFactory qef, String cacheDirectory, String namespace, Map<NamedClass, Set<ObjectProperty>> restrictions, Set<String> personTypes, BlackList blackList) {
        super(ep, qef, cacheDirectory, namespace, restrictions, personTypes, blackList);
    }
    
    /* (non-Javadoc)
     * @see org.aksw.assessment.question.MultipleChoiceQuestionGenerator#getMostProminentResources(java.util.Set)
     */
    @Override
    protected Map<Resource, NamedClass> getMostProminentResources(Set<NamedClass> types) {
    	//INFO for this type of questions it might makes sense to use only resources having all properties of the summary graph
        //as this makes the summary more fancy
    	if(useCompleteResourcesOnly ){
    		Map<Resource, NamedClass> result = Maps.newLinkedHashMap();
    		//we need the summarizing properties graph first
    		for (NamedClass type : types) {
				Set<org.aksw.sparql2nl.entitysummarizer.clustering.Node> summaryProperties = verbalizer.getSummaryProperties(type, propertyFrequencyThreshold, namespace, cooccurrenceType);
				StringBuilder query = new StringBuilder();
	        	query.append("SELECT DISTINCT ?x WHERE{");
	        	query.append("?x a <" + type.getURI() + ">.");
	        	//add triple pattern for each property in summary graphall triple patterns
	        	int i = 0;
				for (org.aksw.sparql2nl.entitysummarizer.clustering.Node propertyNode : summaryProperties) {
					query.append((propertyNode.outgoing ? ("?x <" + propertyNode.label + "> ?o" + i++) :  ("?o" + i++ + " <" + propertyNode.label + "> ?x")) + ".");
				}
	        	SPARQLQueryUtils.addRankingConstraints(endpointType, query, "x");
	        	query.append("}");
	        	SPARQLQueryUtils.addRankingOrder(endpointType, query, "x");
	            query.append(" LIMIT 500");
	            ResultSet rs = executeSelectQuery(query.toString());
	            QuerySolution qs;
	            while (rs.hasNext()) {
	                qs = rs.next();
	                result.put(qs.getResource("x"), type);
	            }
			}
    		return result;
    	} else {
    		return super.getMostProminentResources(types);
    	}
    }

    @Override
    public Question generateQuestion(Resource r, NamedClass type) {
        
        //generate the question in forms of a summary describing the resource
        String summary = getEntitySummary(r.getURI());
        
        if(summary == null){
        	return null;
        }
        
        //get properties of the resource
        String query = "DESCRIBE <" + r.getURI() + ">";
        
        //the correct answer is just the resource itself
        List<Answer> correctAnswers = new ArrayList<>();
        Answer correctAnswer = new SimpleAnswer(getTextualRepresentation(r));
        correctAnswers.add(correctAnswer);
        
        //generate the wrong answers
		List<Answer> wrongAnswers = generateWrongAnswers(r, type);

		String className = nlg.realiser.realiseSentence(nlg.getNPPhrase(type.getName(), false));
		className = className.toLowerCase().replaceAll(Pattern.quote("."), "");

		return new SimpleQuestion("Which " + className + " matches the following description:\n" + summary,
				correctAnswers, wrongAnswers, DIFFICULTY, QueryFactory.create(query), QuestionType.JEOPARDY);
	}
    
    private List<Answer> generateWrongAnswers(Resource r, NamedClass type){
    	List<Answer> wrongAnswers = new ArrayList<>();
		logger.info("Generating wrong answers...");
		
		//get the triples used in the summary of the resource
		List<Triple> summaryTriples = new ArrayList<>(getSummaryTriples(r.getURI()));
		logger.info("Summary triples:" + summaryTriples);
		
		//build a SPARQL query to get wrong answers that are as similar as possible
		//different strategies are possible here:
		//1. bottom-up: add as many triples(p-o) as possible as long not only the resource itself is returned
		//2. top-down:
		List<Resource> wrongAnswerCandidates = new LinkedList<>();
		Collection<List<Triple>> emptyPermutations = new HashSet<>();
		for(int size = 1; size <= Math.min(3, summaryTriples.size()); size++){
			//compute permutations of size n
			Collection<List<Triple>> permutations = PermutationsOfN.getPermutationsOfSizeN(summaryTriples, size);
			
			//we can handle permutations with the same predicate in a single SPARQL query
			//this kind of query should be handled more efficiently than several queries
			//TODO what can be done for permutations of size > 1?
			if(optimizedAnswerGeneration  && size == 1){
				HashMultimap<Node, Triple> partitions = HashMultimap.create();
				for (List<Triple> permutation : permutations) {
					partitions.put(permutation.get(0).getPredicate(), permutation.get(0));
				}
				
				for (Entry<Node, Collection<Triple>> entry : partitions.asMap().entrySet()) {
					Collection<Triple> triples = entry.getValue();
					
					String query = asFilterInSPARQLQuery(triples, type);
					
					Set<Resource> wrongAnswerCandidatesTmp = new HashSet<Resource>();
					ResultSet rs = executeSelectQuery(query);
					QuerySolution qs;
					while (rs.hasNext()) {
						qs = rs.next();
						if(!qs.getResource("s").equals(r)){
							wrongAnswerCandidatesTmp.add(qs.getResource("s"));
						}
					}
					//we remove the correct resource
					wrongAnswerCandidatesTmp.remove(r);
					
					wrongAnswerCandidates.addAll(wrongAnswerCandidatesTmp);
					
					if(wrongAnswerCandidatesTmp.isEmpty()){
						// we can add a permutation for each triple
						for (Triple triple : triples) {
							emptyPermutations.add(Lists.newArrayList(triple));
						}
					}
				}
			} else {
				//for each permutation
				for (List<Triple> permutation : permutations) {
					
					//if a permutation is a superset of an empty permutation we can of course skip it 
					//because a query is just the intersection of triple patterns, thus, more triples patterns
					//make the resultset smaller
					boolean skip = false;
					for (List<Triple> list : emptyPermutations) {
						if(permutation.containsAll(list)){
							skip = true;
						}
					}
					if(!skip){
						String query = asSPARQLQuery(permutation, type);
						
						//check if it returns some other resources
						boolean empty = true;
						ResultSet rs = executeSelectQuery(query);
						QuerySolution qs;
						while (rs.hasNext()) {
							qs = rs.next();
							if(!qs.getResource("s").equals(r)){
								empty = false;
								wrongAnswerCandidates.add(qs.getResource("s"));
							}
						}
						if(empty){
							emptyPermutations.add(permutation);
						}
					}
				}
			}
		}
		//reverse the candidate list such that we get the most similar first
		Collections.reverse(wrongAnswerCandidates);
		Iterator<Resource> iter = wrongAnswerCandidates.iterator();
		Set<Resource> tmp = new HashSet<>();
		while(iter.hasNext() && tmp.size()<maxNrOfAnswersPerQuestion-1){
			tmp.add(iter.next());
		}
		for (Resource resource : tmp) {
			wrongAnswers.add(new SimpleAnswer(getTextualRepresentation(resource)));
		}
		
		logger.info("...done.");
		return wrongAnswers;
    }
    
    private String asSPARQLQuery(List<Triple> triples, NamedClass type){
    	String query = "SELECT DISTINCT ?s WHERE {";
		for (Triple triple : triples) {
			//add triple pattern
			query += asTriplePattern("s", triple, type);
			if(preferPopularWrongAnswers ){
				query += "?o ?p ?s.";
			}
		}
		query += "}";
		if(preferPopularWrongAnswers){
			query += " GROUP BY ?s ORDER BY DESC(COUNT(?o))";
		}
		query += " LIMIT " + (maxNrOfAnswersPerQuestion-1);
		return query;
    }
    
    /**
     * Given a list of triples all having the same predicate a SPARQL query pattern using FILTER IN is returned.
     * @param property
     * @param resources
     * @return
     */
    private String asFilterInSPARQLQuery(Collection<Triple> triples, NamedClass type){
    	Iterator<Triple> iterator = triples.iterator();
    	Triple firstTriple = iterator.next();
    	String query = "SELECT DISTINCT ?s WHERE{";
    	if(triples.size() == 1){
    		query += asTriplePattern("s", firstTriple, type);
    	} else {
    		ObjectProperty property = new ObjectProperty(firstTriple.getPredicate().getURI());
    		boolean outgoingProperty = verbalizer.graphGenerator.isOutgoingProperty(type, property);
    		String subject = outgoingProperty ? "?s" : "?o";
        	String predicate = asTriplePatternComponent(firstTriple.getPredicate());
        	String object = outgoingProperty ? "?o" : "?s";
        	query += subject + " " + predicate + " " + object + ".";
    		String filter = "FILTER (?o IN (";
    		filter += asTriplePatternComponent(firstTriple.getObject());
    		while(iterator.hasNext()){
    			filter += "," + asTriplePatternComponent(iterator.next().getObject());
    		}
    		filter += "))";
    		query += filter;
    	}
    	query += "}";
    	query += " LIMIT " + triples.size() * maxNrOfAnswersPerQuestion;
    	return query;
    }
    
    /**
     * Convert a JENA API triple into a triple pattern string.
     * @param subjectVar
     * @param t
     * @return
     */
    private String asTriplePattern(String subjectVar, Triple t, NamedClass cls){
    	boolean outgoingProperty = verbalizer.graphGenerator.isOutgoingProperty(cls, new ObjectProperty(t.getPredicate().getURI()));
    	//we have to reverse the triple pattern if the property is not an outgoing property
    	String subject = outgoingProperty ? "?" + subjectVar : asTriplePatternComponent(t.getObject());
    	String predicate = asTriplePatternComponent(t.getPredicate());
    	String object = outgoingProperty ? asTriplePatternComponent(t.getObject()) : "?" + subjectVar;
    	return subject + " " + predicate + " " + object + ".";
    }
    
    private String asTriplePatternComponent(Node node){
    	String s;
    	if(node.isURI()){
    		s = "<" + node + ">";
    	} else {
    		s = "\"" + node.getLiteralLexicalForm().replace("\"", "\\\"") + "\"";
    		if(node.getLiteralDatatypeURI() != null){
    			s += "^^<" + node.getLiteralDatatypeURI() + ">";
    		} else if(node.getLiteralLanguage() != null){
    			s += "@" + node.getLiteralLanguage();
    		}
    	}
    	return s;
    }
    
    /**
     * Generates wrong answers by just returning instances of the same class as the correct answer.
     * @param r
     * @return
     */
    private List<Answer> generateWrongAnswersSimple(Resource r, NamedClass type){
    	List<Answer> wrongAnswers = new ArrayList<>();
		logger.info("Generating wrong answers...");
		
		List<Resource> wrongAnswerCandidates;
		if(wrongAnswersByType.containsKey(type)){
			wrongAnswerCandidates = wrongAnswersByType.get(type);
		} else {
			wrongAnswerCandidates = new ArrayList<Resource>(getMostProminentResources(restrictions.keySet()).keySet());
			wrongAnswerCandidates.remove(r);
			Collections.shuffle(wrongAnswerCandidates, new Random(123));
			wrongAnswersByType.put(type, wrongAnswerCandidates);
		}
		Iterator<Resource> iter = wrongAnswerCandidates.iterator();
		Resource candidate;
		while(wrongAnswers.size() < maxNrOfAnswersPerQuestion-1 && iter.hasNext()){
			candidate = iter.next();
			wrongAnswers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(candidate.getURI(), false,
					false))));
			iter.remove();
		}
		logger.info("...done.");
		return wrongAnswers;
    }

	public static void main(String args[]) {
//		Map<NamedClass, Set<ObjectProperty>> restrictions = Maps.newHashMap();
//		restrictions.put(new NamedClass("http://dbpedia.org/ontology/Writer"), new HashSet<ObjectProperty>());
//        JeopardyQuestionGenerator sqg = new JeopardyQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache", "http://dbpedia.org/ontology/", restrictions);
//        Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 10);
//        for (Question q : questions) {
//            if (q != null) {
//                System.out.println(">>" + q.getText());
//                List<Answer> correctAnswers = q.getCorrectAnswers();
//                System.out.println(correctAnswers);
//                List<Answer> wrongAnswers = q.getWrongAnswers();
//                System.out.println(wrongAnswers);
//            }
//        }
		RESTService rest = new RESTService();
		List<String> classes = rest.getClasses(null);
		classes = Lists.newArrayList("http://dbpedia.org/ontology/Play");
		for(String cls : classes){
			try {
				Map<NamedClass, Set<ObjectProperty>> restrictions = Maps.newHashMap();
				restrictions.put(new NamedClass(cls), new HashSet<ObjectProperty>());
				JeopardyQuestionGenerator sqg = new JeopardyQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache2", 
						"http://dbpedia.org/ontology/", 
						restrictions,Sets.newHashSet("http://dbpedia.org/ontology/Person"), new DBpediaPropertyBlackList());
				Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 10);
				if(questions.size() == 0){
					System.err.println("EMTPY: " + cls);
					System.exit(0);
				}
				for (Question q : questions) {
					System.out.println(q.getText());
					for (Answer a : q.getCorrectAnswers()) {
						System.out.println(a.getText());
					}
					for (Answer a : q.getWrongAnswers()) {
						System.out.println(a.getText());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}
	    
    }
}
