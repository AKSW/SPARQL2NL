/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl;

import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;
import org.aksw.sparql2nl.smooth_nlg.CardBox;
import org.aksw.sparql2nl.smooth_nlg.NLConstructor;
import org.aksw.sparql2nl.smooth_nlg.SPARQLDeconstructor;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author christina
 */
public class SmoothNLGTest {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        SPARQLDeconstructor decon = new SPARQLDeconstructor(SparqlEndpoint.getEndpointDBpedia());
        
        String query1 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?x "
                + "WHERE { "
                + "{res:Abraham_Lincoln dbo:deathPlace ?uri} "
                + "UNION {res:Abraham_Lincoln dbo:birthPlace ?uri} . "
                + "?uri rdf:type dbo:Place. "
                + "FILTER regex(?uri, \"France\").  "
                + "FILTER (lang(?uri) = 'en')"
                + "OPTIONAL { ?uri dbo:Name ?x }. "
                + "}";
        String query2 = "PREFIX res: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT DISTINCT ?height "
                + "WHERE { res:Claudia_Schiffer dbo:height ?height . FILTER(\"1.0e6\"^^<http://www.w3.org/2001/XMLSchema#double> <= ?height)}";
        String query3 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT COUNT(DISTINCT ?uri) "
                + "WHERE { "
                + "?uri rdf:type dbo:Mountain . "
                + "?uri dbo:locatedInArea res:Nepal . "
                + "?uri dbo:elevation ?elevation . "
                + "res:Mansiri_Himal dbo:border ?uri . "
                + "FILTER (?elevation > 8000) . "
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
        String query5 = "PREFIX dbo: <http://dbpedia.org/ontology/>"
                + "PREFIX res: <http://dbpedia.org/resource/>"
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "SELECT DISTINCT ?uri ?string WHERE { "
                + "res:Annapurna dbo:elevation ?elevation . "
                + "?uri rdf:type dbo:Mountain . "
                + "?uri dbo:elevation ?otherelevation . "
                + "FILTER (?otherelevation < ?elevation) . "
                + "OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') } "
                + " } ORDER BY DESC(?otherelevation) LIMIT 1";
        try {
            CardBox c; NLConstructor con;System.out.println(query1);
            c = decon.deconstruct(QueryFactory.create(query1,Syntax.syntaxARQ));
            con = new NLConstructor(c);
            con.construct();
            System.out.println("\n----------------------------\n");
            c = decon.deconstruct(QueryFactory.create(query3,Syntax.syntaxARQ));
            con = new NLConstructor(c);
            con.construct();
            System.out.println("\n----------------------------\n");
            c = decon.deconstruct(QueryFactory.create(query5,Syntax.syntaxARQ));
            con = new NLConstructor(c);
            con.construct();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void show(String[][] m) {
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < 3; j++) 
                System.out.print(m[i][j] + " ");
            System.out.println();
        }
    }
}
