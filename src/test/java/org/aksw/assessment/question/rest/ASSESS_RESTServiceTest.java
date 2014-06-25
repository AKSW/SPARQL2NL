/**
 * 
 */
package org.aksw.assessment.question.rest;

import static org.junit.Assert.fail;

import java.util.List;

import org.aksw.assessment.question.QuestionType;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * @author Lorenz Buehmann
 *
 */
public class ASSESS_RESTServiceTest {
	
	
	private RESTService restService = new RESTService();
	
	public ASSESS_RESTServiceTest() {
		RESTService.init(null);
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#getQuestionsJSON(javax.servlet.ServletContext, java.lang.String, java.util.List, int)}.
	 */
	@Test
	public void testGetQuestionsJSON() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#getQuestionsJSON2(javax.servlet.ServletContext, org.codehaus.jettison.json.JSONArray, java.util.List, int)}.
	 * @throws JSONException 
	 */
	@Test
	public void testGetQuestionsJSON2() throws JSONException {
		JSONArray domain = new JSONArray();
		JSONObject entry = new JSONObject();
		entry.put("className", "http://dbpedia.org/ontology/SoccerClub");
		JSONArray properties = new JSONArray();
		properties.put("http://dbpedia.org/ontology/manager");
		entry.put("properties", properties);
		domain.put(entry);
		List<String> questionTypes = Lists.newArrayList(QuestionType.MC.getName(), QuestionType.JEOPARDY.getName());
		RESTQuestions restQuestions = restService.getQuestionsJSON2(null, domain, questionTypes, 3);
		System.out.println(restQuestions);
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#getApplicableProperties(javax.servlet.ServletContext, java.lang.String)}.
	 */
	@Test
	public void testGetApplicableProperties() {
		restService.getApplicableProperties(null, "http://dbpedia.org/ontology/SoccerClub");
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#getClasses(javax.servlet.ServletContext)}.
	 */
	@Test
	public void testGetClasses() {
		restService.getClasses(null);
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#getEntities(javax.servlet.ServletContext)}.
	 */
	@Test
	public void testGetEntities() {
		restService.getEntities(null);
	}

	/**
	 * Test method for {@link org.aksw.assessment.question.rest.RESTService#precomputeGraphs()}.
	 */
	@Test
	public void testPrecomputeGraphs() {
		restService.precomputeGraphs(null);
	}

}
