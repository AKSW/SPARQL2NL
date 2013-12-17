/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import java.util.*;
import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author ngonga
 */
public class TrueFalseQuestionGenerator extends MultipleChoiceQuestionGenerator {

    private static final Logger logger = Logger.getLogger(MultipleChoiceQuestionGenerator.class.getName());

    public TrueFalseQuestionGenerator(SparqlEndpoint ep, Set<Resource> restrictions) {
        super(ep, restrictions);
    }

    @Override
    public Question generateQuestion(Resource r) {
        logger.info("Generating question for resource " + r + "...");
        //get properties
        logger.info("Getting statement for resource");
        String query = "select ?p ?o where {<" + r.getURI() + "> ?p ?o. FILTER(isURI(?o))}";
        boolean result = false;
        ResultSet rs = executeSelectQuery(query, endpoint);
        QuerySolution qs;
        Resource property = null, object = null;
        while (rs.hasNext() && !result) {
            qs = rs.next();
            property = qs.getResource("p");
            object = qs.getResource("o");
            if (!blackList.contains(property)) {
                if (Math.random() >= 0.5) {
                    result = true;
                }
            }
        }
        logger.info("...got result " + result);
        //early termination if resource has no meaningful properties
        if (!result) {
            return null;
        }

        //pick random property
        logger.info("Chosen (property, object) = (" + property + "," + object + ")");
        query = "ASK {<" + r.getURI() + "> <" + property.getURI() + "> <" + object.getURI() + ">}";
        Query questionQuery = QueryFactory.create(query);
        List<Answer> trueAsAnswer = new ArrayList<Answer>();
        trueAsAnswer.add(new SimpleAnswer("True"));
        List<Answer> falseAsAnswer = new ArrayList<Answer>();
        trueAsAnswer.add(new SimpleAnswer("False"));
        // generate wrong object is answer should be false
        if (Math.random() < 0.5) {
            //true answer
            Triple t = new Triple(r.asNode(), property.asNode(), object.asNode());
            return new SimpleQuestion(nlg.realiser.realiseSentence(nlg.getNLForTriple(t)), trueAsAnswer, falseAsAnswer, DIFFICULTY, questionQuery);
        } else {
            //get values for property, i.e. the correct answers
            logger.info("Generating wrong answers...");
            query = "select distinct ?o where {?x <" + property.getURI() + "> ?o. FILTER(isURI(?o))}";
            Query sparqlQuery = QueryFactory.create(query);
            rs = executeSelectQuery(query, endpoint);
            Resource wrongAnswer = null;
            while (rs.hasNext()) {
                qs = rs.next();
                wrongAnswer = qs.get("o").asResource();
                if (!wrongAnswer.getURI().equals(object.getURI())) {
                    break;
                }
            }
            if(wrongAnswer == null) return null;
            Triple t = new Triple(r.asNode(), property.asNode(), wrongAnswer.asNode());
            return new SimpleQuestion(nlg.realiser.realiseSentence(nlg.getNLForTriple(t)), falseAsAnswer, trueAsAnswer, DIFFICULTY, questionQuery);
        }
    }

    public static void main(String args[]) {
        Resource r = ResourceFactory.createResource("http://dbpedia.org/ontology/Country");
        Set<Resource> res = new HashSet<Resource>();
        res.add(r);
        TrueFalseQuestionGenerator sqg = new TrueFalseQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), res);
        System.out.println(sqg.blackList);
        Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 10);
        for (Question q : questions) {
            if (q != null) {
                System.out.println(">>" + q.getText());
                List<Answer> correctAnswers = q.getCorrectAnswers();
                System.out.println(correctAnswers);
                List<Answer> wrongAnswers = q.getWrongAnswers();
                System.out.println(wrongAnswers);
            }
        }
    }
}
