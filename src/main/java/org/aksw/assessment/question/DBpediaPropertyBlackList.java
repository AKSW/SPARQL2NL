/**
 * 
 */
package org.aksw.assessment.question;

import java.util.Set;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * This class contains set of DBpedia properties that are meaningless for the generation of questions
 * in the ASSESS project. 
 * @author Lorenz Buehmann
 *
 */
public class DBpediaPropertyBlackList {

	public static Set<String> blacklist = Sets.newHashSet(
	    "http://dbpedia.org/ontology/wikiPageRedirects", 
	    "http://dbpedia.org/ontology/wikiPageExternalLink",
	    "http://dbpedia.org/ontology/wikiPageDisambiguates",
	    "http://dbpedia.org/ontology/thumbnail",
	    "http://dbpedia.org/property/hasPhotoCollection", 
	    "http://dbpedia.org/property/link",
	    "http://dbpedia.org/property/url",
	    "http://dbpedia.org/property/website",
	    "http://dbpedia.org/property/wordnet_type",
	    "http://dbpedia.org/property/report"
	    );
	
	public static boolean contains(Resource resource){
		return blacklist.contains(resource.getURI());
	}
	
	public static boolean contains(String uri){
		return blacklist.contains(uri);
	}
}
