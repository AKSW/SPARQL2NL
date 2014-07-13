/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl;

import static org.junit.Assert.*;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;

import java.net.URL;

import org.aksw.sparql2nl.naturallanguagegeneration.Postprocessor;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLG;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleSPARQL2NLConverter;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;

import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.lexicon.NIHDBLexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.realiser.english.Realiser;


public class SPARQL2NLTest {
	
	@Test
	public void testSPARQL2NL() throws Exception {
		Lexicon lexicon = new NIHDBLexicon("/home/me/tools/lexAccess2013lite/data/HSqlDb/lexAccess2013.data");
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		SimpleSPARQL2NLConverter sparql2nlConverter = new SimpleSPARQL2NLConverter(endpoint, "cache/sparql2nl", lexicon);
		
		SimpleNLGwithPostprocessing snlg = new SimpleNLGwithPostprocessing(endpoint);
		
		for (Query query : QALDBenchmark.getQueries(9,10,12)) {
			System.out.println(query);
			
			
			String nlr = sparql2nlConverter.getNLR(query);
			System.out.println(nlr);
			
			
//			nlr = snlg.getNLR(query);
//			System.out.println(nlr);
		}
	}
	
    public static void main(String[] args) {
       
        String query2 = "PREFIX res: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT DISTINCT ?height "
                + "WHERE { res:Claudia_Schiffer dbo:height ?height . "
                + "FILTER(\"1.0e6\"^^<http://www.w3.org/2001/XMLSchema#double> <= ?height)}";
        String query2b = "PREFIX res: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT DISTINCT ?height "
                + "WHERE { res:Claudia_Schiffer dbo:height ?height . }";
        String query2c = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT ?x "
                + "WHERE { ?x rdf:type dbo:Place . }";

        String query = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?x "
                + "WHERE { "
                + "{res:Abraham_Lincoln dbo:deathPlace ?uri} "
                + "UNION {res:Abraham_Lincoln dbo:birthPlace ?uri} . "
                + "?uri rdf:type dbo:Place. "
                + "FILTER regex(?x, \"France\").  "
                + "FILTER (lang(?x) = 'en')"
                + "OPTIONAL { ?uri dbo:Name ?x }. "
                + "}";
        String query3 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX yago: <http://dbpedia.org/class/yago/> "
                + "SELECT COUNT(DISTINCT ?uri) "
                //+ "SELECT ?uri "
                + "WHERE { ?uri rdf:type yago:EuropeanCountries . "
                + "?uri dbo:governmentType ?govern . "
                + "FILTER regex(?govern,'monarchy') . "
                //+ "FILTER (!BOUND(?date))"
                + "}";
        String query3b = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX yago: <http://dbpedia.org/class/yago/> "
                + "SELECT COUNT(DISTINCT ?uri) "
                //+ "SELECT ?uri "
                + "WHERE { ?uri rdf:type yago:EuropeanCountries . "
                + "?uri dbo:governmentType ?govern . "
                + "FILTER regex(?govern,'monarchy','i') . "
                + "OPTIONAL { ?govern rdf:type dbo:Film . } " 
                //+ "FILTER (!BOUND(?date))"
                + "}";
        String query4 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE { ?cave rdf:type dbo:Cave . "
                + "?cave dbo:location ?uri . "
                + "?uri rdf:type dbo:Country . "
                + "OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') } }"
                + " GROUP BY ?uri ?string "
                + "HAVING (COUNT(?cave) > 2)";
        String query5 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "SELECT DISTINCT ?uri "
                + "WHERE { ?cave rdf:type dbo:Cave . "
                + "?cave dbo:location ?uri . "
                + "?uri rdf:type dbo:Country . "
                + "?uri dbo:writer ?y . "
                + "FILTER(!BOUND(?y)) . "
                + "FILTER(!BOUND(?uri)) }";

        String query6 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "ASK WHERE { { res:Batman_Begins dbo:starring res:Christian_Bale . } "
                + "UNION { res:Batman_Begins dbp:starring res:Christian_Bale . } }";

        String query7 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/>"
                + "PREFIX res: <http://dbpedia.org/resource/>"
                + "PREFIX yago: <http://dbpedia.org/class/yago/>"
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE { "
                + "	?uri rdf:type yago:RussianCosmonauts."
                + "     ?uri rdf:type yago:FemaleAstronauts ."
                + "OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') }"
                + "}";

        String query8 = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX  dbo:  <http://dbpedia.org/ontology/> "
                + "PREFIX  dbp:  <http://dbpedia.org/property/> "
                + "PREFIX  dbp:  <http://dbpedia.org/ontology/> "
                + "PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "SELECT DISTINCT  ?uri ?string "
                + "WHERE { ?uri rdf:type dbo:Country . "
                + "{?uri dbp:birthPlace ?x} UNION {?union dbo:birthPlace ?x} "
                + "?x rdf:type dbo:Mountain ."
                + "OPTIONAL { ?uri rdfs:label ?string "
                + "FILTER ( lang(?string) = \'en\' )} } "
                + "GROUP BY ?uri ?string "
                + "ORDER BY DESC(?language) "
                + "LIMIT 10 OFFSET 20";

        String query9 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1/> "
                + "PREFIX mo: <http://purl.org/ontology/mo/> "
                + "SELECT DISTINCT ?artisttype "
                + "WHERE {"
                + "?artist foaf:name 'Liz Story'."
                + "?artist rdf:type ?artisttype ."
                + "FILTER (?artisttype != mo:MusicArtist)"
                + "}";

        String query10 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX yago: <http://dbpedia.org/class/yago/>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/>"
                + "PREFIX dbp: <http://dbpedia.org/property/>"
                + "PREFIX res: <http://dbpedia.org/resource/>"
                + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE {"
                + "?uri rdf:type dbo:Person ."
                + "{ ?uri rdf:type yago:PresidentsOfTheUnitedStates. } "
                + "UNION "
                + "{ ?uri rdf:type dbo:President."
                + "?uri dbp:title res:President_of_the_United_States. }"
                + "?uri rdfs:label ?string."
                + "FILTER (?string >\"1970-01-01\"^^xsd:date && lang(?string) = 'en' && !regex(?string,'Presidency','i') && !regex(?string,'and the')) ."
                + "}";
        
        String query11 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?string WHERE { "
                + "?uri rdf:type dbo:Film ."
                + "?uri dbo:starring ?x ."
                + "?x rdf:type dbo:Person ."
                + "?x rdfs:label 'Christian Bale'."
                + "?uri rdfs:label ?string .}";
        String query12 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?string WHERE { "
                + "?uri rdf:type dbo:Film ."
                + "?uri dbo:starring ?x ."
                + "?x rdf:type dbo:Person ."
                + "?x rdfs:label 'Christian Bale'."
                + "res:Batman_Begins dbo:starring ?x ."
                + "?uri rdfs:label ?string .}";
        String query13 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?string WHERE { "
                + "?uri rdf:type dbo:Film ."
                + "?uri dbo:starring ?x ."
                + "?x rdf:type dbo:Person ."
                + "?x rdfs:label 'Christian Bale'."
                + "{res:Batman_Begins dbo:starring ?x.} UNION {res:Batman_Begins dbp:starring ?x.}"
                + "?uri rdfs:label ?string .}";
        String query14 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?string WHERE { "
                + "?uri rdf:type dbo:Film ."
                + "?uri dbo:starring ?x ."
                + "?x rdf:type dbo:Person ."
                + "?x rdfs:label 'Christian Bale'."
                + "res:Batman_Begins dbo:place ?x."
                + "OPTIONAL { ?x  dbp:has dbo:Mountain . }"
                + "?uri rdfs:label ?string .}";
        String query15 = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX  res:  <http://dbpedia.org/resource/>"
                + "PREFIX  foaf: <http://xmlns.com/foaf/0.1/>"
                + "PREFIX  dbo:  <http://dbpedia.org/ontology/>"
                + "PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT  ?actor WHERE"
                + "{ res:Charmed dbo:starring ?actor ."
                + " OPTIONAL { ?actor dbo:birthDate ?date . } }";
        String query16 = "PREFIX  res:  <http://dbpedia.org/resource/>"
                + "PREFIX  dbo:  <http://dbpedia.org/ontology/>"
                + "ASK WHERE { OPTIONAL { res:Frank_Herbert dbo:deathDate ?date } FILTER ( ! bound(?date) ) }";
        String query17 = "PREFIX dbo: <http://dbpedia.org/ontology/> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "ASK WHERE { ?uri rdf:type dbo:VideoGame . ?uri rdfs:label 'Battle Chess'@en . }";
        String query18 = "PREFIX dbo: <http://dbpedia.org/ontology/> PREFIX res: <http://dbpedia.org/resource/> PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "ASK WHERE { res:Barack_Obama dbo:spouse ?spouse . ?spouse rdfs:label ?name . FILTER(regex(?name,'Michelle'))}";
        String query19 = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT  ?uri ?string WHERE { ?uri rdf:type yago:RussianCosmonauts . "
                + "?uri rdf:type yago:FemaleAstronauts . "
                + "OPTIONAL { ?uri rdfs:label ?string . FILTER ( lang(?string) = \"en\" ) } }";
        String query20 = "PREFIX  res:  <http://dbpedia.org/resource/> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  dbo:  <http://dbpedia.org/ontology/> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT  ?uri ?string WHERE { "
                + "{ ?uri rdf:type dbo:City } UNION { ?uri rdf:type dbo:Town }"
                + "?uri dbo:country res:Germany . ?uri dbo:populationTotal ?population . FILTER ( ?population > 250000 )"
                + "OPTIONAL { ?uri rdfs:label ?string . FILTER ( lang(?string) = \"en\" ) } }";
        String query21 = "PREFIX  res:  <http://dbpedia.org/resource/> PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  dbo:  <http://dbpedia.org/ontology/> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT ?person ?date WHERE { ?person rdf:type dbo:Scientist. { ?person dbo:occupation dbo:Surfing .} UNION { ?person dbo:occupation res:Writer. } ?person dbo:birthDate ?date. FILTER(?date > \"1950\"^^xsd:date) . "
                + "OPTIONAL { ?person rdfs:label ?string. FILTER (lang(?string) = 'en') } } ";
        String query25 = "PREFIX  res:  <http://dbpedia.org/resource/> PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  dbp:  <http://dbpedia.org/property/> PREFIX  dbo:  <http://dbpedia.org/ontology/> PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "SELECT DISTINCT ?person WHERE { ?person rdf:type dbo:Person . { ?person rdf:type dbo:Scientist . } UNION { ?person dbo:occupation dbo:Music . }"
                + "?person dbo:birthDate ?date . ?book dbo:author ?person .  FILTER (?date > \"1950\"^^xsd:date ) } GROUP BY ?person HAVING ( COUNT (?book ) > 2) ";
        String query26 = "SELECT DISTINCT ?actorName WHERE { "
                + "?dir1 <http://data.linkedmdb.org/resource/movie/director_name> \"John Waters\" . "
                + "?dir2 <http://data.linkedmdb.org/resource/movie/director_name> \"Steven Spielberg\" . "
                + "?dir1movie <http://data.linkedmdb.org/resource/movie/director> ?dir1 . "
                + "?dir1movie <http://data.linkedmdb.org/resource/movie/actor> ?actor ."
                + "?dir2movie <http://data.linkedmdb.org/resource/movie/actor> ?actor ."
                + "?dir2movie <http://data.linkedmdb.org/resource/movie/director> ?dir2 ."
                + "?actor <http://data.linkedmdb.org/resource/movie/actor_name> ?actorName . } " ;
        String query27 = "SELECT DISTINCT ?actorName WHERE {"
                + "?woody <http://data.linkedmdb.org/resource/movie/director_name> \"Woody Allen\". "
                + "?movie <http://data.linkedmdb.org/resource/movie/director> ?woody; <http://data.linkedmdb.org/resource/movie/actor> ?actor."
                + "?actor <http://data.linkedmdb.org/resource/movie/actor_name> ?actorName.}";
        
//        String[] queries = {query,query2,query2b,query2c,query3,query3b,query4,query5,query6,query7,query8,query9,query10,query11,query14};
      String[] queries = {query27};
        try {
            SparqlEndpoint ep = new SparqlEndpoint(new URL("http://greententacle.techfak.uni-bielefeld.de:5171/sparql"));
            Lexicon lexicon = Lexicon.getDefaultLexicon();
            SimpleNLGwithPostprocessing snlg = new SimpleNLGwithPostprocessing(ep);
            
            for (String q : queries) {
                System.out.println("\n----------------------------------------------------------------");
                Query sparqlQuery = QueryFactory.create(q, Syntax.syntaxARQ);
                snlg.getNLR(sparqlQuery);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
