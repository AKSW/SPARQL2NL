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
	    "http://dbpedia.org/ontology/wikiPageRevisionID",
	    "http://dbpedia.org/ontology/wikiPageID",
	    "http://dbpedia.org/ontology/wikiPageOutLinkCount",
	    "http://dbpedia.org/ontology/wikiPageInLinkCount",
	    "http://dbpedia.org/ontology/thumbnail",
	    "http://dbpedia.org/ontology/abstract",
	    "http://dbpedia.org/ontology/termPeriod",
	    "http://dbpedia.org/ontology/individualisedPnd",
	    "http://dbpedia.org/property/hasPhotoCollection", 
	    "http://dbpedia.org/property/link",
	    "http://dbpedia.org/property/url",
	    "http://dbpedia.org/property/website",
	    "http://dbpedia.org/property/wordnet_type",
	    "http://dbpedia.org/property/report",
	    "http://dbpedia.org/property/id",
	    "http://dbpedia.org/property/pnd",
	    "http://dbpedia.org/property/signature",
	    "http://dbpedia.org/property/refs"
	    );
	
	final static boolean onlyOntologyNamespace = true;
	
	public static boolean contains(Resource resource){
		return contains(resource.getURI());
	}
	
	public static boolean contains(String uri){
		if(onlyOntologyNamespace && !uri.startsWith("http://dbpedia.org/ontology/")){
			return true;
		}
		return blacklist.contains(uri);
	}
}
