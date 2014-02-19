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
import java.util.regex.Pattern;

import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.assessment.question.rest.RESTService;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
    
    public JeopardyQuestionGenerator(SparqlEndpoint ep, String cacheDirectory, String namespace, Map<NamedClass, Set<ObjectProperty>> restrictions, Set<String> personTypes, BlackList blackList) {
        super(ep, cacheDirectory, namespace, restrictions, personTypes, blackList);
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
        List<Answer> correctAnswer = new ArrayList<>();
        correctAnswer.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.getURI(), false, false))));
        
        //generate the wrong answers
		List<Answer> wrongAnswers = generateWrongAnswers(r);

		String className = nlg.realiser.realiseSentence(nlg.getNPPhrase(type.getName(), false));
		className = className.toLowerCase().replaceAll(Pattern.quote("."), "");

		return new SimpleQuestion("Which " + className + " matches the following description:\n" + summary,
				correctAnswer, wrongAnswers, DIFFICULTY, QueryFactory.create(query), QuestionType.JEOPARDY);
	}
    
    private List<Answer> generateWrongAnswers(Resource r){
    	List<Answer> wrongAnswers = new ArrayList<>();
		logger.info("Generating wrong answers...");
		
		//get the triples used in the summary of the resource
		List<Triple> summaryTriples = new ArrayList<>(getSummaryTriples(r.getURI()));
		
		//build a SPARQL query to get wrong answers that are as similar as possible
		//different strategies are possible here:
		//1. bottom-up: add as many triples(p-o) as possible as long not only the resource itself is returned
		//2. top-down:
		List<Resource> wrongAnswerCandidates = new LinkedList<>();
		Collection<List<Triple>> emptyPermutations = new HashSet<>();
		for(int size = 1; size <= Math.min(3, summaryTriples.size()); size++){
			Collection<List<Triple>> permutations = PermutationsOfN.getPermutationsOfSizeN(summaryTriples, size);
			
			for (List<Triple> permutation : permutations) {
				boolean skip = false;
				for (List<Triple> list : emptyPermutations) {
					if(permutation.containsAll(list)){
						skip = true;
					}
				}
				if(!skip){
					String query = "SELECT ?s WHERE {";
					for (Triple triple : permutation) {
						//add triple pattern
						query += asTriplePattern("s", triple);
						if(preferPopularWrongAnswers ){
							query += "?o ?p ?s.";
						}
					}
					query += "}";
					if(preferPopularWrongAnswers){
						query += " GROUP BY ?s ORDER BY DESC(COUNT(?o))";
					}
					query += " LIMIT " + (maxNrOfAnswersPerQuestion-1);
					
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
		//reverse the candidate list such that we get the most similar first
		Collections.reverse(wrongAnswerCandidates);
		Iterator<Resource> iter = wrongAnswerCandidates.iterator();
		Set<Resource> tmp = new HashSet<>();
		while(iter.hasNext() && tmp.size()<maxNrOfAnswersPerQuestion-1){
			tmp.add(iter.next());
		}
		for (Resource resource : tmp) {
			wrongAnswers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(resource.getURI(), false,
					false))));
		}
		
		logger.info("...done.");
		return wrongAnswers;
    }
    
    /**
     * Convert a JENA API triple into a triple pattern string.
     * @param subjectVar
     * @param t
     * @return
     */
    private String asTriplePattern(String subjectVar, Triple t){
    	String s = "?" + subjectVar;
    	s += " <" + t.getPredicate() + "> ";
    	if(t.getObject().isURI()){
    		s += "<" + t.getObject() + ">";
    	} else {
    		s += "\"" + t.getObject().getLiteralLexicalForm().replace("\"", "\\\"") + "\"";
    		if(t.getObject().getLiteralDatatypeURI() != null){
    			s += "^^<" + t.getObject().getLiteralDatatypeURI() + ">";
    		} else if(t.getObject().getLiteralLanguage() != null){
    			s += "@" + t.getObject().getLiteralLanguage();
    		}
    	}
    	s += ".";
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
