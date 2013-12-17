/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.sparql2nl.entitysummarizer.Verbalizer;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import simplenlg.framework.NLGElement;

/**
 *
 * @author ngonga
 */
public class JeopardyQuestionGenerator extends MultipleChoiceQuestionGenerator {

    private static final Logger logger = Logger.getLogger(MultipleChoiceQuestionGenerator.class.getName());
    Verbalizer v;
    NamedClass nc;
    public final int maxShownValuesPerProperty = 3; 
    public JeopardyQuestionGenerator(SparqlEndpoint ep, Set<Resource> restrictions) {
        super(ep, restrictions);
        if (SimpleNLGwithPostprocessing.isWindows()) {
            v = new JeopardyVerbalizer(endpoint, "cache/sparql", "resources/wordnetWindows/");
        } else {
            v = new JeopardyVerbalizer(endpoint, "cache/sparql", "resources/wordnet/dict");
        }
        v.setPersonTypes(Sets.newHashSet("http://dbpedia.org/ontology/Person"));
        nc = new NamedClass(restrictions.iterator().next().getURI());
        v.setMaxShownValuesPerProperty(maxShownValuesPerProperty);
    }

    @Override
    public Question generateQuestion(Resource r) {
        logger.info("Generating summary for resource " + r + "...");
        Individual ind = new Individual(r.getURI());
//        Individual ind = new Individual("http://dbpedia.org/resource/David_Foster");
//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/MusicalArtist");
        List<NLGElement> text = v.verbalize(ind, nc, 0.4, DatasetBasedGraphGenerator.Cooccurrence.PROPERTIES, HardeningFactory.HardeningType.SMALLEST);
        String summary = v.realize(text);
        summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
        summary = summary.replace(" , among others,", ", among others,");
        
        //get properties
        String query = "describe {<" + r.getURI() + ">}";
        
        List<Answer> correctAnswer = new ArrayList<>();
        correctAnswer.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.getURI(), false, false))));
        List<Answer> wrongAnswers = new ArrayList<>();
        // generate wrong object is answer should be false
        
            //get values for property, i.e. the correct answers
            logger.info("Generating wrong answers...");
            query = "select distinct ?x where {?x ?p ?o. <"+r.getURI()+"> ?q ?o.} LIMIT 10";
            Query sparqlQuery = QueryFactory.create(query);
            ResultSet rs = executeSelectQuery(query, endpoint);
            Resource wrongAnswer = null;
            QuerySolution qs;
            while (rs.hasNext()) {
                qs = rs.next();
                wrongAnswer = qs.get("x").asResource();
                if (!wrongAnswer.getURI().equals(r.getURI())) {
                    wrongAnswers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(wrongAnswer.getURI(), false, false))));
                }
            }
            if (wrongAnswer == null) {
                return null;
            }
            return new SimpleQuestion("Which entity matches the following description:\n" + summary, wrongAnswers, correctAnswer, DIFFICULTY, QueryFactory.create(query));        
    }
    
      public static void main(String args[]) {
        Resource r = ResourceFactory.createResource("http://dbpedia.org/ontology/Person");
        Set<Resource> res = new HashSet<Resource>();
        res.add(r);
        JeopardyQuestionGenerator sqg = new JeopardyQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), res);
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
