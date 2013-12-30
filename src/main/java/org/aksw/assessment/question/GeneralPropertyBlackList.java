/**
 * 
 */
package org.aksw.assessment.question;

import java.util.Set;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * This class contains basically a set of defined properties that are meaningless for the generation of questions
 * in the ASSESS project. 
 * @author Lorenz Buehmann
 *
 */
public class GeneralPropertyBlackList {

	public static Set<String> blacklist = Sets.newHashSet(
		"http://www.w3.org/ns/prov#was", 
	    "http://www.w3.org/2002/07/owl#sameAs", 
	    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", 
	    "http://www.w3.org/ns/prov#wasDerivedFrom", 
	    "http://xmlns.com/foaf/0.1/isPrimaryTopicOf", 
	    "http://xmlns.com/foaf/0.1/depiction", 
	    "http://xmlns.com/foaf/0.1/homepage", 
	    "http://purl.org/dc/terms/subject"
	    );
	
	public static boolean contains(Resource resource){
		return blacklist.contains(resource.getURI());
	}
	
	public static boolean contains(String uri){
		return blacklist.contains(uri);
	}
}
